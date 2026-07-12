"use client";

import { useEffect, useState } from "react";

export default function ElectionOperationsPage() {
  const [elections, setElections] = useState<any[]>([]);
  const [selectedElection, setSelectedElection] = useState<any>(null);
  const [electionStatus, setElectionStatus] = useState<any>(null);
  const [electionHealth, setElectionHealth] = useState<any>(null);
  const [warnings, setWarnings] = useState<any[]>([]);

  useEffect(() => {
    const fetchElections = async () => {
      const token = localStorage.getItem("adminToken");
      const headers = { Authorization: `Bearer ${token}` };
      const baseUrl = process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || "http://localhost:8080";

      try {
        const res = await fetch(`${baseUrl}/api/admin/elections`, { headers });
        if (res.ok) setElections(await res.json());
      } catch (err) {
        console.error("Failed to fetch elections", err);
      }
    };

    fetchElections();
  }, []);

  useEffect(() => {
    if (!selectedElection) return;

    const fetchElectionData = async () => {
      const token = localStorage.getItem("adminToken");
      const headers = { Authorization: `Bearer ${token}` };
      const baseUrl = process.env.NEXT_PUBLIC_CORE_GATEWAY_URL || "http://localhost:8080";

      try {
        const [statusRes, healthRes, warningsRes] = await Promise.all([
          fetch(`${baseUrl}/api/admin/operations/elections/${selectedElection.id}/status`, { headers }),
          fetch(`${baseUrl}/api/admin/operations/elections/${selectedElection.id}/health`, { headers }),
          fetch(`${baseUrl}/api/admin/operations/elections/${selectedElection.id}/warnings`, { headers }),
        ]);

        if (statusRes.ok) setElectionStatus(await statusRes.json());
        if (healthRes.ok) setElectionHealth(await healthRes.json());
        if (warningsRes.ok) setWarnings(await warningsRes.json());
      } catch (err) {
        console.error("Failed to fetch election operations data", err);
      }
    };

    fetchElectionData();
    const interval = setInterval(fetchElectionData, 10000);
    return () => clearInterval(interval);
  }, [selectedElection]);

  const getHealthColor = (status: string) => {
    switch (status) {
      case "HEALTHY": return "text-emerald-400";
      case "DEGRADED": return "text-amber-400";
      case "CRITICAL": return "text-rose-400";
      default: return "text-slate-400";
    }
  };

  return (
    <div className="p-8">
      <h1 className="text-3xl font-bold mb-6 text-white">Election Operations</h1>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg">
          <h2 className="text-xl font-bold text-white mb-4">Elections</h2>
          <div className="space-y-2">
            {elections.map((election) => (
              <button
                key={election.id}
                onClick={() => setSelectedElection(election)}
                className={`w-full text-left p-3 rounded-lg transition-colors ${
                  selectedElection?.id === election.id
                    ? "bg-blue-600/20 border border-blue-500/30"
                    : "bg-slate-700/50 hover:bg-slate-700"
                }`}
              >
                <div className="font-medium text-slate-200">{election.title}</div>
                <div className="text-sm text-slate-400">{election.electionCode}</div>
                <div className="mt-1">
                  <span className={`px-2 py-0.5 rounded text-xs font-semibold ${
                    election.status === 'OPEN' ? 'bg-emerald-500/20 text-emerald-400' :
                    election.status === 'CLOSED' ? 'bg-rose-500/20 text-rose-400' :
                    'bg-slate-500/20 text-slate-400'
                  }`}>
                    {election.status}
                  </span>
                </div>
              </button>
            ))}
          </div>
        </div>

        <div className="lg:col-span-2 space-y-6">
          {selectedElection ? (
            <>
              <div className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg">
                <div className="flex items-center justify-between mb-4">
                  <h2 className="text-xl font-bold text-white">{selectedElection.title}</h2>
                  <div className={`text-2xl font-bold ${getHealthColor(electionHealth?.healthStatus)}`}>
                    {electionHealth?.healthScore || 0}/100
                  </div>
                </div>

                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div className="bg-slate-700/50 p-4 rounded-lg">
                    <div className="text-sm text-slate-400">Open Incidents</div>
                    <div className="text-xl font-bold text-amber-400">{electionStatus?.openIncidents || 0}</div>
                  </div>
                  <div className="bg-slate-700/50 p-4 rounded-lg">
                    <div className="text-sm text-slate-400">Critical</div>
                    <div className="text-xl font-bold text-rose-400">{electionStatus?.criticalIncidents || 0}</div>
                  </div>
                  <div className="bg-slate-700/50 p-4 rounded-lg">
                    <div className="text-sm text-slate-400">Votes Cast</div>
                    <div className="text-xl font-bold text-white">{electionStatus?.totalVotesCast || 0}</div>
                  </div>
                  <div className="bg-slate-700/50 p-4 rounded-lg">
                    <div className="text-sm text-slate-400">Turnout</div>
                    <div className="text-xl font-bold text-blue-400">{electionStatus?.turnoutPercentage || 0}%</div>
                  </div>
                </div>
              </div>

              {warnings.length > 0 && (
                <div className="bg-slate-800 p-6 rounded-xl border border-slate-700 shadow-lg">
                  <h3 className="text-lg font-bold text-white mb-4">Operational Warnings</h3>
                  <div className="space-y-2">
                    {warnings.map((warning, index) => (
                      <div key={index} className={`p-3 rounded-lg border ${
                        warning.severity === 'CRITICAL' ? 'bg-rose-500/10 border-rose-500/20' :
                        warning.severity === 'HIGH' ? 'bg-orange-500/10 border-orange-500/20' :
                        'bg-amber-500/10 border-amber-500/20'
                      }`}>
                        <span className={`font-semibold ${
                          warning.severity === 'CRITICAL' ? 'text-rose-400' :
                          warning.severity === 'HIGH' ? 'text-orange-400' :
                          'text-amber-400'
                        }`}>
                          [{warning.severity}]
                        </span>
                        <span className="text-slate-300 ml-2">{warning.message}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="bg-slate-800 p-12 rounded-xl border border-slate-700 shadow-lg text-center">
              <div className="text-slate-500">Select an election to view operations data</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
