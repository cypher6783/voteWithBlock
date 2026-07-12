"use client";

import { useEffect, useState } from "react";

export default function ServiceHealthPage() {
  const [health, setHealth] = useState<any>(null);

  useEffect(() => {
    const fetchData = async () => {
      const token = localStorage.getItem("adminToken");
      const headers = { Authorization: `Bearer ${token}` };
      const baseUrl = process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || "http://localhost:8080";

      try {
        const res = await fetch(`${baseUrl}/api/admin/operations/health`, { headers });
        if (res.ok) setHealth(await res.json());
      } catch (err) {
        console.error("Failed to fetch health data", err);
      }
    };

    fetchData();
    const interval = setInterval(fetchData, 10000);
    return () => clearInterval(interval);
  }, []);

  const getStatusColor = (status: string) => {
    switch (status) {
      case "HEALTHY": return "text-emerald-400 bg-emerald-500/10 border-emerald-500/20";
      case "DEGRADED": return "text-amber-400 bg-amber-500/10 border-amber-500/20";
      case "CRITICAL": return "text-rose-400 bg-rose-500/10 border-rose-500/20";
      default: return "text-slate-400 bg-slate-500/10 border-slate-500/20";
    }
  };

  return (
    <div className="p-8">
      <h1 className="text-3xl font-bold mb-6 text-white">Service Health Center</h1>

      <div className="mb-8 bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-bold text-white">Overall System Status</h2>
          <span className={`px-4 py-2 rounded-full text-sm font-bold border ${getStatusColor(health?.overallStatus || "UNKNOWN")}`}>
            {health?.overallStatus || "Loading..."}
          </span>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
        {[
          { title: "Core Gateway", health: health?.gatewayHealth, icon: "Gateway" },
          { title: "AI Service", health: health?.aiServiceHealth, icon: "AI" },
          { title: "PostgreSQL Database", health: health?.databaseHealth, icon: "DB" },
          { title: "Hyperledger Fabric", health: health?.fabricHealth, icon: "Fabric" },
        ].map((service) => (
          <div key={service.title} className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-slate-200">{service.title}</h3>
              <span className={`px-3 py-1 rounded-full text-xs font-bold border ${getStatusColor(service.health?.status || "UNKNOWN")}`}>
                {service.health?.status || "Unknown"}
              </span>
            </div>
            <p className="text-slate-400 text-sm">{service.health?.details || "No details available"}</p>
          </div>
        ))}
      </div>

      <div className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg">
        <h2 className="text-xl font-bold text-white mb-4">Blockchain Metrics</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="bg-slate-700/50 p-4 rounded-lg">
            <div className="text-sm text-slate-400">Avg Submission Latency</div>
            <div className="text-xl font-bold text-white">{health?.blockchainMetrics?.averageSubmissionLatencyMs?.toFixed(1) || "N/A"} ms</div>
          </div>
          <div className="bg-slate-700/50 p-4 rounded-lg">
            <div className="text-sm text-slate-400">Avg Query Latency</div>
            <div className="text-xl font-bold text-white">{health?.blockchainMetrics?.averageQueryLatencyMs?.toFixed(1) || "N/A"} ms</div>
          </div>
          <div className="bg-slate-700/50 p-4 rounded-lg">
            <div className="text-sm text-slate-400">Last Successful TX</div>
            <div className="text-xl font-bold text-emerald-400 font-mono text-sm truncate">{health?.blockchainMetrics?.lastSuccessfulTransaction || "N/A"}</div>
          </div>
          <div className="bg-slate-700/50 p-4 rounded-lg">
            <div className="text-sm text-slate-400">Last Failed TX</div>
            <div className="text-xl font-bold text-rose-400 font-mono text-sm truncate">{health?.blockchainMetrics?.lastFailedTransaction || "N/A"}</div>
          </div>
        </div>
      </div>
    </div>
  );
}
