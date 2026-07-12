"use client";

import { useEffect, useState } from "react";
import Link from "next/link";

export default function OperationsDashboardPage() {
  const [health, setHealth] = useState<any>(null);
  const [alertStats, setAlertStats] = useState<any>(null);
  const [incidentStats, setIncidentStats] = useState<any>(null);
  const [securityStats, setSecurityStats] = useState<any>(null);

  useEffect(() => {
    const fetchData = async () => {
      const token = localStorage.getItem("adminToken");
      const headers = { Authorization: `Bearer ${token}` };
      const baseUrl = process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || "http://localhost:8080";

      try {
        const [healthRes, alertRes, incidentRes, securityRes] = await Promise.all([
          fetch(`${baseUrl}/api/admin/operations/health`, { headers }),
          fetch(`${baseUrl}/api/admin/operations/alerts/stats`, { headers }),
          fetch(`${baseUrl}/api/admin/operations/incidents/stats`, { headers }),
          fetch(`${baseUrl}/api/admin/operations/security/events/stats`, { headers }),
        ]);

        if (healthRes.ok) setHealth(await healthRes.json());
        if (alertRes.ok) setAlertStats(await alertRes.json());
        if (incidentRes.ok) setIncidentStats(await incidentRes.json());
        if (securityRes.ok) setSecurityStats(await securityRes.json());
      } catch (err) {
        console.error("Failed to fetch operations data", err);
      }
    };

    fetchData();
    const interval = setInterval(fetchData, 10000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="p-8">
      <h1 className="text-3xl font-bold mb-6 text-white">Operations Center</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <Link href="/admin/operations/incidents" className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg hover:border-slate-600 transition-colors">
          <h2 className="text-lg font-semibold mb-2 text-slate-200">Incidents</h2>
          <div className="text-2xl text-amber-400 font-bold">{incidentStats?.open || 0} Open</div>
          <p className="text-slate-400 text-sm mt-1">{incidentStats?.critical || 0} Critical</p>
        </Link>

        <Link href="/admin/operations/alerts" className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg hover:border-slate-600 transition-colors">
          <h2 className="text-lg font-semibold mb-2 text-slate-200">Alerts</h2>
          <div className="text-2xl text-rose-500 font-bold">{alertStats?.active || 0} Active</div>
          <p className="text-slate-400 text-sm mt-1">{alertStats?.critical || 0} Critical</p>
        </Link>

        <Link href="/admin/operations/security" className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg hover:border-slate-600 transition-colors">
          <h2 className="text-lg font-semibold mb-2 text-slate-200">Security</h2>
          <div className="text-2xl text-orange-400 font-bold">{securityStats?.total24h || 0} Events (24h)</div>
          <p className="text-slate-400 text-sm mt-1">{securityStats?.failedLogin || 0} Failed Logins</p>
        </Link>

        <Link href="/admin/operations/health" className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg hover:border-slate-600 transition-colors">
          <h2 className="text-lg font-semibold mb-2 text-slate-200">System Health</h2>
          <div className={`text-2xl font-bold ${health?.overallStatus === 'HEALTHY' ? 'text-emerald-400' : health?.overallStatus === 'DEGRADED' ? 'text-amber-400' : 'text-rose-500'}`}>
            {health?.overallStatus || 'Loading...'}
          </div>
          <p className="text-slate-400 text-sm mt-1">All Services</p>
        </Link>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg">
          <h2 className="text-xl font-bold text-white mb-4">Incident Summary</h2>
          <div className="grid grid-cols-2 gap-4">
            <div className="bg-slate-700/50 p-4 rounded-lg">
              <div className="text-sm text-slate-400">Open</div>
              <div className="text-xl font-bold text-amber-400">{incidentStats?.open || 0}</div>
            </div>
            <div className="bg-slate-700/50 p-4 rounded-lg">
              <div className="text-sm text-slate-400">Acknowledged</div>
              <div className="text-xl font-bold text-blue-400">{incidentStats?.acknowledged || 0}</div>
            </div>
            <div className="bg-slate-700/50 p-4 rounded-lg">
              <div className="text-sm text-slate-400">Investigating</div>
              <div className="text-xl font-bold text-purple-400">{incidentStats?.investigating || 0}</div>
            </div>
            <div className="bg-slate-700/50 p-4 rounded-lg">
              <div className="text-sm text-slate-400">Resolved</div>
              <div className="text-xl font-bold text-emerald-400">{incidentStats?.resolved || 0}</div>
            </div>
          </div>
        </div>

        <div className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg">
          <h2 className="text-xl font-bold text-white mb-4">Security Overview</h2>
          <div className="grid grid-cols-2 gap-4">
            <div className="bg-slate-700/50 p-4 rounded-lg">
              <div className="text-sm text-slate-400">Failed Logins</div>
              <div className="text-xl font-bold text-rose-400">{securityStats?.failedLogin || 0}</div>
            </div>
            <div className="bg-slate-700/50 p-4 rounded-lg">
              <div className="text-sm text-slate-400">Rate Limits</div>
              <div className="text-xl font-bold text-amber-400">{securityStats?.rateLimit || 0}</div>
            </div>
            <div className="bg-slate-700/50 p-4 rounded-lg">
              <div className="text-sm text-slate-400">Lockouts</div>
              <div className="text-xl font-bold text-orange-400">{securityStats?.accountLockout || 0}</div>
            </div>
            <div className="bg-slate-700/50 p-4 rounded-lg">
              <div className="text-sm text-slate-400">Suspicious</div>
              <div className="text-xl font-bold text-purple-400">{securityStats?.suspiciousActivity || 0}</div>
            </div>
          </div>
        </div>
      </div>

      <div className="mt-6 grid grid-cols-1 md:grid-cols-3 gap-6">
        <Link href="/admin/operations/audit" className="bg-slate-800 p-4 rounded-xl border border-slate-700 shadow-lg hover:border-slate-600 transition-colors text-center">
          <span className="text-slate-200 font-medium">Audit Log</span>
        </Link>
        <Link href="/admin/operations/elections" className="bg-slate-800 p-4 rounded-xl border border-slate-700 shadow-lg hover:border-slate-600 transition-colors text-center">
          <span className="text-slate-200 font-medium">Election Operations</span>
        </Link>
        <Link href="/admin/monitoring" className="bg-slate-800 p-4 rounded-xl border border-slate-700 shadow-lg hover:border-slate-600 transition-colors text-center">
          <span className="text-slate-200 font-medium">System Monitoring</span>
        </Link>
      </div>
    </div>
  );
}
