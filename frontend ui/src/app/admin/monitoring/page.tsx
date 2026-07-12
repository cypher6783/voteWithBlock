"use client";

import { useEffect, useState } from "react";

export default function MonitoringDashboardPage() {
  const [metrics, setMetrics] = useState<any>(null);

  useEffect(() => {
    const fetchMetrics = async () => {
      try {
        const res = await fetch(`${process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || 'http://localhost:8080'}/api/admin/metrics/operational`, {
            headers: { 'Authorization': 'Bearer ' + localStorage.getItem('adminToken') }
        });
        if (res.ok) {
          const data = await res.json();
          setMetrics(data);
        }
      } catch (err) {
        console.error("Failed to fetch monitoring metrics", err);
      }
    };
    fetchMetrics();
    const interval = setInterval(fetchMetrics, 5000); // 5s interval for monitoring
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="p-8">
      <h1 className="text-3xl font-bold mb-6 text-white">System Monitoring</h1>
      {!metrics ? (
        <div className="text-slate-400">Loading metrics...</div>
      ) : (
        <div className="space-y-6">
            <div className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg">
                <h2 className="text-xl font-bold text-white mb-4">Overall System Status: 
                   <span className={`ml-2 ${metrics.overallStatus === 'HEALTHY' ? 'text-emerald-400' : 'text-amber-500'}`}>
                      {metrics.overallStatus}
                   </span>
                </h2>
                
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                    <StatusCard title="Core Gateway" health={metrics.gatewayHealth} />
                    <StatusCard title="AI Service" health={metrics.aiServiceHealth} />
                    <StatusCard title="PostgreSQL Database" health={metrics.databaseHealth} />
                    <StatusCard title="Hyperledger Fabric" health={metrics.fabricHealth} />
                </div>
            </div>

            <div className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg">
                <h2 className="text-xl font-bold text-white mb-4">Blockchain Metrics</h2>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                    <MetricCard title="Average Submission Latency" value={`${metrics.blockchainMetrics?.averageSubmissionLatencyMs} ms`} />
                    <MetricCard title="Average Query Latency" value={`${metrics.blockchainMetrics?.averageQueryLatencyMs} ms`} />
                    <MetricCard title="Last Successful TX" value={metrics.blockchainMetrics?.lastSuccessfulTransaction || 'N/A'} isText={true} />
                    <MetricCard title="Last Failed TX" value={metrics.blockchainMetrics?.lastFailedTransaction || 'N/A'} isText={true} />
                </div>
            </div>
        </div>
      )}
    </div>
  );
}

function StatusCard({ title, health }: { title: string, health: any }) {
    if (!health) return null;
    return (
        <div className="bg-slate-700/50 p-4 rounded-lg border border-slate-600/50">
            <h3 className="text-sm font-medium text-slate-400 mb-1">{title}</h3>
            <div className={`text-lg font-bold ${health.status === 'HEALTHY' ? 'text-emerald-400' : 'text-amber-500'}`}>
                {health.status}
            </div>
            <p className="text-xs text-slate-500 mt-1">{health.details}</p>
        </div>
    );
}

function MetricCard({ title, value, isText = false }: { title: string, value: string | number, isText?: boolean }) {
    return (
        <div className="bg-slate-700/50 p-4 rounded-lg border border-slate-600/50">
            <h3 className="text-sm font-medium text-slate-400 mb-1">{title}</h3>
            <div className={`${isText ? 'text-sm font-mono' : 'text-xl font-bold'} text-white truncate`}>
                {value}
            </div>
        </div>
    );
}
