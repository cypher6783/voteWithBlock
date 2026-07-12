import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Votetrust Ledger | Enterprise-Grade Decentralized Voting System",
  description: "Secure, anonymous, and immutable digital voting powered by Hyperledger Fabric blockchain networks. Face verification and liveness detection off-chain.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full flex flex-col bg-slate-50 text-slate-900 transition-colors duration-200">
        {children}
      </body>
    </html>
  );
}
