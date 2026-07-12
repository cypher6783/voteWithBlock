"use client";

import React, { useState } from "react";
import { Shield } from "lucide-react";
import LoginScreen from "@/components/LoginScreen";
import BiometricScreen from "@/components/BiometricScreen";
import BallotScreen from "@/components/BallotScreen";
import AuditScreen from "@/components/AuditScreen";

export default function Home() {
  const [currentStep, setCurrentStep] = useState<1 | 2 | 3 | 4>(1);
  const [isHighContrast, setIsHighContrast] = useState(false);
  const [fontSize, setFontSize] = useState(16); // 16px base font
  
  // Voting session state
  const [voterId, setVoterId] = useState("");
  const [preAuthToken, setPreAuthToken] = useState("");
  const [sessionToken, setSessionToken] = useState("");
  const [selectedCandidate, setSelectedCandidate] = useState("");
  const [transactionId, setTransactionId] = useState("");

  const handleLoginProceed = (id: string, token: string) => {
    setVoterId(id);
    setPreAuthToken(token);
    setCurrentStep(2);
  };

  const handleBiometricProceed = (sessionTokenStr: string) => {
    setSessionToken(sessionTokenStr);
    setCurrentStep(3);
  };

  const handleBallotProceed = (candidateName: string, txId: string) => {
    setSelectedCandidate(candidateName);
    setTransactionId(txId);
    setCurrentStep(4);
  };

  const handleResetFlow = () => {
    setVoterId("");
    setPreAuthToken("");
    setSessionToken("");
    setSelectedCandidate("");
    setTransactionId("");
    setCurrentStep(1);
  };

  return (
    <div 
      className={`min-h-screen flex flex-col transition-all duration-200 bg-slate-950 ${
        isHighContrast ? "high-contrast" : ""
      }`}
      style={{ 
        // Dynamic root font size scaling
        "--base-font-size": `${fontSize}px`,
      } as React.CSSProperties & Record<"--base-font-size", string>}
    >
      {/* Top Application Header */}
      <nav className="w-full bg-slate-900 border-b border-slate-800 text-slate-100 py-3.5 px-6 flex flex-wrap items-center justify-between gap-4 select-none z-30 shadow-md">
        <div className="flex items-center gap-3">
          <div className="bg-blue-600 p-2 rounded-lg text-white">
            <Shield className="h-5 w-5" />
          </div>
          <div>
            <h1 className="font-extrabold text-sm tracking-wide text-slate-100">Votetrust Ledger</h1>
            <p className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider">Enterprise Decentralized Voting</p>
          </div>
        </div>

        {/* High Contrast / Font size knobs */}
        <div className="flex items-center gap-2 bg-slate-800 p-1.5 rounded-xl border border-slate-700 text-xs">
          <button
            onClick={() => setIsHighContrast(!isHighContrast)}
            className={`p-1.5 rounded-lg font-bold transition-all ${
              isHighContrast ? "bg-white text-slate-900" : "text-slate-400 hover:text-slate-200 cursor-pointer"
            }`}
            title="Toggle high contrast colors"
          >
            Contrast
          </button>
          <div className="h-3.5 w-px bg-slate-600" />
          <button
            onClick={() => setFontSize(Math.max(14, fontSize - 1))}
            className="p-1 text-slate-400 hover:text-slate-200 font-bold"
            title="Shrink text sizes"
          >
            A-
          </button>
          <span className="text-[10px] text-slate-500 font-bold">{fontSize}px</span>
          <button
            onClick={() => setFontSize(Math.min(22, fontSize + 1))}
            className="p-1 text-slate-400 hover:text-slate-200 font-bold"
            title="Enlarge text sizes"
          >
            A+
          </button>
        </div>
      </nav>

      {/* Main Application Area */}
      <div className="flex-1 overflow-auto p-6 flex items-center justify-center relative min-h-[500px]">
        <div className="w-full max-w-4xl bg-slate-50 border border-slate-200 rounded-3xl overflow-hidden shadow-2xl relative animate-stream">
          
          {currentStep === 1 && (
            <LoginScreen
              onProceed={handleLoginProceed}
              isHighContrast={isHighContrast}
              setIsHighContrast={setIsHighContrast}
              fontSize={fontSize}
              setFontSize={setFontSize}
            />
          )}

          {currentStep === 2 && (
            <BiometricScreen
              onProceed={handleBiometricProceed}
              voterId={voterId}
              preAuthToken={preAuthToken}
            />
          )}

          {currentStep === 3 && (
              <BallotScreen
                onProceed={handleBallotProceed}
                voterId={voterId}
                sessionToken={sessionToken}
              />
          )}

          {currentStep === 4 && (
            <AuditScreen
              onReset={handleResetFlow}
              selectedCandidate={selectedCandidate}
              transactionId={transactionId}
            />
          )}
        </div>
      </div>
    </div>
  );
}
