"use client";

import React, { useState, useEffect } from "react";
import { CheckCircle, Copy, LogOut, ShieldCheck, Database } from "lucide-react";

interface BlockEntry {
  height: number;
  timestamp: string;
  txHash: string;
  payloadHash: string;
  channel: string;
}

interface AuditScreenProps {
  onReset: () => void;
  selectedCandidate: string;
  transactionId: string;
}

// Helper to generate a random mock hash
const generateRandomHash = (length = 64) => {
  const chars = "0123456789abcdef";
  let hash = "";
  for (let i = 0; i < length; i++) {
    hash += chars[Math.floor(Math.random() * chars.length)];
  }
  return hash;
};

const createInitialBlocks = (): BlockEntry[] => {
  const baseHeight = 184024;
  const now = new Date();

  return Array.from({ length: 5 }).map((_, index) => {
    const blockTime = new Date(now.getTime() - index * 45000);
    return {
      height: baseHeight - index,
      timestamp: blockTime.toLocaleTimeString(),
      txHash: `tx-f28a-${generateRandomHash(16)}`,
      payloadHash: generateRandomHash(40),
      channel: "governance-channel",
    };
  });
};

export default function AuditScreen({ onReset, selectedCandidate, transactionId }: AuditScreenProps) {
  const [copied, setCopied] = useState(false);
  const [blocks, setBlocks] = useState<BlockEntry[]>(createInitialBlocks);

  // Simulating real-time block streaming
  useEffect(() => {
    const interval = setInterval(() => {
      const now = new Date();
      setBlocks((prev) => {
        const nextHeight = prev.length > 0 ? prev[0].height + 1 : 184025;
        const newBlock: BlockEntry = {
          height: nextHeight,
          timestamp: now.toLocaleTimeString(),
          txHash: `tx-f28a-${generateRandomHash(16)}`,
          payloadHash: generateRandomHash(40),
          channel: "governance-channel",
        };
        // Prepend new block and limit to last 6 entries
        return [newBlock, ...prev.slice(0, 5)];
      });
    }, 6000); // add a new block every 6 seconds

    return () => clearInterval(interval);
  }, []);

  const handleCopy = () => {
    navigator.clipboard.writeText(transactionId);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="w-full flex flex-col min-h-full">
      {/* Dynamic Success Announcement */}
      <div className="w-full bg-slate-900 text-white py-12 px-6 text-center border-b border-slate-800 relative overflow-hidden">
        {/* Background vector icons */}
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 text-slate-850 opacity-10 pointer-events-none">
          <Database className="h-64 w-64 animate-pulse-slow" />
        </div>

        <div className="relative z-10 max-w-2xl mx-auto flex flex-col items-center gap-4">
          <div className="bg-emerald-500 text-slate-950 p-4 rounded-full shadow-lg shadow-emerald-500/20 flex items-center justify-center animate-bounce">
            <CheckCircle className="h-12 w-12" />
          </div>
          <div>
            <h2 className="text-3xl font-extrabold text-slate-50 tracking-tight">Ballot Successfully Written to Ledger!</h2>
            <p className="text-sm text-slate-400 mt-2 max-w-lg mx-auto">
              Your choice has been encrypted, assigned a randomized epoch sequence slot, and committed permanently to the Hyperledger Fabric block state.
            </p>
          </div>
        </div>
      </div>

      <main className="flex-1 max-w-5xl w-full mx-auto px-6 py-8 flex flex-col gap-8">
        {/* Grid containing Receipt and Audit Log */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          
          {/* Left Column: Metric Card & Info Box */}
          <div className="lg:col-span-5 space-y-6">
            
            {/* Primary Metric Card */}
            <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-sm">
              <span className="text-[10px] text-cobalt-900 bg-blue-50 border border-blue-200/50 px-2 py-0.5 rounded font-extrabold uppercase tracking-wider">
                Blockchain Receipt
              </span>
              
              <h3 className="text-sm font-bold text-slate-800 mt-3.5">
                Anonymous Blockchain Ballot Token / Receipt Hash (Cast-As-Intended Verifier)
              </h3>
              
              {/* Copyable Hash Box */}
              <div className="mt-3 relative flex items-center bg-slate-50 border border-slate-200 rounded-lg overflow-hidden group">
                <code className="text-xs text-slate-700 p-3.5 pr-14 font-mono break-all leading-normal flex-1">
                  {transactionId || "Pending Verification"}
                </code>
                <button
                  onClick={handleCopy}
                  className={`absolute right-2.5 top-1/2 -translate-y-1/2 p-2 rounded-md border text-xs font-semibold flex items-center gap-1 transition-all ${
                    copied
                      ? "bg-emerald-50 border-emerald-300 text-emerald-700"
                      : "bg-white border-slate-200 text-slate-600 hover:bg-slate-100 cursor-pointer"
                  }`}
                  title="Copy Receipt Hash"
                >
                  <Copy className="h-3.5 w-3.5" />
                  <span>{copied ? "Copied" : "Copy"}</span>
                </button>
              </div>
            </div>

            {/* Informational Text Box */}
            <div className="bg-blue-50 border border-blue-200 text-blue-800 rounded-xl p-5 shadow-sm space-y-3">
              <div className="flex items-center gap-2 text-blue-900 font-bold text-xs uppercase tracking-wider">
                <ShieldCheck className="h-4.5 w-4.5 text-blue-600" />
                <span>Auditing Your Ballot</span>
              </div>
              <p className="text-xs text-blue-700 leading-relaxed">
                This long receipt hash represents the cryptographic digest of your vote package. During election counting, you can verify this hash exists in the public block records.
              </p>
              <p className="text-xs text-blue-700 leading-relaxed font-semibold">
                Because your real ID is not linked to the token key, publishing this receipt proves your vote was counted without revealing your identity. Your local session recorded the ballot choice as {selectedCandidate}.
              </p>
            </div>
          </div>

          {/* Right Column: Dynamic Audit Log Panel */}
          <div className="lg:col-span-7 bg-white rounded-xl border border-slate-200 p-6 shadow-sm space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3 flex-wrap gap-2">
              <div>
                <h3 className="font-bold text-slate-800 text-sm uppercase tracking-wider">Hyperledger Block Explorer</h3>
                <p className="text-xs text-slate-400 mt-0.5">Real-time mock stream of committed blocks in the network.</p>
              </div>
              <div className="flex items-center gap-1.5 text-xs text-slate-500 bg-slate-50 border border-slate-200 px-2.5 py-1 rounded-md font-semibold">
                <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
                <span>Streaming blocks</span>
              </div>
            </div>

            {/* Blocks Table */}
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b border-slate-200 text-slate-400 font-bold">
                    <th className="py-2.5 px-2">Block</th>
                    <th className="py-2.5 px-2">Time</th>
                    <th className="py-2.5 px-2">TX Hash Prefix</th>
                    <th className="py-2.5 px-2">Payload Digest</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 font-medium">
                  {blocks.map((block) => (
                    <tr 
                      key={block.height} 
                      className="hover:bg-slate-50/50 transition-colors animate-stream"
                    >
                      <td className="py-3 px-2 font-bold text-slate-700">#{block.height}</td>
                      <td className="py-3 px-2 text-slate-500 font-semibold">{block.timestamp}</td>
                      <td className="py-3 px-2"><code className="text-slate-600 bg-slate-100 px-1.5 py-0.5 rounded text-[10px] font-mono">{block.txHash}</code></td>
                      <td className="py-3 px-2"><code className="text-slate-400 text-[10px] font-mono">{block.payloadHash.substring(0, 16)}...</code></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>

        {/* Primary Footer Action */}
        <div className="border-t border-slate-200 pt-6 flex justify-center">
          <button
            onClick={onReset}
            className="bg-slate-900 hover:bg-slate-850 text-white font-bold py-3.5 px-8 rounded-xl transition-all duration-200 flex items-center justify-center gap-2 shadow-md shadow-slate-950/10 cursor-pointer text-xs uppercase tracking-wider"
          >
            <LogOut className="h-4 w-4" />
            <span>Securely Destroy Session & Logout</span>
          </button>
        </div>
      </main>

      <footer className="w-full text-center py-5 text-xs text-slate-400 mt-auto border-t border-slate-200 bg-white/50">
        <p>Your session cache has been encrypted locally and is completely wiped upon clicking logout.</p>
      </footer>
    </div>
  );
}
