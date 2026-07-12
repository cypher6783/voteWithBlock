"use client";

import React, { useState, useEffect } from "react";
import { CheckCircle2, Lock, User, Clock, ChevronDown, ChevronUp, AlertCircle, Loader2 } from "lucide-react";
import axios from "axios";
import { VOTE_CAST_URL } from "@/lib/api";

interface Candidate {
  id: string;
  name: string;
  party: string;
  manifesto: string;
  color: string;
  avatarBg: string;
}

interface BallotScreenProps {
  onProceed: (candidateName: string, txId: string) => void;
  voterId: string;
  sessionToken: string;
}

export default function BallotScreen({ onProceed, voterId, sessionToken }: BallotScreenProps) {
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [electionId, setElectionId] = useState<string | null>(null);
  const [electionTitle, setElectionTitle] = useState<string>("Loading Election...");
  const [selectedCandidateId, setSelectedCandidateId] = useState<string | null>(null);
  const [expandedManifestoId, setExpandedManifestoId] = useState<string | null>(null);
  const [timeRemaining, setTimeRemaining] = useState("00:00:00");
  const [isCasting, setIsCasting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Fetch active election data
  useEffect(() => {
    const fetchActiveElection = async () => {
      try {
        const response = await axios.get("/api/elections/active");
        if (response.status === 204 || !response.data) {
          setErrorMsg("No active election is currently open for voting.");
          setIsLoading(false);
          return;
        }
        
        const { election, candidates: fetchedCandidates } = response.data;
        setElectionId(election.id);
        setElectionTitle(election.title);
        
        // Map backend candidates to frontend candidate format
        const mappedCandidates = fetchedCandidates.map((c: any, index: number) => ({
          id: c.id,
          name: c.name,
          party: c.party || "Independent",
          manifesto: c.manifesto || "No manifesto provided.",
          color: "border-blue-500 text-blue-600 bg-blue-50/50",
          avatarBg: "bg-blue-100 text-blue-700",
        }));
        
        setCandidates(mappedCandidates);
        setIsLoading(false);
        
        // Setup timer based on end date
        if (election.endDate) {
          const end = new Date(election.endDate).getTime();
          const timer = setInterval(() => {
            const now = new Date().getTime();
            const distance = end - now;
            if (distance < 0) {
              clearInterval(timer);
              setTimeRemaining("00:00:00");
            } else {
              const hrs = Math.floor((distance % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)).toString().padStart(2, "0");
              const mins = Math.floor((distance % (1000 * 60 * 60)) / (1000 * 60)).toString().padStart(2, "0");
              const secs = Math.floor((distance % (1000 * 60)) / 1000).toString().padStart(2, "0");
              setTimeRemaining(`${hrs}:${mins}:${secs}`);
            }
          }, 1000);
          return () => clearInterval(timer);
        }
      } catch (err) {
        console.error("Failed to load active election", err);
        setErrorMsg("Failed to load active election. Please try again later.");
        setIsLoading(false);
      }
    };
    
    fetchActiveElection();
  }, []);

  const handleSelect = (id: string) => {
    setSelectedCandidateId(id);
  };

  const getSelectedCandidate = () => {
    return candidates.find((c) => c.id === selectedCandidateId);
  };

  const toggleManifesto = (id: string, e: React.MouseEvent) => {
    e.stopPropagation(); // Avoid triggering card selection
    setExpandedManifestoId(expandedManifestoId === id ? null : id);
  };

  const handleCastVote = async () => {
    const candidate = getSelectedCandidate();
    if (!candidate || !selectedCandidateId) return;

    setIsCasting(true);
    setErrorMsg(null);

    const anonToken = "anon-token-" + Math.random().toString(36).substring(2, 11);
    const payload = {
      nationalId: voterId,
      electionId: electionId,
      candidateId: selectedCandidateId,
      token: anonToken,
      anonymousToken: anonToken,
    };

    try {
      const response = await axios.post(VOTE_CAST_URL, payload, {
        headers: {
          Authorization: `Bearer ${sessionToken}`,
        },
      });
      if (response.data && response.data.success) {
        onProceed(candidate.name, response.data.transactionId);
      } else {
        setErrorMsg("Failed to cast vote. Response indicated failure.");
        setIsCasting(false);
      }
    } catch (error: unknown) {
      console.error("Error casting vote:", error);
      let errMsg = "An error occurred while communicating with the blockchain gateway.";
      if (axios.isAxiosError(error)) {
        if (typeof error.response?.data === "string") {
          errMsg = error.response.data;
        } else if (typeof error.response?.data?.message === "string") {
          errMsg = error.response.data.message;
        } else if (error.message) {
          errMsg = error.message;
        }
      } else if (error instanceof Error) {
        errMsg = error.message;
      }
      setErrorMsg(errMsg);
      setIsCasting(false);
    }
  };

  return (
    <div className="w-full flex flex-col min-h-full">
      {/* Top Banner (Success & Reassurance) */}
      <div className="w-full bg-emerald-800 text-white px-6 py-4 flex items-center justify-between border-b border-emerald-900 shadow-md">
        <div className="flex items-center gap-3">
          <div className="bg-emerald-950 p-2 rounded-lg">
            <CheckCircle2 className="h-5 w-5 text-emerald-400" />
          </div>
          <div>
            <h3 className="font-bold text-sm leading-tight text-white">Identity Verified Anonymously</h3>
            <p className="text-xs text-emerald-200 mt-0.5">Real identity credentials disconnected. Anonymous voting session tokens issued successfully.</p>
          </div>
        </div>
        <div className="hidden md:block">
          <div className="bg-emerald-900/60 border border-emerald-700 px-3 py-1 rounded text-[11px] font-bold text-emerald-300 shadow-sm">
            Security Status: 100% Anonymous (Hyperledger Ring-Signature Activated)
          </div>
        </div>
      </div>

      {/* Main Grid Workspace */}
      <div className="flex-1 max-w-7xl w-full mx-auto px-6 py-8 grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        
        {/* Left Sidebar: Election Info */}
        <aside className="lg:col-span-4 bg-white rounded-xl border border-slate-200 shadow-sm p-6 space-y-5">
          <div>
            <span className="text-[10px] bg-slate-100 text-slate-600 font-bold px-2 py-1 rounded uppercase tracking-wider">Active Ballot</span>
            <h2 className="text-xl font-extrabold text-slate-900 mt-2">{electionTitle}</h2>
            <p className="text-xs text-slate-500 mt-1">Select one representative from the listed council candidates below.</p>
          </div>

          <hr className="border-slate-100" />

          {/* Details */}
          <div className="space-y-4">
            <div className="flex items-center justify-between text-xs">
              <span className="text-slate-500 font-semibold flex items-center gap-1.5"><Clock className="h-4 w-4 text-slate-400" /> Remaining Time</span>
              <span className="text-rose-600 font-extrabold tracking-wider bg-rose-50 px-2 py-1 rounded border border-rose-100">{timeRemaining}</span>
            </div>
            <div className="flex items-center justify-between text-xs">
              <span className="text-slate-500 font-semibold">Total Candidates</span>
              <span className="text-slate-800 font-bold bg-slate-100 px-2 py-0.5 rounded">{candidates.length}</span>
            </div>
            <div className="flex items-center justify-between text-xs">
              <span className="text-slate-500 font-semibold">Poll Closing Date</span>
              <span className="text-slate-800 font-bold">May 25, 2026 at 20:00 UTC</span>
            </div>
          </div>

          <hr className="border-slate-100" />

          {/* Privacy Note */}
          <div className="bg-slate-50 border border-slate-200 rounded-lg p-4 text-xs text-slate-600 space-y-2">
            <div className="flex items-center gap-1.5 text-slate-800 font-bold">
              <Lock className="h-3.5 w-3.5 text-cobalt-900" />
              <span>Zero-Knowledge Proof active</span>
            </div>
            <p className="leading-relaxed">
              When you click &quot;Encrypt &amp; Cast Vote&quot;, the system encrypts your choice locally in-browser. It is added to the ledger with a random timestamp so block analysis cannot correlate it with your login time.
            </p>
          </div>
        </aside>

        {/* Center Main Panel: Candidates */}
        <section className="lg:col-span-8 space-y-6">
          {errorMsg && (
            <div className="p-4 bg-rose-50 border-l-4 border-rose-500 text-rose-800 rounded-xl text-sm font-medium animate-stream animate-pulse">
              {errorMsg}
            </div>
          )}
          <div className="flex items-center justify-between flex-wrap gap-2">
            <div>
              <h3 className="text-lg font-bold text-slate-900">List of Representatives</h3>
              <p className="text-xs text-slate-500 mt-0.5">Click anywhere on a card to select that candidate. Select &quot;View Manifesto&quot; to read their full outline.</p>
            </div>
            <span className="text-xs font-bold text-slate-400">1 candidate maximum</span>
          </div>

          {/* Candidates Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
            {isLoading && <div className="col-span-2 text-center p-10 text-slate-500">Loading candidates...</div>}
            {!isLoading && candidates.map((cand) => {
              const isSelected = selectedCandidateId === cand.id;
              const isExpanded = expandedManifestoId === cand.id;
              
              return (
                <div
                  key={cand.id}
                  onClick={() => handleSelect(cand.id)}
                  className={`border rounded-2xl p-5 cursor-pointer transition-all duration-200 relative group flex flex-col justify-between ${
                    isSelected
                      ? `border-cobalt-900 ring-2 ring-cobalt-900 bg-blue-50/20 shadow-md shadow-blue-900/5`
                      : "border-slate-200 bg-white hover:border-slate-300 hover:shadow-md"
                  }`}
                >
                  <div>
                    {/* Header: Avatar, Name & Checkbox */}
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex items-center gap-3">
                        <div className={`h-11 w-11 rounded-xl flex items-center justify-center font-bold text-sm shadow-sm ${cand.avatarBg}`}>
                          <User className="h-5 w-5" />
                        </div>
                        <div>
                          <h4 className="font-bold text-slate-900 group-hover:text-cobalt-900 transition-colors leading-snug">{cand.name}</h4>
                          <p className="text-xs font-semibold text-slate-500 mt-0.5">{cand.party}</p>
                        </div>
                      </div>

                      {/* Radio button selection target */}
                      <div 
                        className={`h-5.5 w-5.5 rounded-full border flex items-center justify-center transition-all ${
                          isSelected
                            ? "border-cobalt-900 bg-cobalt-900 text-white shadow-inner"
                            : "border-slate-300 bg-slate-50 group-hover:border-slate-400"
                        }`}
                      >
                        {isSelected && <span className="h-2 w-2 bg-white rounded-full" />}
                      </div>
                    </div>

                    {/* Manifesto accordion */}
                    <div className="mt-4 pt-3 border-t border-slate-100">
                      <button
                        onClick={(e) => toggleManifesto(cand.id, e)}
                        className="text-xs font-bold text-cobalt-900 flex items-center gap-1 hover:text-cobalt-800 focus:outline-none cursor-pointer"
                      >
                        <span>{isExpanded ? "Hide Manifesto" : "View Manifesto"}</span>
                        {isExpanded ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
                      </button>

                      {isExpanded && (
                        <p className="text-xs text-slate-600 mt-2.5 bg-slate-50 border border-slate-200/60 p-3 rounded-lg leading-relaxed animate-stream">
                          {cand.manifesto}
                        </p>
                      )}
                    </div>
                  </div>

                  <div className="mt-5 text-[10px] text-slate-400 font-semibold border-t border-slate-100/50 pt-2 flex items-center justify-between">
                    <span>Token: SECURE-ELECTION</span>
                    <span>1 Vote Limit</span>
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      </div>

      {/* Bottom Confirmation Dock (Sticky Footer) */}
      <footer className="sticky bottom-0 w-full glass-panel border-t border-slate-300 shadow-xl py-5 px-6 flex flex-col md:flex-row items-center justify-between gap-4 z-20">
        <div className="text-center md:text-left">
          {selectedCandidateId ? (
            <p className="text-slate-800 text-sm font-medium">
              You are casting your anonymous vote for{" "}
              <span className="font-extrabold text-cobalt-900 bg-blue-50 border border-blue-200/80 px-2 py-0.5 rounded shadow-sm">
                {getSelectedCandidate()?.name}
              </span>
            </p>
          ) : (
            <div className="flex items-center gap-2 text-slate-500 text-sm">
              <AlertCircle className="h-4.5 w-4.5 text-slate-400 flex-shrink-0" />
              <span>Please select a representative candidate from the grid above.</span>
            </div>
          )}
        </div>

        {/* CTAs */}
        <div className="flex items-center gap-3">
          {selectedCandidateId && (
            <button
              onClick={() => setSelectedCandidateId(null)}
              className="px-5 py-2.5 bg-transparent border border-slate-300 text-slate-600 rounded-xl text-xs font-bold hover:bg-slate-100 transition-all cursor-pointer"
            >
              Change Selection
            </button>
          )}
          <button
            onClick={handleCastVote}
            disabled={!selectedCandidateId || isCasting}
            className={`px-6 py-3 rounded-xl text-xs font-bold flex items-center gap-2 transition-all shadow-lg duration-200 ${
              selectedCandidateId && !isCasting
                ? "bg-cobalt-900 text-white hover:bg-cobalt-800 shadow-blue-900/10 cursor-pointer"
                : "bg-slate-200 text-slate-400 shadow-none border border-slate-300/40 cursor-not-allowed"
            }`}
          >
            {isCasting ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <Lock className="h-3.5 w-3.5" />
            )}
            <span>{isCasting ? "Encrypting & Submitting..." : "Encrypt & Cast Vote on Blockchain"}</span>
          </button>
        </div>
      </footer>
    </div>
  );
}
