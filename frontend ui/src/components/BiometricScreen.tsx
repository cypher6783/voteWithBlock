"use client";

import React, { useState, useEffect, useRef, useCallback } from "react";
import { Camera, AlertCircle, ShieldAlert, CheckCircle, RefreshCw, Loader2, ArrowRight, Eye } from "lucide-react";

import axios from "axios";
import { AUTH_BIOMETRIC_CHALLENGE_URL, AUTH_BIOMETRIC_CONFIRM_URL } from "@/lib/api";

interface BiometricScreenProps {
  onProceed: (sessionToken: string) => void;
  voterId: string;
  preAuthToken: string;
}

// Eye Aspect Ratio threshold — below this value = blink
const BLINK_EAR_THRESHOLD = 0.25;
// How many consecutive low-EAR frames = confirmed blink
const BLINK_CONSEC_FRAMES = 2;

// Landmark indices for left and right eye (face-api 68-point model)
// Left eye: 36–41, Right eye: 42–47
const LEFT_EYE_IDXS = [36, 37, 38, 39, 40, 41];
const RIGHT_EYE_IDXS = [42, 43, 44, 45, 46, 47];

function euclidean(p1: { x: number; y: number }, p2: { x: number; y: number }) {
  return Math.sqrt((p1.x - p2.x) ** 2 + (p1.y - p2.y) ** 2);
}

function computeEAR(pts: { x: number; y: number }[]) {
  // EAR = (||p2-p6|| + ||p3-p5||) / (2 * ||p1-p4||)
  const A = euclidean(pts[1], pts[5]);
  const B = euclidean(pts[2], pts[4]);
  const C = euclidean(pts[0], pts[3]);
  return (A + B) / (2.0 * C);
}

type ScanState = "idle" | "loading-models" | "connecting" | "no-face" | "scanning" | "blink-prompt" | "blink-detected" | "generating" | "success";

interface FacePoint {
  x: number;
  y: number;
}

interface FaceLandmarks {
  positions: FacePoint[];
}

interface FaceDetectionResult {
  landmarks: FaceLandmarks;
}

interface FaceDetectionChain {
  withFaceLandmarks(useTinyNet?: boolean): Promise<FaceDetectionResult | null>;
}

interface FaceApi {
  nets: {
    tinyFaceDetector: { loadFromUri(uri: string): Promise<void> };
    faceLandmark68TinyNet: { loadFromUri(uri: string): Promise<void> };
  };
  TinyFaceDetectorOptions: new (options: { scoreThreshold: number }) => unknown;
  detectSingleFace(video: HTMLVideoElement, options: unknown): FaceDetectionChain;
}

type BrowserWindow = Window &
  typeof globalThis & {
    faceapi?: FaceApi;
    webkitAudioContext?: typeof AudioContext;
  };

const getFaceApi = () => (window as BrowserWindow).faceapi;

const getCameraStream = async (): Promise<MediaStream> => {
  if (!navigator.mediaDevices?.getUserMedia) {
    throw new Error("This browser does not expose camera access. Use Chrome or Edge on localhost/HTTPS.");
  }

  const cameraRequest = navigator.mediaDevices.getUserMedia({
    video: { width: 640, height: 480, facingMode: "user" },
  });

  const timeout = new Promise<never>((_, reject) => {
    window.setTimeout(() => {
      reject(new Error("Camera permission was not granted in time. Check the browser camera prompt or site permissions, then try again."));
    }, 15000);
  });

  return Promise.race([cameraRequest, timeout]);
};

export default function BiometricScreen({ onProceed, voterId, preAuthToken }: BiometricScreenProps) {
  const [secondsLeft, setSecondsLeft] = useState(299);
  const [scanState, setScanState] = useState<ScanState>("idle");
  const [cameraConnected, setCameraConnected] = useState(false);
  const [livenessStatus, setLivenessStatus] = useState<"pending" | "waiting" | "success">("pending");
  const [vectorStatus, setVectorStatus] = useState<"pending" | "processing" | "success">("pending");
  const [progress, setProgress] = useState(0);
  const [capturedFaceData, setCapturedFaceData] = useState<string>("");
  const [errorMsg, setErrorMsg] = useState<string>("");
  const [earValue, setEarValue] = useState<number | null>(null);
  const [faceAligned, setFaceAligned] = useState(false);
  const [isVerifying, setIsVerifying] = useState(false);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const overlayCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const detectionLoopRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const blinkFrameCounter = useRef(0);
  const blinkDetectedRef = useRef(false);
  const scanStateRef = useRef<ScanState>("idle");
  const audioContextRef = useRef<AudioContext | null>(null);

  // Keep scanStateRef in sync so the detection loop can read current state
  useEffect(() => {
    scanStateRef.current = scanState;
  }, [scanState]);

  const stopCamera = useCallback(() => {
    if (videoRef.current?.srcObject) {
      const stream = videoRef.current.srcObject as MediaStream;
      stream.getTracks().forEach((t) => t.stop());
      videoRef.current.srcObject = null;
    }
  }, []);

  const stopDetectionLoop = useCallback(() => {
    if (detectionLoopRef.current) {
      clearInterval(detectionLoopRef.current);
      detectionLoopRef.current = null;
    }
  }, []);

  const playBeep = useCallback((freq = 440, duration = 0.15) => {
    try {
      if (!audioContextRef.current) {
        const AudioContextCtor = window.AudioContext || (window as BrowserWindow).webkitAudioContext;
        if (!AudioContextCtor) return;
        audioContextRef.current = new AudioContextCtor();
      }
      const ctx = audioContextRef.current;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = "sine";
      osc.frequency.setValueAtTime(freq, ctx.currentTime);
      gain.gain.setValueAtTime(0.08, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + duration);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + duration);
    } catch {}
  }, []);

  // Session countdown
  useEffect(() => {
    const timer = setInterval(() => setSecondsLeft((p) => (p > 0 ? p - 1 : 0)), 1000);
    return () => clearInterval(timer);
  }, []);

  const formatTime = (secs: number) => {
    const m = Math.floor(secs / 60).toString().padStart(2, "0");
    const s = (secs % 60).toString().padStart(2, "0");
    return `${m}:${s}`;
  };

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stopDetectionLoop();
      stopCamera();
    };
  }, [stopCamera, stopDetectionLoop]);

  // Inject face-api.js locally once
  const loadFaceApiModels = async (): Promise<boolean> => {
    if (getFaceApi()) {
      // Already loaded — just load models
      return await loadModels();
    }
    return new Promise((resolve) => {
      const script = document.createElement("script");
      script.src = "/models/face-api.min.js";
      script.onload = async () => {
        const ok = await loadModels();
        resolve(ok);
      };
      script.onerror = (e) => {
        console.error("Failed to load local face-api.min.js", e);
        resolve(false);
      };
      document.head.appendChild(script);
    });
  };

  const loadModels = async (): Promise<boolean> => {
    try {
      const faceapi = getFaceApi();
      if (!faceapi) return false;
      const MODEL_URL = "/models";
      await faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_URL);
      await faceapi.nets.faceLandmark68TinyNet.loadFromUri(MODEL_URL);
      return true;
    } catch (e) {
      console.error("Failed to load local face-api models", e);
      return false;
    }
  };

  // Capture a clean JPEG frame to canvas for the base64 faceData
  const captureFrame = useCallback((): string => {
    const video = videoRef.current;
    if (!video) return "";
    const canvas = canvasRef.current || document.createElement("canvas");
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    const ctx = canvas.getContext("2d");
    if (!ctx) return "";
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL("image/jpeg", 0.85);
  }, []);

  // Draw face landmark points + eye contours on the overlay canvas
  const drawLandmarks = useCallback((landmarks: FaceLandmarks, videoEl: HTMLVideoElement, currentEar: number) => {
    const overlayCanvas = overlayCanvasRef.current;
    if (!overlayCanvas) return;
    overlayCanvas.width = videoEl.videoWidth || 640;
    overlayCanvas.height = videoEl.videoHeight || 480;
    const ctx = overlayCanvas.getContext("2d");
    if (!ctx) return;
    ctx.clearRect(0, 0, overlayCanvas.width, overlayCanvas.height);

    const positions = landmarks.positions;
    if (!positions) return;

    // Draw all 68 dots
    ctx.fillStyle = "rgba(59,130,246,0.7)";
    positions.forEach((pt) => {
      ctx.beginPath();
      ctx.arc(pt.x, pt.y, 2, 0, Math.PI * 2);
      ctx.fill();
    });

    // Highlight left eye (green when EAR is good, red when blinking)
    const drawEye = (idxs: number[], color: string) => {
      ctx.beginPath();
      ctx.strokeStyle = color;
      ctx.lineWidth = 1.5;
      idxs.forEach((idx, i) => {
        const pt = positions[idx];
        if (i === 0) ctx.moveTo(pt.x, pt.y);
        else ctx.lineTo(pt.x, pt.y);
      });
      ctx.closePath();
      ctx.stroke();
    };

    const eyeColor = currentEar < BLINK_EAR_THRESHOLD ? "#f87171" : "#34d399";
    drawEye(LEFT_EYE_IDXS, eyeColor);
    drawEye(RIGHT_EYE_IDXS, eyeColor);
  }, []);

  // Core detection loop — runs every 100ms
  const runDetectionLoop = useCallback(() => {
    const faceapi = getFaceApi();
    if (!faceapi || !videoRef.current) return;

    detectionLoopRef.current = setInterval(async () => {
      const video = videoRef.current;
      const state = scanStateRef.current;
      if (!video || !faceapi || state === "blink-detected" || state === "generating" || state === "success") return;

      try {
        const result = await faceapi
          .detectSingleFace(video, new faceapi.TinyFaceDetectorOptions({ scoreThreshold: 0.4 }))
          .withFaceLandmarks(true); // true = tiny net

        if (!result) {
          setFaceAligned(false);
          if (state === "blink-prompt") {
            setScanState("scanning");
          }
          return;
        }

        const landmarks = result.landmarks;
        const positions = landmarks.positions;

        // Compute EAR
        const leftPts = LEFT_EYE_IDXS.map((i) => positions[i]);
        const rightPts = RIGHT_EYE_IDXS.map((i) => positions[i]);
        const ear = (computeEAR(leftPts) + computeEAR(rightPts)) / 2;
        setEarValue(ear);

        drawLandmarks(landmarks, video, ear);
        setFaceAligned(true);

        // Transition to blink-prompt once face is aligned
        if (state === "scanning") {
          setScanState("blink-prompt");
          setLivenessStatus("waiting");
        }

        // Blink detection
        if (!blinkDetectedRef.current && (state === "blink-prompt" || state === "scanning")) {
          if (ear < BLINK_EAR_THRESHOLD) {
            blinkFrameCounter.current++;
            if (blinkFrameCounter.current >= BLINK_CONSEC_FRAMES) {
              blinkDetectedRef.current = true;
              playBeep(880, 0.2);

              // Capture the frame right after blink
              const frameData = captureFrame();
              setCapturedFaceData(frameData);

              setScanState("blink-detected");
              setLivenessStatus("success");

              // Animate the vector generation progress bar
              setTimeout(() => {
                setScanState("generating");
                setVectorStatus("processing");
                let p = 0;
                const prog = setInterval(() => {
                  p += 8;
                  setProgress(p);
                  if (p >= 100) {
                    clearInterval(prog);
                    setVectorStatus("success");
                    setScanState("success");
                    playBeep(1046, 0.35);
                    stopDetectionLoop();
                    // Clear overlay
                    if (overlayCanvasRef.current) {
                      const oc = overlayCanvasRef.current.getContext("2d");
                      oc?.clearRect(0, 0, overlayCanvasRef.current.width, overlayCanvasRef.current.height);
                    }
                  }
                }, 180);
              }, 600);
            }
          } else {
            blinkFrameCounter.current = 0;
          }
        }
      } catch {}
    }, 100);
  }, [captureFrame, drawLandmarks, playBeep, stopDetectionLoop]);

  const startCamera = async () => {
    if (scanState !== "idle") return;
    setScanState("loading-models");
    setErrorMsg("");
    playBeep(330, 0.1);

    const modelsOk = await loadFaceApiModels();
    if (!modelsOk) {
      setErrorMsg("Failed to load face detection models. Check your internet connection and try again.");
      setScanState("idle");
      return;
    }
    setScanState("connecting");
    try {
      const stream = await getCameraStream();

      // videoRef is always mounted (video element is always in DOM), so this is safe
      const video = videoRef.current;
      if (!video) {
        throw new Error("Video element not found in DOM.");
      }

      video.srcObject = stream;
      await video.play().catch(() => undefined);

      // Wait for metadata to be ready. Use addEventListener + 3s timeout fallback
      // because onloadedmetadata property assignment is unreliable if the event
      // fires synchronously before the assignment.
      await new Promise<void>((resolve) => {
        const onReady = () => {
          video.removeEventListener("loadedmetadata", onReady);
          video.removeEventListener("loadeddata", onReady);
          resolve();
        };
        video.addEventListener("loadedmetadata", onReady);
        video.addEventListener("loadeddata", onReady);
        // Fallback: proceed after 3s regardless
        setTimeout(resolve, 3000);
      });

      setCameraConnected(true);
      setScanState("scanning");
      playBeep(440, 0.1);
      runDetectionLoop();

    } catch (err) {
      console.error("Webcam access error:", err);
      setErrorMsg(err instanceof Error ? err.message : "Failed to access webcam. Please allow camera permission and try again.");
      setScanState("idle");
    }
  };

  const resetScanner = () => {
    stopDetectionLoop();
    stopCamera();
    blinkFrameCounter.current = 0;
    blinkDetectedRef.current = false;
    setScanState("idle");
    setCameraConnected(false);
    setLivenessStatus("pending");
    setVectorStatus("pending");
    setProgress(0);
    setCapturedFaceData("");
    setFaceAligned(false);
    setEarValue(null);
    if (overlayCanvasRef.current) {
      const oc = overlayCanvasRef.current.getContext("2d");
      oc?.clearRect(0, 0, overlayCanvasRef.current.width, overlayCanvasRef.current.height);
    }
  };

  const hudText = () => {
    switch (scanState) {
      case "loading-models": return { text: "Loading AI face detection models…", color: "text-blue-400", pulse: true };
      case "connecting":     return { text: "Connecting to webcam device…", color: "text-slate-300", pulse: true };
      case "no-face":        return { text: "No face detected — position yourself in the frame", color: "text-amber-400", pulse: false };
      case "scanning":       return { text: faceAligned ? "Face aligned — please blink naturally" : "Align your face inside the dashed oval", color: faceAligned ? "text-blue-400" : "text-slate-300", pulse: faceAligned };
      case "blink-prompt":   return { text: "✦ Liveness check active — please BLINK naturally", color: "text-blue-300", pulse: true };
      case "blink-detected": return { text: "✓ Blink detected! Liveness confirmed", color: "text-emerald-400", pulse: false };
      case "generating":     return { text: `Generating biometric vector… (${progress}%)`, color: "text-blue-400", pulse: true };
      case "success":        return { text: "✓ Biometric Match Verified Securely", color: "text-emerald-400", pulse: false };
      default:               return { text: "Click below to start", color: "text-slate-400", pulse: false };
    }
  };

  const hud = hudText();
  // showVideo controls visibility of the overlay guides/HUD — NOT the video element itself.
  // The <video> element must ALWAYS be in the DOM so videoRef.current is never null
  // when startCamera() tries to attach the MediaStream to it.
  const showVideoOverlays = !["idle", "loading-models", "connecting"].includes(scanState);
  const isActive = !["idle", "success"].includes(scanState);

  const handleServerVerify = async () => {
    setIsVerifying(true);
    setErrorMsg("");
    try {
      const challengeRes = await axios.post(
        AUTH_BIOMETRIC_CHALLENGE_URL,
        {},
        { headers: { Authorization: `Bearer ${preAuthToken}` } }
      );
      const challengeId = challengeRes.data.challengeId;

      const confirmRes = await axios.post(
        AUTH_BIOMETRIC_CONFIRM_URL,
        {
          preAuthToken,
          base64FaceImage: capturedFaceData,
          challengeId,
        }
      );

      onProceed(confirmRes.data.sessionToken);
    } catch (err: any) {
      console.error(err);
      if (err.response?.data?.message) {
        setErrorMsg(err.response.data.message);
      } else {
        setErrorMsg("An unexpected error occurred during server biometric verification.");
      }
      resetScanner();
    } finally {
      setIsVerifying(false);
    }
  };

  return (
    <div className="w-full flex flex-col min-h-full">
      {/* Session Header */}
      <div className="w-full bg-slate-900 text-white px-6 py-3 flex items-center justify-between border-b border-slate-800">
        <div className="flex items-center gap-2">
          <span className="h-2 w-2 rounded-full bg-blue-500 animate-ping" />
          <span className="text-xs uppercase tracking-wider font-bold text-blue-400">Authenticated Voter Session</span>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs text-slate-400 font-semibold">
            Security ID: <code className="text-slate-200">{voterId.substring(0, 12)}</code>
          </span>
          <div className="h-4 w-px bg-slate-700" />
          <span className="text-xs text-rose-400 font-bold bg-rose-950/40 px-3 py-1 rounded border border-rose-900/30 flex items-center gap-1.5 animate-pulse">
            Session expires in {formatTime(secondsLeft)}
          </span>
        </div>
      </div>

      <main className="flex-1 max-w-5xl w-full mx-auto px-6 py-8 flex flex-col items-center justify-center gap-8">
        <div className="w-full text-center">
          <h2 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">Biometric Liveness Verification</h2>
          <p className="mt-1.5 text-sm text-slate-500 max-w-xl mx-auto">
            Our AI automatically detects your face and a natural blink to confirm you are a live person — no buttons needed.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-12 gap-8 items-center w-full max-w-4xl">
          {/* Webcam Panel */}
          <div className="md:col-span-7 flex flex-col items-center">
            <div className="relative w-full aspect-[4/3] bg-slate-950 rounded-2xl overflow-hidden shadow-2xl border border-slate-800 flex flex-col items-center justify-center group">

              {/* Oval face guide */}
              {showVideoOverlays && (
                <div className={`absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-48 h-64 border-2 rounded-[100px] z-10 pointer-events-none transition-colors duration-300 ${
                  scanState === "success" ? "border-emerald-400 shadow-lg shadow-emerald-500/30" :
                  faceAligned ? "border-blue-400 shadow-lg shadow-blue-500/20 animate-pulse" :
                  "border-dashed border-slate-600"
                }`} />
              )}

              {/* Scanning laser line */}
              {scanState === "scanning" && !faceAligned && (
                <div className="absolute left-0 w-full h-0.5 bg-gradient-to-r from-transparent via-blue-500 to-transparent shadow-lg shadow-blue-500/50 z-20 pointer-events-none animate-scan" />
              )}

              {/* EAR debug chip */}
              {showVideoOverlays && earValue !== null && (
                <div className={`absolute top-3 left-3 z-30 px-2.5 py-1 rounded-md text-[10px] font-mono font-bold border backdrop-blur-sm ${
                  earValue < BLINK_EAR_THRESHOLD ? "bg-rose-950/80 border-rose-800 text-rose-300" : "bg-slate-900/80 border-slate-700 text-slate-300"
                }`}>
                  <Eye className="h-3 w-3 inline mr-1" />EAR {earValue.toFixed(3)}
                </div>
              )}

              {/* Idle state */}
              {scanState === "idle" && (
                <div className="flex flex-col items-center gap-4 text-center p-6 z-10">
                  {errorMsg && (
                    <div className="mb-2 p-2.5 bg-rose-950/40 border border-rose-900/30 text-rose-400 rounded text-xs font-semibold max-w-xs leading-normal">
                      <AlertCircle className="h-3.5 w-3.5 inline mr-1" />{errorMsg}
                    </div>
                  )}
                  <div className="p-5 bg-slate-900 rounded-full border border-slate-800 text-slate-400 group-hover:text-blue-400 group-hover:scale-105 transition-all">
                    <Camera className="h-10 w-10" />
                  </div>
                  <div>
                    <h4 className="font-bold text-slate-200">Camera Access Pending</h4>
                    <p className="text-xs text-slate-500 mt-1 max-w-xs">Click below to start the AI biometric scanner. You will be asked to allow camera access.</p>
                  </div>
                  <button
                    onClick={startCamera}
                    className="mt-2 bg-blue-600 hover:bg-blue-500 text-white font-bold py-2.5 px-5 rounded-lg text-xs transition-all shadow-lg shadow-blue-900/20 cursor-pointer"
                  >
                    Start Biometric Scanner
                  </button>
                </div>
              )}

              {/* Loading models */}
              {scanState === "loading-models" && (
                <div className="flex flex-col items-center gap-3 z-10">
                  <Loader2 className="h-8 w-8 text-blue-400 animate-spin" />
                  <span className="text-xs font-semibold text-slate-400">Loading AI face detection models…</span>
                  <span className="text-[10px] text-slate-500">Downloading from CDN, please wait</span>
                </div>
              )}

              {/* Connecting */}
              {scanState === "connecting" && (
                <div className="flex flex-col items-center gap-3 z-10">
                  <Loader2 className="h-8 w-8 text-blue-400 animate-spin" />
                  <span className="text-xs font-semibold text-slate-400">Connecting to webcam device…</span>
                </div>
              )}

              {/* Live video + overlay canvas — ALWAYS in DOM so videoRef is always valid */}
              <div className={`absolute inset-0 transition-opacity duration-300 ${cameraConnected ? "opacity-100" : "opacity-0 pointer-events-none"}`}>
                <video
                  ref={videoRef}
                  autoPlay
                  playsInline
                  muted
                  className="absolute inset-0 w-full h-full object-cover"
                />
                {/* Landmark overlay canvas */}
                <canvas
                  ref={overlayCanvasRef}
                  className="absolute inset-0 w-full h-full object-cover z-10 pointer-events-none"
                />
                {/* Hidden capture canvas */}
                <canvas ref={canvasRef} className="hidden" />
              </div>

              {/* HUD text bar */}
              {showVideoOverlays && (
                <div className="absolute bottom-4 inset-x-4 flex justify-center z-20 pointer-events-none">
                  <div className="px-4 py-2 bg-slate-950/85 backdrop-blur border border-slate-800 rounded-lg text-xs font-semibold shadow-lg">
                    <span className={`flex items-center gap-1.5 ${hud.color} ${hud.pulse ? "animate-pulse" : ""}`}>
                      {scanState === "scanning" || scanState === "blink-prompt" ? (
                        <span className="h-2 w-2 rounded-full bg-blue-400 animate-ping flex-shrink-0" />
                      ) : scanState === "blink-detected" || scanState === "success" ? (
                        <CheckCircle className="h-3.5 w-3.5 flex-shrink-0" />
                      ) : (
                        <Loader2 className="h-3 w-3 animate-spin flex-shrink-0" />
                      )}
                      {hud.text}
                    </span>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Status Panel */}
          <div className="md:col-span-5 flex flex-col gap-5">
            {/* Progress checklist */}
            <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-md">
              <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider mb-4 border-b border-slate-100 pb-2">Verification Progress</h3>
              <div className="space-y-4">
                {/* 1. Camera */}
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className={`h-5 w-5 rounded-full flex items-center justify-center text-xs font-bold border ${
                      cameraConnected ? "bg-emerald-50 border-emerald-300 text-emerald-600" : "bg-slate-50 border-slate-300 text-slate-400"
                    }`}>{cameraConnected ? "✓" : "1"}</span>
                    <span className="text-sm font-semibold text-slate-700">Camera Connected</span>
                  </div>
                  <span className="text-[10px] font-bold text-slate-400">{cameraConnected ? "Connected" : "Awaiting"}</span>
                </div>

                {/* 2. Face aligned */}
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className={`h-5 w-5 rounded-full flex items-center justify-center text-xs font-bold border ${
                      faceAligned ? "bg-emerald-50 border-emerald-300 text-emerald-600" :
                      cameraConnected ? "bg-blue-50 border-blue-300 text-blue-600" :
                      "bg-slate-50 border-slate-300 text-slate-400"
                    }`}>{faceAligned ? "✓" : cameraConnected ? "⋯" : "2"}</span>
                    <span className="text-sm font-semibold text-slate-700">Face Detected</span>
                  </div>
                  <span className={`text-[10px] font-bold ${faceAligned ? "text-emerald-500" : cameraConnected ? "text-blue-500 animate-pulse" : "text-slate-400"}`}>
                    {faceAligned ? "Aligned" : cameraConnected ? "Scanning…" : "Pending"}
                  </span>
                </div>

                {/* 3. Liveness */}
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className={`h-5 w-5 rounded-full flex items-center justify-center text-xs font-bold border ${
                      livenessStatus === "success" ? "bg-emerald-50 border-emerald-300 text-emerald-600" :
                      livenessStatus === "waiting" ? "bg-blue-50 border-blue-300 text-blue-600" :
                      "bg-slate-50 border-slate-300 text-slate-400"
                    }`}>{livenessStatus === "success" ? "✓" : livenessStatus === "waiting" ? "⋯" : "3"}</span>
                    <span className="text-sm font-semibold text-slate-700">Blink Detected</span>
                  </div>
                  <span className={`text-[10px] font-bold ${
                    livenessStatus === "success" ? "text-emerald-500" :
                    livenessStatus === "waiting" ? "text-blue-500 animate-pulse" : "text-slate-400"
                  }`}>
                    {livenessStatus === "success" ? "Verified" : livenessStatus === "waiting" ? "Watching…" : "Pending"}
                  </span>
                </div>

                {/* 4. Vector */}
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className={`h-5 w-5 rounded-full flex items-center justify-center text-xs font-bold border ${
                      vectorStatus === "success" ? "bg-emerald-50 border-emerald-300 text-emerald-600" :
                      vectorStatus === "processing" ? "bg-blue-50 border-blue-300 text-blue-600" :
                      "bg-slate-50 border-slate-300 text-slate-400"
                    }`}>{vectorStatus === "success" ? "✓" : vectorStatus === "processing" ? "⋯" : "4"}</span>
                    <span className="text-sm font-semibold text-slate-700">Vector Generated</span>
                  </div>
                  <span className={`text-[10px] font-bold ${
                    vectorStatus === "success" ? "text-emerald-500" :
                    vectorStatus === "processing" ? "text-blue-500 animate-pulse" : "text-slate-400"
                  }`}>
                    {vectorStatus === "success" ? "Created" : vectorStatus === "processing" ? "Processing" : "Pending"}
                  </span>
                </div>
              </div>

              {vectorStatus === "processing" && (
                <div className="mt-5">
                  <div className="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden">
                    <div className="bg-blue-500 h-full transition-all duration-200" style={{ width: `${progress}%` }} />
                  </div>
                </div>
              )}
            </div>

            {/* Privacy note */}
            <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 flex gap-3 text-blue-800 shadow-sm">
              <ShieldAlert className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
              <div>
                <h4 className="text-xs font-bold uppercase tracking-wider text-blue-900">Privacy Guard Active</h4>
                <p className="text-xs text-blue-700 mt-1 leading-relaxed">
                  Face detection runs entirely in your browser. No images or biometric data leave your device until you proceed. Embeddings are discarded immediately after verification.
                </p>
              </div>
            </div>

            {/* Instructions callout during active scan */}
            {(scanState === "scanning" || scanState === "blink-prompt") && (
              <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 flex gap-3 text-amber-800 shadow-sm">
                <Eye className="h-5 w-5 text-amber-600 flex-shrink-0 mt-0.5" />
                <div>
                  <h4 className="text-xs font-bold uppercase tracking-wider text-amber-900">Blink Naturally</h4>
                  <p className="text-xs text-amber-700 mt-1 leading-relaxed">
                    {faceAligned
                      ? "Face detected! Simply blink your eyes normally — the AI will register it automatically."
                      : "Look directly at the camera and center your face inside the oval guide."}
                  </p>
                </div>
              </div>
            )}

            {isActive && (
              <button
                onClick={resetScanner}
                className="w-full py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-300 rounded-lg text-xs font-semibold flex items-center justify-center gap-1.5 cursor-pointer"
              >
                <RefreshCw className="h-3.5 w-3.5" />
                Reset Scanner
              </button>
            )}
          </div>
        </div>

        <div className="w-full max-w-4xl border-t border-slate-200 pt-6 flex justify-end">
          <button
            onClick={handleServerVerify}
            disabled={scanState !== "success" || isVerifying}
            className={`px-8 py-3.5 rounded-xl font-bold flex items-center gap-2 shadow-lg transition-all duration-200 ${
              scanState === "success" && !isVerifying
                ? "bg-blue-700 text-white hover:bg-blue-600 shadow-blue-900/10 cursor-pointer"
                : "bg-slate-200 text-slate-400 shadow-none border border-slate-300/40 cursor-not-allowed"
            }`}
          >
            {isVerifying ? (
              <>
                <Loader2 className="h-4.5 w-4.5 animate-spin" />
                <span>Verifying...</span>
              </>
            ) : (
              <>
                <span>Proceed to Secure Ballot</span>
                <ArrowRight className="h-4.5 w-4.5" />
              </>
            )}
          </button>
        </div>
      </main>

      <footer className="w-full text-center py-5 text-xs text-slate-400 mt-auto border-t border-slate-200 bg-white/50">
        <p>Powered by face-api.js · Eye Aspect Ratio blink detection · All processing is fully local in-browser.</p>
      </footer>
    </div>
  );
}
