"use client";

import { useEffect, useState } from "react";

export default function SecurityOperationsPage() {
  const [events, setEvents] = useState<any[]>([]);
  const [stats, setStats] = useState<any>(null);
  const [filter, setFilter] = useState<string>("");

  useEffect(() => {
    const fetchData = async () => {
      const token = localStorage.getItem("adminToken");
      const headers = { Authorization: `Bearer ${token}` };
      const baseUrl = process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || "http://localhost:8080";

      try {
        const [eventsRes, statsRes] = await Promise.all([
          fetch(`${baseUrl}/api/admin/operations/security/events?size=50${filter ? `&eventType=${filter}` : ""}`, { headers }),
          fetch(`${baseUrl}/api/admin/operations/security/events/stats`, { headers }),
        ]);

        if (eventsRes.ok) {
          const data = await eventsRes.json();
          setEvents(data.content || []);
        }
        if (statsRes.ok) setStats(await statsRes.json());
      } catch (err) {
        console.error("Failed to fetch security data", err);
      }
    };

    fetchData();
    const interval = setInterval(fetchData, 5000);
    return () => clearInterval(interval);
  }, [filter]);

  return (
    <div className="p-8">
      <h1 className="text-3xl font-bold mb-6 text-white">Security Operations</h1>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        <div className="bg-rose-500/10 p-4 rounded-lg border border-rose-500/20">
          <div className="text-sm font-medium text-rose-400">Failed Logins (24h)</div>
          <div className="text-3xl font-bold text-white mt-1">{stats?.failedLogin || 0}</div>
        </div>
        <div className="bg-amber-500/10 p-4 rounded-lg border border-amber-500/20">
          <div className="text-sm font-medium text-amber-400">Rate Limits (24h)</div>
          <div className="text-3xl font-bold text-white mt-1">{stats?.rateLimit || 0}</div>
        </div>
        <div className="bg-orange-500/10 p-4 rounded-lg border border-orange-500/20">
          <div className="text-sm font-medium text-orange-400">Account Lockouts</div>
          <div className="text-3xl font-bold text-white mt-1">{stats?.accountLockout || 0}</div>
        </div>
        <div className="bg-purple-500/10 p-4 rounded-lg border border-purple-500/20">
          <div className="text-sm font-medium text-purple-400">Suspicious Activity</div>
          <div className="text-3xl font-bold text-white mt-1">{stats?.suspiciousActivity || 0}</div>
        </div>
      </div>

      <div className="mb-4 flex gap-2 flex-wrap">
        {["", "FAILED_LOGIN", "FAILED_ADMIN_LOGIN", "RATE_LIMIT", "ACCOUNT_LOCKOUT", "BIOMETRIC_FAILURE", "SUSPICIOUS_ACTIVITY"].map((type) => (
          <button
            key={type}
            onClick={() => setFilter(type)}
            className={`px-3 py-1 rounded text-sm font-medium ${
              filter === type ? "bg-blue-600 text-white" : "bg-slate-700 text-slate-300 hover:bg-slate-600"
            }`}
          >
            {type || "All"}
          </button>
        ))}
      </div>

      <div className="bg-slate-800 rounded-xl border border-slate-700 overflow-hidden shadow-lg">
        <table className="w-full text-left text-sm text-slate-300">
          <thead className="bg-slate-700 text-slate-200">
            <tr>
              <th className="px-6 py-4 font-medium">Time</th>
              <th className="px-6 py-4 font-medium">Event Type</th>
              <th className="px-6 py-4 font-medium">Severity</th>
              <th className="px-6 py-4 font-medium">Source</th>
              <th className="px-6 py-4 font-medium">Details</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-700/50">
            {events.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-6 py-8 text-center text-slate-500">
                  No security events found.
                </td>
              </tr>
            ) : (
              events.map((event) => (
                <tr key={event.id} className="hover:bg-slate-700/30">
                  <td className="px-6 py-4 text-slate-400">
                    {new Date(event.occurredAt).toLocaleString()}
                  </td>
                  <td className="px-6 py-4 font-medium text-slate-200">{event.eventType}</td>
                  <td className="px-6 py-4">
                    <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${
                      event.severity === 'CRITICAL' ? 'bg-red-500/20 text-red-400 border border-red-500/20' :
                      event.severity === 'HIGH' ? 'bg-orange-500/20 text-orange-400 border border-orange-500/20' :
                      event.severity === 'MEDIUM' ? 'bg-amber-500/20 text-amber-400 border border-amber-500/20' :
                      'bg-blue-500/20 text-blue-400 border border-blue-500/20'
                    }`}>
                      {event.severity}
                    </span>
                  </td>
                  <td className="px-6 py-4">{event.source}</td>
                  <td className="px-6 py-4 text-slate-400 text-xs max-w-xs truncate">{event.details}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
