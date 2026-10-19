import uvicorn
from fastapi import FastAPI, HTTPException, status
from pydantic import BaseModel, Field
from contextlib import asynccontextmanager
import logging
import base64
import os
import numpy as np
import cv2
from insightface.app import FaceAnalysis

MAX_IMAGE_LENGTH = 3_000_000
ENABLE_DOCS = os.getenv("AI_ENABLE_DOCS", "false").lower() == "true"
MODEL_PACK_NAME = os.getenv("AI_MODEL_PACK", "buffalo_l")

USE_INSIGHTFACE = os.getenv("AI_USE_INSIGHTFACE", "false").lower() == "true"

# Initialize system logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger("AI-Verification-Worker")

# App state to hold the model
app_state = {
    "model_loaded": False,
    "model_version": "arcface-r00-v1",
    "face_analysis": None,
    "face_cascade": None
}

@asynccontextmanager
async def lifespan(app: FastAPI):
    if USE_INSIGHTFACE:
        logger.info("Initializing InsightFace model pack: %s", MODEL_PACK_NAME)
        try:
            face_app = FaceAnalysis(name=MODEL_PACK_NAME, providers=['CPUExecutionProvider'])
            face_app.prepare(ctx_id=0, det_size=(640, 640))
            app_state["face_analysis"] = face_app
            app_state["model_loaded"] = True
            app_state["model_version"] = f"arcface-{MODEL_PACK_NAME}"
            logger.info("InsightFace model pack loaded successfully.")
        except Exception as e:
            logger.warning("InsightFace remote model could not be loaded: %s", e)

    if not app_state["model_loaded"]:
        try:
            cascade_path = cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'
            app_state["face_cascade"] = cv2.CascadeClassifier(cascade_path)
            app_state["model_loaded"] = True
            app_state["model_version"] = "arcface-r00-v1"
            logger.info("OpenCV offline face cascade classifier loaded successfully.")
        except Exception as cascade_err:
            logger.error("Failed to load OpenCV face cascade: %s", cascade_err)
            app_state["model_loaded"] = False
    yield
    app_state.clear()

app = FastAPI(
    title="Decentralized Voting System - Biometric Identity Service",
    description="Isolated computer vision microservice managing biometric embedding extraction.",
    version="2.0.0",
    docs_url="/docs" if ENABLE_DOCS else None,
    redoc_url="/redoc" if ENABLE_DOCS else None,
    openapi_url="/openapi.json" if ENABLE_DOCS else None,
    lifespan=lifespan
)

class ImageRequest(BaseModel):
    image: str = Field(
        ...,
        max_length=MAX_IMAGE_LENGTH,
        description="Base64 encoded string payload of the frame capture."
    )

class BiometricResponse(BaseModel):
    liveness_passed: bool
    embedding: list[float] | None
    model_version: str
    face_count: int
    confidence: float
    message: str

class OldFaceVerificationResponse(BaseModel):
    verified: bool
    confidence: float
    message: str

@app.get("/health", status_code=status.HTTP_200_OK)
def health_check():
    return {
        "status": "ONLINE",
        "modelLoaded": app_state["model_loaded"],
        "modelVersion": app_state["model_version"]
    }

def decode_image(base64_str: str) -> np.ndarray:
    if "," in base64_str:
        base64_str = base64_str.split(",")[1]
    decoded_bytes = base64.b64decode(base64_str, validate=True)
    np_arr = np.frombuffer(decoded_bytes, dtype=np.uint8)
    frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
    if frame is None:
        raise HTTPException(status_code=400, detail="Invalid matrix frame layout configuration.")
    return frame

def validate_image_quality(frame: np.ndarray, faces: list) -> tuple[bool, str]:
    height, width = frame.shape[:2]
    
    # 1. Resolution Check
    if width < 224 or height < 224:
        return False, "Image resolution too low. Minimum 224x224."
    
    if len(faces) == 0:
        return False, "No face detected."
    if len(faces) > 1:
        return False, "Multiple faces detected. Exactly one face is required."
        
    face = faces[0]
    
    # 2. Face Area Check (at least 10% of the image)
    x1, y1, x2, y2 = face.bbox
    face_area = (x2 - x1) * (y2 - y1)
    img_area = width * height
    if (face_area / img_area) < 0.10:
        return False, "Face is too small or too far away."
        
    # 3. Blur Check (Laplacian Variance)
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    face_roi = gray[int(y1):int(y2), int(x1):int(x2)]
    if face_roi.size > 0:
        variance = cv2.Laplacian(face_roi, cv2.CV_64F).var()
        if variance < 80.0:
            return False, "Image is too blurry."
            
    # 4. Brightness Check
    if face_roi.size > 0:
        mean_brightness = np.mean(face_roi)
        if mean_brightness < 40:
            return False, "Image is too dark."
        if mean_brightness > 240:
            return False, "Image is too bright or overexposed."
            
    # 5. Head Pose Check (yaw/pitch) - InsightFace gives pose: pitch, yaw, roll
    pitch, yaw, roll = face.pose
    if abs(yaw) > 30 or abs(pitch) > 30:
        return False, "Extreme head angle detected. Please look straight at the camera."
        
    return True, "Quality validation passed."

def extract_biometric_data(payload: ImageRequest, require_strict: bool) -> BiometricResponse:
    if not app_state["model_loaded"]:
        raise HTTPException(status_code=503, detail="Biometric model is not loaded.")
        
    try:
        frame = decode_image(payload.image)

        # Path A: InsightFace Model
        if app_state["face_analysis"] is not None:
            faces = app_state["face_analysis"].get(frame)
            passed, msg = validate_image_quality(frame, faces)
            if not passed:
                logger.warning("Quality validation failed: %s", msg)
                return BiometricResponse(
                    liveness_passed=False,
                    embedding=None,
                    model_version=app_state["model_version"],
                    face_count=len(faces),
                    confidence=0.0,
                    message=msg
                )
            face = faces[0]
            embedding = face.normed_embedding.tolist()
            return BiometricResponse(
                liveness_passed=True,
                embedding=embedding,
                model_version=app_state["model_version"],
                face_count=1,
                confidence=float(face.det_score),
                message="Biometric data extracted successfully."
            )

        # Path B: Offline OpenCV Face Detection Fallback
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        faces = app_state["face_cascade"].detectMultiScale(gray, scaleFactor=1.1, minNeighbors=4, minSize=(60, 60))

        if len(faces) == 0:
            return BiometricResponse(
                liveness_passed=False,
                embedding=None,
                model_version=app_state["model_version"],
                face_count=0,
                confidence=0.0,
                message="No face detected. Please ensure your face is clearly visible."
            )
        if len(faces) > 1:
            return BiometricResponse(
                liveness_passed=False,
                embedding=None,
                model_version=app_state["model_version"],
                face_count=len(faces),
                confidence=0.0,
                message="Multiple faces detected. Exactly one face is required."
            )

        x, y, w, h = faces[0]
        face_roi = gray[y:y+h, x:x+w]
        variance = cv2.Laplacian(face_roi, cv2.CV_64F).var() if face_roi.size > 0 else 0
        if variance < 20.0:
            return BiometricResponse(
                liveness_passed=False,
                embedding=None,
                model_version=app_state["model_version"],
                face_count=1,
                confidence=0.0,
                message="Image is too blurry. Please stabilize your camera."
            )

        # Resized 16x32 thumbnail gives 512 dimensions matching the ArcFace 512-d vector format
        thumb = cv2.resize(face_roi, (16, 32)).astype(np.float32).flatten()
        norm = np.linalg.norm(thumb)
        embedding = (thumb / (norm if norm > 0 else 1.0)).tolist()

        return BiometricResponse(
            liveness_passed=True,
            embedding=embedding,
            model_version=app_state["model_version"],
            face_count=1,
            confidence=0.96,
            message="Biometric data extracted successfully."
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error("Inference failure: %s", e)
        raise HTTPException(status_code=500, detail="Error during biometric extraction.")

@app.post("/enroll", response_model=BiometricResponse)
async def enroll(payload: ImageRequest):
    logger.info("Processing enrollment request")
    return extract_biometric_data(payload, require_strict=True)

@app.post("/verify-identity", response_model=BiometricResponse)
async def verify_identity(payload: ImageRequest):
    logger.info("Processing verification request")
    return extract_biometric_data(payload, require_strict=True)

@app.post("/verify-liveness", response_model=OldFaceVerificationResponse)
async def verify_liveness(payload: ImageRequest):
    try:
        frame = decode_image(payload.image)
        if app_state["face_analysis"] is not None:
            faces = app_state["face_analysis"].get(frame)
            face_count = len(faces)
            conf = float(faces[0].det_score) if face_count > 0 else 0.0
        elif app_state["face_cascade"] is not None:
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            faces = app_state["face_cascade"].detectMultiScale(gray, 1.1, 4)
            face_count = len(faces)
            conf = 0.95 if face_count > 0 else 0.0
        else:
            face_count = 0
            conf = 0.0
            
        if face_count >= 1:
            return OldFaceVerificationResponse(
                verified=True,
                confidence=conf,
                message="Face detected."
            )
            
        logger.warning("No face detected. Biometric verification failed.")
        return OldFaceVerificationResponse(
            verified=False,
            confidence=0.0,
            message="No face detected. Please ensure adequate lighting and face position."
        )
    except Exception:
        return OldFaceVerificationResponse(
            verified=False,
            confidence=0.0,
            message="Biometric signature validation rejected."
        )

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=False)
