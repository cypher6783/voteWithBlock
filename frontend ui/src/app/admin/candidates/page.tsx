"use client";

import React, { useState } from "react";

export default function CandidatesPage() {
  return (
    <div className="p-8 bg-slate-50 min-h-screen">
      <h1 className="text-2xl font-bold text-slate-900 mb-6">Candidate Management</h1>
      <div className="bg-white rounded-xl shadow-sm border border-slate-200 p-8 text-center text-slate-500">
        Candidate management view. Select an election to view or edit candidates.
      </div>
    </div>
  );
}
