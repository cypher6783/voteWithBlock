"use client";

import { useEffect, useState } from "react";

export default function AdminDashboardPage() {
  const [metrics, setMetrics] = useState<any>(null);

  useEffect(() => {
    const fetchMetrics = async () => {
      try {
        const res = await fetch(`${process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || 'http://localhost:8080'}/api/admin/dashboard`, {
            headers: { 'Authorization': 'Bearer ' + localStorage.getItem('adminToken') }
        });
        if (res.ok) {
          const data = await res.json();
          setMetrics(data);
        }
      } catch (err) {
        console.error("Failed to fetch admin metrics", err);
      }
    };
    fetchMetrics();
    const interval = setInterval(fetchMetrics, 10000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="p-8">
      <h1 className="text-3xl font-bold mb-6 text-white">Admin Operations Dashboard</h1>
      {!metrics ? (
        <div className="text-slate-400">Loading metrics...</div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          <div className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg">
            <h2 className="text-lg font-semibold mb-2 text-slate-200">Election Overview</h2>
            <div className="text-2xl text-white font-bold">{metrics.electionMetrics?.turnoutPercentage}% Turnout</div>
            <p className="text-slate-400 text-sm mt-1">{metrics.electionMetrics?.totalVotesCast} votes cast</p>
          </div>
          
          <div className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg">
            <h2 className="text-lg font-semibold mb-2 text-slate-200">Biometric Verification</h2>
            <div className="text-2xl text-emerald-400 font-bold">{metrics.biometricMetrics?.successfulVerifications} Success</div>
            <p className="text-red-400 text-sm mt-1">{metrics.biometricMetrics?.failedVerifications} Failures</p>
          </div>

          <div className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg">
            <h2 className="text-lg font-semibold mb-2 text-slate-200">Security Events</h2>
            <div className="text-2xl text-rose-500 font-bold">{metrics.securityMetrics?.suspiciousActivities} Alerts</div>
            <p className="text-slate-400 text-sm mt-1">{metrics.securityMetrics?.loginFailures} Login Failures</p>
          </div>

          <div className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg">
            <h2 className="text-lg font-semibold mb-2 text-slate-200">System Health</h2>
            <div className={`text-2xl font-bold ${metrics.operationalMetrics?.overallStatus === 'HEALTHY' ? 'text-emerald-400' : 'text-amber-500'}`}>
              {metrics.operationalMetrics?.overallStatus}
            </div>
            <p className="text-slate-400 text-sm mt-1">Gateway: {metrics.operationalMetrics?.gatewayHealth?.status}</p>
          </div>
        </div>
      )}
    </div>
  );
}
