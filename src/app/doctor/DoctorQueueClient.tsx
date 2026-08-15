"use client";

import { useCallback, useEffect, useState } from "react";

type VisitRow = {
  id: string;
  status: string;
  tokenNumber: number | null;
  scheduledStart: string | null;
  delayMinutes: number;
  reason: string | null;
  patient: { fullName: string; phone: string };
  isPatientBooking?: boolean;
  createdByUserId?: string | null;
};

export function DoctorQueueClient({ initialVisits }: { initialVisits: VisitRow[] }) {
  const [visits, setVisits] = useState(initialVisits);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busyId, setBusyId] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      const res = await fetch("/api/visits");
      if (res.status === 401) {
        setError("Session expired — please sign in again");
        return;
      }
      const data = await res.json();
      if (!res.ok) {
        setError(data.error || "Could not refresh queue");
        return;
      }
      setVisits(
        (data.visits ?? [])
          .filter((v: VisitRow) =>
            ["BOOKED", "CHECKED_IN", "IN_CONSULT", "CALLED"].includes(v.status)
          )
          .map((v: VisitRow) => ({
            ...v,
            isPatientBooking:
              v.isPatientBooking ?? v.createdByUserId == null,
          }))
      );
      setError("");
    } catch {
      // Ignore transient network errors on poll
    }
  }, []);

  useEffect(() => {
    setVisits(initialVisits);
  }, [initialVisits]);

  useEffect(() => {
    void reload();
    const t = setInterval(() => void reload(), 10_000);
    return () => clearInterval(t);
  }, [reload]);

  async function act(id: string, action: "start" | "late" | "complete") {
    setBusyId(id);
    setError("");
    setMessage("");
    try {
      const res = await fetch(`/api/visits/${id}/${action}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: action === "late" ? JSON.stringify({ minutes: 15 }) : undefined,
      });
      const data = await res.json();
      if (!res.ok) {
        setError(data.error || "Action failed");
        return;
      }
      if (action === "late") {
        setMessage(
          "Marked +15 late. Patient notified on phone, email, and in-app."
        );
      } else if (action === "complete") {
        setMessage("Visit completed. Next patient notified.");
      } else {
        setMessage("Consult started. Patient notified.");
      }
      await reload();
    } catch {
      setError("Network error — try again");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section className="card stack">
      <p className="muted" style={{ marginTop: 0 }}>
        Your queue includes desk check-ins and online patient bookings. New
        public bookings appear here automatically (refreshes every 10s).
      </p>
      {error ? <p className="error">{error}</p> : null}
      {message ? <p className="success">{message}</p> : null}
      {visits.length === 0 ? (
        <p>
          No open visits yet. When a patient books online for you, it will show
          here with status <span className="badge">BOOKED</span>.
        </p>
      ) : (
        <ul className="list">
          {visits.map((v) => (
            <li key={v.id}>
              <div className="row" style={{ justifyContent: "space-between" }}>
                <div>
                  <strong>
                    #{v.tokenNumber ?? "—"} {v.patient.fullName}
                  </strong>{" "}
                  <span className="badge">{v.status}</span>
                  {v.isPatientBooking ? (
                    <span className="badge">Patient booking</span>
                  ) : null}
                  {v.delayMinutes ? (
                    <span className="badge">+{v.delayMinutes}m</span>
                  ) : null}
                  <div className="muted">
                    {v.scheduledStart
                      ? new Date(v.scheduledStart).toLocaleString()
                      : "Unscheduled"}{" "}
                    · {v.reason || "No reason"} · {v.patient.phone}
                  </div>
                </div>
                <div className="row">
                  {(v.status === "CHECKED_IN" || v.status === "BOOKED") && (
                    <button
                      type="button"
                      disabled={busyId === v.id}
                      onClick={() => act(v.id, "start")}
                    >
                      Start
                    </button>
                  )}
                  {v.status !== "COMPLETED" && (
                    <button
                      type="button"
                      className="secondary"
                      disabled={busyId === v.id}
                      onClick={() => act(v.id, "late")}
                    >
                      +15 late
                    </button>
                  )}
                  {(v.status === "IN_CONSULT" || v.status === "CHECKED_IN") && (
                    <button
                      type="button"
                      disabled={busyId === v.id}
                      onClick={() => act(v.id, "complete")}
                    >
                      Complete
                    </button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
      <button type="button" className="secondary" onClick={() => void reload()}>
        Refresh queue
      </button>
    </section>
  );
}
