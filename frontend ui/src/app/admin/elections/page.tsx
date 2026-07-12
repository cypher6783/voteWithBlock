"use client";

import React, { useState, useEffect } from "react";
import axios from "axios";

export default function ElectionsPage() {
  const [elections, setElections] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchElections = async () => {
      try {
        const res = await axios.get("/api/admin/elections", {
          headers: { Authorization: `Bearer ${localStorage.getItem("adminSession")}` }
        });
        setElections(res.data);
      } catch (err) {
        console.error("Failed to load elections", err);
      } finally {
        setLoading(false);
      }
    };
    fetchElections();
  }, []);

  return (
    <div className="p-8 bg-slate-50 min-h-screen">
      <h1 className="text-2xl font-bold text-slate-900 mb-6">Elections Management</h1>
      
      {loading ? (
        <p>Loading elections...</p>
      ) : (
        <div className="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-100 text-slate-600 border-b">
              <tr>
                <th className="p-4 font-semibold">Code</th>
                <th className="p-4 font-semibold">Title</th>
                <th className="p-4 font-semibold">Type</th>
                <th className="p-4 font-semibold">Status</th>
                <th className="p-4 font-semibold">Start Date</th>
                <th className="p-4 font-semibold">End Date</th>
              </tr>
            </thead>
            <tbody>
              {elections.map((election: any) => (
                <tr key={election.id} className="border-b last:border-0 hover:bg-slate-50">
                  <td className="p-4">{election.electionCode}</td>
                  <td className="p-4 font-medium text-slate-900">{election.title}</td>
                  <td className="p-4">{election.type}</td>
                  <td className="p-4">
                    <span className={`px-2 py-1 rounded text-xs font-bold ${
                      election.status === 'OPEN' ? 'bg-emerald-100 text-emerald-800' :
                      election.status === 'CLOSED' ? 'bg-rose-100 text-rose-800' :
                      'bg-slate-100 text-slate-800'
                    }`}>
                      {election.status}
                    </span>
                  </td>
                  <td className="p-4 text-slate-500">{new Date(election.startDate).toLocaleDateString()}</td>
                  <td className="p-4 text-slate-500">{new Date(election.endDate).toLocaleDateString()}</td>
                </tr>
              ))}
              {elections.length === 0 && (
                <tr>
                  <td colSpan={6} className="p-8 text-center text-slate-500">No elections found.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
