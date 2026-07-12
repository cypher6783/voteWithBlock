"use client";

import { useEffect, useState } from "react";
import Link from "next/link";

interface PublicDashboardDto {
  electionTitle: string;
  electionCode: string;
  status: string;
  turnoutPercentage: number;
  totalVotesCast: number;
  registeredVoters: number;
  countdownSeconds: number | null;
}

export default function PublicDashboardPage() {
  const [metrics, setMetrics] = useState<PublicDashboardDto | null>(null);
  const [error, setError] = useState<string>("");

  useEffect(() => {
    const fetchMetrics = async () => {
      try {
        const res = await fetch(`${process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || 'http://localhost:8080'}/api/public/dashboard`);
        if (res.status === 204) {
          setError("No active election currently.");
          return;
        }
        if (!res.ok) throw new Error("Failed to fetch dashboard metrics");
        const data = await res.json();
        setMetrics(data);
        setError("");
      } catch (err: any) {
        setError(err.message);
      }
    };

    fetchMetrics();
    // Poll every 30 seconds
    const interval = setInterval(fetchMetrics, 30000);
    return () => clearInterval(interval);
  }, []);

  if (error) {
    return (
      <div className="min-h-screen bg-slate-900 flex items-center justify-center p-4">
        <div className="bg-red-500/10 text-red-400 p-6 rounded-xl border border-red-500/20 max-w-md w-full text-center">
          <p>{error}</p>
          <Link href="/" className="mt-4 inline-block text-sm hover:text-red-300 underline">Return Home</Link>
        </div>
      </div>
    );
  }

  if (!metrics) {
    return (
      <div className="min-h-screen bg-slate-900 flex items-center justify-center">
        <div className="animate-pulse flex space-x-4">
          <div className="rounded-full bg-slate-700 h-10 w-10"></div>
          <div className="flex-1 space-y-6 py-1">
            <div className="h-2 bg-slate-700 rounded w-24"></div>
            <div className="space-y-3">
              <div className="grid grid-cols-3 gap-4">
                <div className="h-2 bg-slate-700 rounded col-span-2"></div>
                <div className="h-2 bg-slate-700 rounded col-span-1"></div>
              </div>
              <div className="h-2 bg-slate-700 rounded"></div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-900 text-slate-200 p-8">
      <div className="max-w-5xl mx-auto space-y-8">
        <header className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold text-white">{metrics.electionTitle}</h1>
            <p className="text-slate-400 mt-1">Live Election Dashboard</p>
          </div>
          <div className="text-right">
            <div className="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              <span className="w-2 h-2 rounded-full bg-emerald-400 mr-2 animate-pulse"></span>
              {metrics.status}
            </div>
          </div>
        </header>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 shadow-xl">
            <h3 className="text-sm font-medium text-slate-400 mb-2">Turnout Percentage</h3>
            <div className="text-4xl font-bold text-white">{metrics.turnoutPercentage}%</div>
            <div className="w-full bg-slate-700 rounded-full h-2 mt-4">
              <div className="bg-blue-500 h-2 rounded-full" style={{ width: `${metrics.turnoutPercentage}%` }}></div>
            </div>
          </div>

          <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 shadow-xl">
            <h3 className="text-sm font-medium text-slate-400 mb-2">Total Votes Cast</h3>
            <div className="text-4xl font-bold text-white">{metrics.totalVotesCast.toLocaleString()}</div>
            <div className="text-sm text-slate-400 mt-2">
              out of {metrics.registeredVoters.toLocaleString()} registered
            </div>
          </div>

          <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 shadow-xl">
            <h3 className="text-sm font-medium text-slate-400 mb-2">Time Remaining</h3>
            <div className="text-4xl font-bold text-white">
              {metrics.countdownSeconds !== null 
                ? `${Math.floor(metrics.countdownSeconds / 3600)}h ${Math.floor((metrics.countdownSeconds % 3600) / 60)}m`
                : '--'}
            </div>
            <div className="text-sm text-slate-400 mt-2">
              {metrics.status === 'SCHEDULED' ? 'until polls open' : 'until polls close'}
            </div>
          </div>
        </div>

        <div className="flex justify-center pt-8">
           <Link href="/" className="px-6 py-3 bg-blue-600 hover:bg-blue-500 text-white rounded-lg font-medium transition-colors">
              Cast Your Vote
           </Link>
        </div>
      </div>
    </div>
  );
}
