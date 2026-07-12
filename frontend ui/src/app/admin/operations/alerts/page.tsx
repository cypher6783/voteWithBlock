"use client";

import { useEffect, useState } from "react";

export default function AlertManagementPage() {
  const [alerts, setAlerts] = useState<any[]>([]);
  const [stats, setStats] = useState<any>(null);
  const [statusFilter, setStatusFilter] = useState("ACTIVE");

  useEffect(() => {
    const fetchData = async () => {
      const token = localStorage.getItem("adminToken");
      const headers = { Authorization: `Bearer ${token}` };
      const baseUrl = process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || "http://localhost:8080";

      try {
        const [alertsRes, statsRes] = await Promise.all([
          fetch(`${baseUrl}/api/admin/operations/alerts?status=${statusFilter}&size=50`, { headers }),
          fetch(`${baseUrl}/api/admin/operations/alerts/stats`, { headers }),
        ]);

        if (alertsRes.ok) {
          const data = await alertsRes.json();
          setAlerts(data.content || []);
        }
        if (statsRes.ok) setStats(await statsRes.json());
      } catch (err) {
        console.error("Failed to fetch alerts", err);
      }
    };

    fetchData();
    const interval = setInterval(fetchData, 10000);
    return () => clearInterval(interval);
  }, [statusFilter]);

  const handleAcknowledge = async (id: number) => {
    const token = localStorage.getItem("adminToken");
    const baseUrl = process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || "http://localhost:8080";

    try {
      await fetch(`${baseUrl}/api/admin/operations/alerts/${id}/acknowledge`, {
        method: "PUT",
        headers: { Authorization: `Bearer ${token}` },
      });
      setAlerts(alerts.map(a => a.id === id ? { ...a, status: "ACKNOWLEDGED" } : a));
    } catch (err) {
      console.error("Failed to acknowledge alert", err);
    }
  };

  const handleResolve = async (id: number) => {
    const token = localStorage.getItem("adminToken");
    const baseUrl = process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || "http://localhost:8080";

    try {
      await fetch(`${baseUrl}/api/admin/operations/alerts/${id}/resolve`, {
        method: "PUT",
        headers: { Authorization: `Bearer ${token}` },
      });
      setAlerts(alerts.map(a => a.id === id ? { ...a, status: "RESOLVED" } : a));
    } catch (err) {
      console.error("Failed to resolve alert", err);
    }
  };

  return (
    <div className="p-8">
      <h1 className="text-3xl font-bold mb-6 text-white">Alert Management</h1>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        <div className="bg-rose-500/10 p-4 rounded-lg border border-rose-500/20">
          <div className="text-sm font-medium text-rose-400">Active</div>
          <div className="text-3xl font-bold text-white mt-1">{stats?.active || 0}</div>
        </div>
        <div className="bg-amber-500/10 p-4 rounded-lg border border-amber-500/20">
          <div className="text-sm font-medium text-amber-400">Acknowledged</div>
          <div className="text-3xl font-bold text-white mt-1">{stats?.acknowledged || 0}</div>
        </div>
        <div className="bg-emerald-500/10 p-4 rounded-lg border border-emerald-500/20">
          <div className="text-sm font-medium text-emerald-400">Resolved</div>
          <div className="text-3xl font-bold text-white mt-1">{stats?.resolved || 0}</div>
        </div>
        <div className="bg-purple-500/10 p-4 rounded-lg border border-purple-500/20">
          <div className="text-sm font-medium text-purple-400">Critical</div>
          <div className="text-3xl font-bold text-white mt-1">{stats?.critical || 0}</div>
        </div>
      </div>

      <div className="mb-4 flex gap-2">
        {["ACTIVE", "ACKNOWLEDGED", "RESOLVED"].map((status) => (
          <button
            key={status}
            onClick={() => setStatusFilter(status)}
            className={`px-4 py-2 rounded-md text-sm font-medium ${
              statusFilter === status ? "bg-blue-600 text-white" : "bg-slate-700 text-slate-300 hover:bg-slate-600"
            }`}
          >
            {status}
          </button>
        ))}
      </div>

      <div className="bg-slate-800 rounded-xl border border-slate-700 overflow-hidden shadow-lg">
        <table className="w-full text-left text-sm text-slate-300">
          <thead className="bg-slate-700 text-slate-200">
            <tr>
              <th className="px-6 py-4 font-medium">Created</th>
              <th className="px-6 py-4 font-medium">Severity</th>
              <th className="px-6 py-4 font-medium">Source</th>
              <th className="px-6 py-4 font-medium">Message</th>
              <th className="px-6 py-4 font-medium">Status</th>
              <th className="px-6 py-4 font-medium text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-700/50">
            {alerts.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-6 py-8 text-center text-slate-500">
                  No alerts found.
                </td>
              </tr>
            ) : (
              alerts.map((alert) => (
                <tr key={alert.id} className="hover:bg-slate-700/30">
                  <td className="px-6 py-4 text-slate-400">
                    {new Date(alert.createdAt).toLocaleString()}
                  </td>
                  <td className="px-6 py-4">
                    <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${
                      alert.severity === 'CRITICAL' ? 'bg-red-500/20 text-red-400 border border-red-500/20' :
                      alert.severity === 'HIGH' ? 'bg-orange-500/20 text-orange-400 border border-orange-500/20' :
                      alert.severity === 'MEDIUM' ? 'bg-amber-500/20 text-amber-400 border border-amber-500/20' :
                      'bg-blue-500/20 text-blue-400 border border-blue-500/20'
                    }`}>
                      {alert.severity}
                    </span>
                  </td>
                  <td className="px-6 py-4 font-medium text-slate-200">{alert.source}</td>
                  <td className="px-6 py-4 text-slate-400 max-w-xs truncate">{alert.message}</td>
                  <td className="px-6 py-4">
                    <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${
                      alert.status === 'ACTIVE' ? 'bg-rose-500/20 text-rose-400 border border-rose-500/20' :
                      alert.status === 'ACKNOWLEDGED' ? 'bg-amber-500/20 text-amber-400 border border-amber-500/20' :
                      'bg-emerald-500/20 text-emerald-400 border border-emerald-500/20'
                    }`}>
                      {alert.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right">
                    {alert.status === 'ACTIVE' && (
                      <button
                        onClick={() => handleAcknowledge(alert.id)}
                        className="text-amber-400 hover:text-amber-300 font-medium mr-3"
                      >
                        Acknowledge
                      </button>
                    )}
                    {(alert.status === 'ACTIVE' || alert.status === 'ACKNOWLEDGED') && (
                      <button
                        onClick={() => handleResolve(alert.id)}
                        className="text-emerald-400 hover:text-emerald-300 font-medium"
                      >
                        Resolve
                      </button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
