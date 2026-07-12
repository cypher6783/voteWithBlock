"use client";

import { useEffect, useState } from "react";

export default function IncidentsDashboardPage() {
  const [incidents, setIncidents] = useState<any[]>([]);
  const [stats, setStats] = useState<any>(null);
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [severityFilter, setSeverityFilter] = useState<string>("");

  useEffect(() => {
    const fetchIncidents = async () => {
      const token = localStorage.getItem("adminToken");
      const headers = { Authorization: `Bearer ${token}` };
      const baseUrl = process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || "http://localhost:8080";

      let url = `${baseUrl}/api/admin/incidents?size=50`;
      if (statusFilter) url += `&status=${statusFilter}`;
      if (severityFilter) url += `&severity=${severityFilter}`;

      try {
        const [incidentsRes, statsRes] = await Promise.all([
          fetch(url, { headers }),
          fetch(`${baseUrl}/api/admin/operations/incidents/stats`, { headers }),
        ]);

        if (incidentsRes.ok) {
          const data = await incidentsRes.json();
          setIncidents(data.content || []);
        }
        if (statsRes.ok) setStats(await statsRes.json());
      } catch (err) {
        console.error("Failed to fetch incidents", err);
      }
    };

    fetchIncidents();
    const interval = setInterval(fetchIncidents, 30000);
    return () => clearInterval(interval);
  }, [statusFilter, severityFilter]);

  const handleStatusChange = async (incidentId: number, action: string) => {
    const token = localStorage.getItem("adminToken");
    const baseUrl = process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || "http://localhost:8080";

    try {
      await fetch(`${baseUrl}/api/admin/incidents/${incidentId}/${action}`, {
        method: "PUT",
        headers: { Authorization: `Bearer ${token}` },
      });
      setIncidents(incidents.map(i => 
        i.id === incidentId ? { ...i, status: action.toUpperCase() } : i
      ));
    } catch (err) {
      console.error(`Failed to ${action} incident`, err);
    }
  };

  return (
    <div className="p-8">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-3xl font-bold text-white">Incident Management</h1>
        <div className="flex gap-2">
          {["", "OPEN", "ACKNOWLEDGED", "INVESTIGATING", "RESOLVED"].map((status) => (
            <button
              key={status}
              onClick={() => setStatusFilter(status)}
              className={`px-3 py-1 rounded text-sm font-medium ${
                statusFilter === status ? "bg-blue-600 text-white" : "bg-slate-700 text-slate-300 hover:bg-slate-600"
              }`}
            >
              {status || "All"}
            </button>
          ))}
        </div>
      </div>

      {stats && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          <div className="bg-slate-800 p-4 rounded-lg border border-slate-700">
            <div className="text-sm text-slate-400">Open</div>
            <div className="text-xl font-bold text-amber-400">{stats.open || 0}</div>
          </div>
          <div className="bg-slate-800 p-4 rounded-lg border border-slate-700">
            <div className="text-sm text-slate-400">Acknowledged</div>
            <div className="text-xl font-bold text-blue-400">{stats.acknowledged || 0}</div>
          </div>
          <div className="bg-slate-800 p-4 rounded-lg border border-slate-700">
            <div className="text-sm text-slate-400">Investigating</div>
            <div className="text-xl font-bold text-purple-400">{stats.investigating || 0}</div>
          </div>
          <div className="bg-slate-800 p-4 rounded-lg border border-slate-700">
            <div className="text-sm text-slate-400">Resolved</div>
            <div className="text-xl font-bold text-emerald-400">{stats.resolved || 0}</div>
          </div>
        </div>
      )}

      <div className="bg-slate-800 rounded-xl border border-slate-700 overflow-hidden shadow-lg">
        <table className="w-full text-left text-sm text-slate-300">
          <thead className="bg-slate-700 text-slate-200">
            <tr>
              <th className="px-6 py-4 font-medium">Severity</th>
              <th className="px-6 py-4 font-medium">Status</th>
              <th className="px-6 py-4 font-medium">Title</th>
              <th className="px-6 py-4 font-medium">Assigned To</th>
              <th className="px-6 py-4 font-medium">Created</th>
              <th className="px-6 py-4 font-medium text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-700/50">
            {incidents.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-6 py-8 text-center text-slate-500">
                  No incidents found.
                </td>
              </tr>
            ) : (
              incidents.map((incident) => (
                <tr key={incident.id} className="hover:bg-slate-700/30">
                  <td className="px-6 py-4">
                    <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${
                      incident.severity === 'CRITICAL' ? 'bg-red-500/20 text-red-400 border border-red-500/20' :
                      incident.severity === 'HIGH' ? 'bg-orange-500/20 text-orange-400 border border-orange-500/20' :
                      incident.severity === 'MEDIUM' ? 'bg-amber-500/20 text-amber-400 border border-amber-500/20' :
                      'bg-blue-500/20 text-blue-400 border border-blue-500/20'
                    }`}>
                      {incident.severity}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${
                      incident.status === 'OPEN' ? 'bg-amber-500/20 text-amber-400 border border-amber-500/20' :
                      incident.status === 'ACKNOWLEDGED' ? 'bg-blue-500/20 text-blue-400 border border-blue-500/20' :
                      incident.status === 'INVESTIGATING' ? 'bg-purple-500/20 text-purple-400 border border-purple-500/20' :
                      'bg-emerald-500/20 text-emerald-400 border border-emerald-500/20'
                    }`}>
                      {incident.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 font-medium text-slate-200">{incident.title}</td>
                  <td className="px-6 py-4">{incident.assignedTo || "Unassigned"}</td>
                  <td className="px-6 py-4 text-slate-400">
                    {new Date(incident.createdAt).toLocaleDateString()}
                  </td>
                  <td className="px-6 py-4 text-right">
                    {incident.status === 'OPEN' && (
                      <button
                        onClick={() => handleStatusChange(incident.id, 'acknowledge')}
                        className="text-amber-400 hover:text-amber-300 font-medium mr-3"
                      >
                        Acknowledge
                      </button>
                    )}
                    {incident.status === 'ACKNOWLEDGED' && (
                      <button
                        onClick={() => handleStatusChange(incident.id, 'investigate')}
                        className="text-purple-400 hover:text-purple-300 font-medium mr-3"
                      >
                        Investigate
                      </button>
                    )}
                    {(incident.status === 'INVESTIGATING' || incident.status === 'ACKNOWLEDGED') && (
                      <button
                        onClick={() => handleStatusChange(incident.id, 'resolve')}
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
