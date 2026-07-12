"use client";

import { useEffect, useState } from "react";

export default function SecurityDashboardPage() {
  const [metrics, setMetrics] = useState<any>(null);

  useEffect(() => {
    const fetchMetrics = async () => {
      try {
        const res = await fetch(`${process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || 'http://localhost:8080'}/api/admin/dashboard`, {
            headers: { 'Authorization': 'Bearer ' + localStorage.getItem('adminToken') }
        });
        if (res.ok) {
          const data = await res.json();
          setMetrics(data.securityMetrics);
        }
      } catch (err) {
        console.error("Failed to fetch security metrics", err);
      }
    };
    fetchMetrics();
    const interval = setInterval(fetchMetrics, 10000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="p-8">
      <h1 className="text-3xl font-bold mb-6 text-white">Security Monitoring</h1>
      {!metrics ? (
        <div className="text-slate-400">Loading metrics...</div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg col-span-2">
                <h2 className="text-xl font-bold text-white mb-4">Threat Detection Summary</h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
                    <div className="bg-rose-500/10 p-4 rounded-lg border border-rose-500/20">
                        <div className="text-sm font-medium text-rose-400">Suspicious Activities</div>
                        <div className="text-3xl font-bold text-white mt-1">{metrics.suspiciousActivities}</div>
                    </div>
                    <div className="bg-amber-500/10 p-4 rounded-lg border border-amber-500/20">
                        <div className="text-sm font-medium text-amber-400">Rate Limit Events</div>
                        <div className="text-3xl font-bold text-white mt-1">{metrics.rateLimitEvents}</div>
                    </div>
                    <div className="bg-slate-700/50 p-4 rounded-lg border border-slate-600/50">
                        <div className="text-sm font-medium text-slate-400">Login Failures</div>
                        <div className="text-3xl font-bold text-white mt-1">{metrics.loginFailures}</div>
                    </div>
                    <div className="bg-slate-700/50 p-4 rounded-lg border border-slate-600/50">
                        <div className="text-sm font-medium text-slate-400">Admin Login Failures</div>
                        <div className="text-3xl font-bold text-white mt-1">{metrics.adminLoginFailures}</div>
                    </div>
                </div>
            </div>
        </div>
      )}
    </div>
  );
}
