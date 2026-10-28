"use client";

import { createContext, useContext } from "react";

export type SessionView = { userId: string; fullName: string; roles: string[]; csrfToken: string };
const SessionContext = createContext<SessionView | null>(null);
export const SessionProvider = SessionContext.Provider;
export function useSession() {
  const context = useContext(SessionContext);
  if (!context) throw new Error("Session is not available in this route");
  return context;
}

