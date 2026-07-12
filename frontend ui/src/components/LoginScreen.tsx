"use client";

import React, { useState } from "react";
import { Shield, Eye, EyeOff, Info, CheckCircle, Volume2, Sparkles, ArrowRight } from "lucide-react";
import axios from "axios";
import { AUTH_LOGIN_URL } from "@/lib/api";

interface LoginScreenProps {
  onProceed: (voterId: string, preAuthToken: string) => void;
  isHighContrast: boolean;
  setIsHighContrast: (val: boolean) => void;
  fontSize: number;
  setFontSize: (size: number) => void;
}

export default function LoginScreen({
  onProceed,
  isHighContrast,
  setIsHighContrast,
  fontSize,
  setFontSize,
}: LoginScreenProps) {
  const [nationalId, setNationalId] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSimulatingSpeech, setIsSimulatingSpeech] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!nationalId.trim() || !password.trim()) {
      setError("Please fill in both your National ID/Username and Password.");
      return;
    }
    setIsSubmitting(true);
    setError("");
    try {
      const response = await axios.post(AUTH_LOGIN_URL, {
        nationalId: nationalId.trim(),
        password,
      });
      if (response.data.biometricEnrolled === false) {
        setError("Your account is not enrolled for biometric verification. Please contact an administrator.");
        return;
      }
      onProceed(response.data.nationalId, response.data.preAuthToken);
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        setError("Invalid credentials. Check your National ID and password.");
      } else if (axios.isAxiosError(err) && err.response?.status === 429) {
        setError("Too many login attempts. Please wait a moment and try again.");
      } else {
        setError("Authentication service is unavailable. Please try again.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const speakAccessibility = () => {
    if (isSimulatingSpeech) {
      setIsSimulatingSpeech(false);
      return;
    }
    setIsSimulatingSpeech(true);
    // Simulate reading the main parts of the screen
    const text = "Welcome to Votetrust Ledger. Please enter your Username or National ID and Password. On your right is the how it works section, which explains that identity is verified off-chain, biometrics are never stored on the blockchain, and zero cryptocurrency is required.";
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.onend = () => setIsSimulatingSpeech(false);
    window.speechSynthesis.speak(utterance);
  };

  return (
    <div className="w-full flex flex-col min-h-full">
      {/* Top Navigation */}
      <header className="w-full px-6 py-4 glass-panel border-b border-slate-200/80 flex items-center justify-between flex-wrap gap-4 z-10">
        <div className="flex items-center gap-3">
          <div className="bg-cobalt-900 text-white p-2.5 rounded-xl shadow-lg shadow-blue-900/20">
            <Shield className="h-6 w-6" aria-hidden="true" />
          </div>
          <div>
            <h1 className="text-xl font-bold tracking-tight text-slate-900">Votetrust Ledger</h1>
            <p className="text-xs text-slate-500 font-medium">Enterprise Decentralized Voting</p>
          </div>
        </div>

        {/* Status and Accessiblity controls */}
        <div className="flex items-center gap-4 flex-wrap">
          {/* System Status */}
          <div className="flex items-center gap-2 bg-emerald-50 border border-emerald-200/80 text-emerald-800 px-3 py-1.5 rounded-full text-xs font-semibold shadow-sm">
            <span className="h-2.5 w-2.5 rounded-full bg-emerald-500 animate-pulse" />
            <span>Blockchain Network Secure</span>
          </div>

          {/* Accessibility Settings Toggles */}
          <div className="flex items-center gap-1.5 bg-slate-100 p-1 rounded-lg">
            <button
              onClick={() => setIsHighContrast(!isHighContrast)}
              className={`p-1.5 rounded-md text-xs font-medium transition-colors ${
                isHighContrast 
                  ? "bg-slate-900 text-white shadow-sm" 
                  : "text-slate-600 hover:bg-slate-200"
              }`}
              title="Toggle High Contrast Mode"
              aria-label="Toggle High Contrast Mode"
            >
              Contrast
            </button>
            <div className="h-4 w-px bg-slate-300" />
            <button
              onClick={() => setFontSize(Math.max(14, fontSize - 1))}
              className="p-1.5 rounded-md text-xs text-slate-600 hover:bg-slate-200 font-bold"
              title="Decrease Font Size"
              aria-label="Decrease Font Size"
            >
              A-
            </button>
            <button
              onClick={() => setFontSize(Math.min(20, fontSize + 1))}
              className="p-1.5 rounded-md text-xs text-slate-600 hover:bg-slate-200 font-bold"
              title="Increase Font Size"
              aria-label="Increase Font Size"
            >
              A+
            </button>
            <div className="h-4 w-px bg-slate-300" />
            <button
              onClick={speakAccessibility}
              className={`p-1.5 rounded-md text-xs transition-colors flex items-center gap-1 ${
                isSimulatingSpeech 
                  ? "bg-cobalt-900 text-white animate-pulse" 
                  : "text-slate-600 hover:bg-slate-200"
              }`}
              title="Read Page Aloud"
              aria-label="Read Page Aloud"
            >
              <Volume2 className="h-3.5 w-3.5" />
              <span>Listen</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-6 py-8 flex flex-col justify-center gap-8">
        
        {/* Hero Section */}
        <section className="text-center max-w-3xl mx-auto py-4">
          <div className="inline-flex items-center gap-2 bg-blue-50 border border-blue-100 text-blue-800 px-4 py-1.5 rounded-full text-xs font-semibold mb-4">
            <Sparkles className="h-3.5 w-3.5" />
            <span>Hyperledger Fabric Integration</span>
          </div>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-slate-900 tracking-tight leading-tight">
            Secure, Anonymous, and Immutable Digital Voting Powered by Hyperledger
          </h2>
          <p className="mt-3 text-base text-slate-600 max-w-2xl mx-auto">
            An enterprise-grade, cryptographically verifiable national election gateway. Your credentials grant a one-time voting token completely decoupled from your biometric check.
          </p>
        </section>

        {/* Two-Column Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-stretch max-w-5xl mx-auto w-full">
          {/* Left Column: Login Card */}
          <div className="lg:col-span-7 bg-white rounded-2xl border border-slate-200 shadow-xl shadow-slate-900/5 p-8 flex flex-col justify-between">
            <div>
              <div className="mb-6">
                <h3 className="text-xl font-bold text-slate-900">Voter Authentication</h3>
                <p className="text-sm text-slate-500 mt-1">Enter your government-issued credentials to load your ballot profile.</p>
              </div>

              {error && (
                <div className="mb-5 p-3.5 bg-rose-50 border-l-4 border-rose-500 text-rose-800 rounded text-sm font-medium">
                  {error}
                </div>
              )}

              <form onSubmit={handleSubmit} className="space-y-5">
                <div>
                  <label htmlFor="national-id" className="block text-sm font-semibold text-slate-700 mb-1.5">
                    Username or National ID Number
                  </label>
                  <input
                    id="national-id"
                    type="text"
                    required
                    value={nationalId}
                    onChange={(e) => setNationalId(e.target.value)}
                    className="w-full px-4 py-3 rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-cobalt-900 focus:border-cobalt-900 transition-all font-medium text-slate-900 placeholder:text-slate-400"
                    placeholder="e.g. US-98420-A"
                  />
                </div>

                <div>
                  <label htmlFor="password" className="block text-sm font-semibold text-slate-700 mb-1.5">
                    Security Password
                  </label>
                  <div className="relative">
                    <input
                      id="password"
                      type={showPassword ? "text" : "password"}
                      required
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      className="w-full px-4 py-3 pr-11 rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-cobalt-900 focus:border-cobalt-900 transition-all font-medium text-slate-900 placeholder:text-slate-400"
                      placeholder="••••••••••••"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 p-1"
                      title={showPassword ? "Hide password" : "Show password"}
                    >
                      {showPassword ? <EyeOff className="h-4.5 w-4.5" /> : <Eye className="h-4.5 w-4.5" />}
                    </button>
                  </div>
                </div>

                <div className="pt-2">
                  <button
                    type="submit"
                    disabled={isSubmitting}
                    className="w-full bg-cobalt-900 hover:bg-cobalt-800 text-white font-bold py-3.5 px-6 rounded-xl transition-all duration-150 flex items-center justify-center gap-2 shadow-lg shadow-blue-900/10 cursor-pointer"
                  >
                    <span>{isSubmitting ? "Authenticating..." : "Authenticate & Proceed"}</span>
                    <ArrowRight className="h-4.5 w-4.5" />
                  </button>
                </div>
              </form>
            </div>

            <div className="mt-8 pt-5 border-t border-slate-100 flex items-center gap-2.5 text-xs text-slate-500">
              <Shield className="h-4 w-4 text-cobalt-900 flex-shrink-0" />
              <span>Standard military-grade AES-256 session encryption is active.</span>
            </div>
          </div>

          {/* Right Column: Context Info Panel */}
          <div className="lg:col-span-5 bg-slate-900 text-white rounded-2xl p-8 flex flex-col justify-between relative overflow-hidden shadow-xl shadow-slate-950/20">
            {/* Background vector accents */}
            <div className="absolute top-0 right-0 w-48 h-48 bg-blue-600/10 rounded-full blur-3xl pointer-events-none" />
            <div className="absolute bottom-0 left-0 w-32 h-32 bg-emerald-500/5 rounded-full blur-2xl pointer-events-none" />

            <div className="z-10">
              <div className="flex items-center gap-2 text-blue-400 font-bold text-xs uppercase tracking-wider mb-3">
                <Info className="h-4 w-4" />
                <span>Security Briefing</span>
              </div>
              <h3 className="text-xl font-bold text-slate-100 mb-6">How it Works</h3>

              <div className="space-y-6">
                {/* Point 1 */}
                <div className="flex gap-4">
                  <div className="flex-shrink-0 w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-sm font-bold text-blue-400 shadow-inner">
                    1
                  </div>
                  <div>
                    <h4 className="font-semibold text-slate-100">Off-Chain Identity Verification</h4>
                    <p className="text-sm text-slate-400 mt-1 leading-relaxed">
                      Your credentials are authenticated securely via a government directory over SSL. Your real identity is disconnected from the ballot before any block is written.
                    </p>
                  </div>
                </div>

                {/* Point 2 */}
                <div className="flex gap-4">
                  <div className="flex-shrink-0 w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-sm font-bold text-blue-400 shadow-inner">
                    2
                  </div>
                  <div>
                    <h4 className="font-semibold text-slate-100">Private Biometrics Guard</h4>
                    <p className="text-sm text-slate-400 mt-1 leading-relaxed">
                      Face scans are verified locally in your browser to generate a temporary vector. Biometric imagery is destroyed instantly, never stored, and never touches the blockchain.
                    </p>
                  </div>
                </div>

                {/* Point 3 */}
                <div className="flex gap-4">
                  <div className="flex-shrink-0 w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-sm font-bold text-blue-400 shadow-inner">
                    3
                  </div>
                  <div>
                    <h4 className="font-semibold text-slate-100">Zero Cryptocurrency Required</h4>
                    <p className="text-sm text-slate-400 mt-1 leading-relaxed">
                      This is an enterprise permissioned ledger. Voters do not need wallets, secret seed phrases, tokens, or gas. Transactions are fully sponsored by the system.
                    </p>
                  </div>
                </div>
              </div>
            </div>

            <div className="mt-8 pt-5 border-t border-slate-800 z-10 flex items-center justify-between text-xs text-slate-400">
              <span className="flex items-center gap-1.5">
                <CheckCircle className="h-3.5 w-3.5 text-emerald-500" />
                Voter Privacy Guaranteed
              </span>
              <span className="text-slate-500">v1.2-Fabric</span>
            </div>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="w-full text-center py-6 text-xs text-slate-400 mt-auto border-t border-slate-200 bg-white/50">
        <p>© 2026 Votetrust Ledger System. Authorized for certified national, municipal, and institutional elections.</p>
      </footer>
    </div>
  );
}
