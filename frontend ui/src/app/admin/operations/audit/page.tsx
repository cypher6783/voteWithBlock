"use client";

import { useEffect, useState } from "react";

export default function AuditAnalyticsPage() {
  const [logs, setLogs] = useState<any[]>([]);
  const [stats, setStats] = useState<any>(null);
  const [actionFilter, setActionFilter] = useState("");
  const [outcomeFilter, setOutcomeFilter] = useState("");
  const [page, setPage] = useState(0);

  useEffect(() => {
    const fetchData = async () => {
      const token = localStorage.getItem("adminToken");
      const headers = { Authorization: `Bearer ${token}` };
      const baseUrl = process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || "http://localhost:8080";

      let url = `${baseUrl}/api/admin/operations/audit/logs?page=${page}&size=20`;
      if (actionFilter) url += `&action=${actionFilter}`;
      if (outcomeFilter) url += `&outcome=${outcomeFilter}`;

      try {
        const [logsRes, statsRes] = await Promise.all([
          fetch(url, { headers }),
          fetch(`${baseUrl}/api/admin/operations/audit/stats`, { headers }),
        ]);

        if (logsRes.ok) {
          const data = await logsRes.json();
          setLogs(data.content || []);
        }
        if (statsRes.ok) setStats(await statsRes.json());
      } catch (err) {
        console.error("Failed to fetch audit data", err);
      }
    };

    fetchData();
  }, [page, actionFilter, outcomeFilter]);

  const handleExport = async () => {
    const token = localStorage.getItem("adminToken");
    const baseUrl = process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || "http://localhost:8080";

    try {
      const res = await fetch(`${baseUrl}/api/admin/operations/audit/export`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        const exportData = await res.json();
        alert(`Export generated. Records: ${exportData.recordCount}, SHA-256: ${exportData.sha256Hash.substring(0, 16)}...`);
      }
    } catch (err) {
      console.error("Failed to export audit logs", err);
    }
  };

  return (
    <div className="p-8">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-3xl font-bold text-white">Audit Analytics</h1>
        <button
          onClick={handleExport}
          className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-md text-sm font-medium"
        >
          Export Audit Log
        </button>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        <div className="bg-slate-800 p-4 rounded-lg border border-slate-700">
          <div className="text-sm text-slate-400">Total Events (24h)</div>
          <div className="text-2xl font-bold text-white">{stats?.total24h || 0}</div>
        </div>
        <div className="bg-slate-800 p-4 rounded-lg border border-slate-700">
          <div className="text-sm text-slate-400">Total Events (7d)</div>
          <div className="text-2xl font-bold text-white">{stats?.total7d || 0}</div>
        </div>
        <div className="bg-emerald-500/10 p-4 rounded-lg border border-emerald-500/20">
          <div className="text-sm text-emerald-400">Success</div>
          <div className="text-2xl font-bold text-white">{stats?.successCount || 0}</div>
        </div>
        <div className="bg-rose-500/10 p-4 rounded-lg border border-rose-500/20">
          <div className="text-sm text-rose-400">Failures</div>
          <div className="text-2xl font-bold text-white">{stats?.failCount || 0}</div>
        </div>
      </div>

      <div className="mb-4 flex gap-4">
        <input
          type="text"
          placeholder="Filter by action..."
          value={actionFilter}
          onChange={(e) => setActionFilter(e.target.value)}
          className="px-3 py-2 bg-slate-700 border border-slate-600 rounded-md text-white text-sm"
        />
        <select
          value={outcomeFilter}
          onChange={(e) => setOutcomeFilter(e.target.value)}
          className="px-3 py-2 bg-slate-700 border border-slate-600 rounded-md text-white text-sm"
        >
          <option value="">All Outcomes</option>
          <option value="SUCCESS">SUCCESS</option>
          <option value="FAIL">FAIL</option>
        </select>
      </div>

      <div className="bg-slate-800 rounded-xl border border-slate-700 overflow-hidden shadow-lg">
        <table className="w-full text-left text-sm text-slate-300">
          <thead className="bg-slate-700 text-slate-200">
            <tr>
              <th className="px-6 py-4 font-medium">Time</th>
              <th className="px-6 py-4 font-medium">Action</th>
              <th className="px-6 py-4 font-medium">Target</th>
              <th className="px-6 py-4 font-medium">Outcome</th>
              <th className="px-6 py-4 font-medium">Admin Hash</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-700/50">
            {logs.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-6 py-8 text-center text-slate-500">
                  No audit logs found.
                </td>
              </tr>
            ) : (
              logs.map((log) => (
                <tr key={log.id} className="hover:bg-slate-700/30">
                  <td className="px-6 py-4 text-slate-400">
                    {new Date(log.performedAt).toLocaleString()}
                  </td>
                  <td className="px-6 py-4 font-medium text-slate-200">{log.action}</td>
                  <td className="px-6 py-4">{log.targetEntityType}</td>
                  <td className="px-6 py-4">
                    <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${
                      log.outcome === 'SUCCESS' 
                        ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/20' 
                        : 'bg-rose-500/20 text-rose-400 border border-rose-500/20'
                    }`}>
                      {log.outcome}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-xs font-mono text-slate-500">{log.adminUsernameHash?.substring(0, 16)}...</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="mt-4 flex justify-between items-center">
        <button
          onClick={() => setPage(Math.max(0, page - 1))}
          disabled={page === 0}
          className="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white rounded-md text-sm disabled:opacity-50"
        >
          Previous
        </button>
        <span className="text-slate-400 text-sm">Page {page + 1}</span>
        <button
          onClick={() => setPage(page + 1)}
          className="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white rounded-md text-sm"
        >
          Next
        </button>
      </div>
    </div>
  );
}
