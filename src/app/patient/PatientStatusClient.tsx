"use client";

import { useCallback, useEffect, useState } from "react";

type QueueView = {
  queuePosition: number | null;
  patientsAhead: number;
  fixedConsultMinutes: number;
  estimatedConsultAt: string | null;
  estimatedWaitMinutes: number | null;
  nowServingToken: number | null;
  doctorName: string | null;
  isDoctorLate: boolean;
};

type Notification = {
  id: string;
  eventCode: string;
  channel: string;
  body: string;
  status: string;
  createdAt: string;
};

type ActiveVisit = {
  id: string;
  status: string;
  tokenNumber: number | null;
  scheduledStart: string | null;
  delayMinutes: number;
  doctorName: string | null;
  fixedConsultMinutes: number;
  events: {
    id: string;
    eventType: string;
    message: string | null;
    createdAt: string;
    toStatus: string | null;
  }[];
};

type StatusPayload = {
  activeVisit: ActiveVisit | null;
  queue: QueueView | null;
  notifications: Notification[];
  visits: {
    id: string;
    status: string;
    tokenNumber: number | null;
    scheduledStart: string | null;
    doctorName: string | null;
    createdAt: string;
  }[];
};

function fmtTime(value: string | null | undefined) {
  if (!value) return "—";
  return new Date(value).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}

function fmtDateTime(value: string | null | undefined) {
  if (!value) return "—";
  return new Date(value).toLocaleString();
}

export function PatientStatusClient() {
  const [data, setData] = useState<StatusPayload | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const res = await fetch("/api/patient/status", { cache: "no-store" });
      const json = await res.json();
      if (!res.ok) throw new Error(json.error || "Failed to load status");
      setData(json);
      setError("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    const t = setInterval(load, 15_000);
    return () => clearInterval(t);
  }, [load]);

  if (loading && !data) {
    return <p className="muted">Loading your visit status…</p>;
  }

  if (error) {
    return <p className="error">{error}</p>;
  }

  const active = data?.activeVisit;
  const queue = data?.queue;
  const appNotes =
    data?.notifications.filter((n) => n.channel === "PUSH") ?? [];
  const allNotes = data?.notifications ?? [];

  return (
    <div className="stack">
      <section className="card stack">
        {!active ? (
          <p>No active visit. Your history is below.</p>
        ) : (
          <>
            <div className="row" style={{ justifyContent: "space-between" }}>
              <span className="badge">{active.status.replaceAll("_", " ")}</span>
              {queue?.isDoctorLate || active.delayMinutes > 0 ? (
                <span className="badge" style={{ background: "#fde68a", color: "#92400e" }}>
                  Delayed +{active.delayMinutes || queue?.estimatedWaitMinutes || 0} min
                </span>
              ) : null}
            </div>

            <div className="grid-2">
              <div>
                <p className="muted" style={{ marginBottom: 0 }}>
                  Doctor
                </p>
                <p style={{ fontSize: "1.35rem", margin: 0, fontWeight: 700 }}>
                  {active.doctorName ?? queue?.doctorName ?? "Assigning…"}
                </p>
              </div>
              <div>
                <p className="muted" style={{ marginBottom: 0 }}>
                  Token number
                </p>
                <p style={{ fontSize: "1.8rem", margin: 0, fontWeight: 700 }}>
                  {active.tokenNumber ?? "Pending check-in"}
                </p>
              </div>
              <div>
                <p className="muted" style={{ marginBottom: 0 }}>
                  Fixed consult time
                </p>
                <p style={{ fontSize: "1.2rem", margin: 0, fontWeight: 700 }}>
                  {active.fixedConsultMinutes ??
                    queue?.fixedConsultMinutes ??
                    15}{" "}
                  minutes
                </p>
              </div>
              <div>
                <p className="muted" style={{ marginBottom: 0 }}>
                  When you can consult
                </p>
                <p style={{ fontSize: "1.2rem", margin: 0, fontWeight: 700 }}>
                  {active.status === "IN_CONSULT"
                    ? "Now — with doctor"
                    : fmtTime(queue?.estimatedConsultAt)}
                </p>
                {queue?.estimatedWaitMinutes != null &&
                active.status !== "IN_CONSULT" ? (
                  <p className="muted" style={{ margin: "0.25rem 0 0" }}>
                    Approx wait {queue.estimatedWaitMinutes} min
                    {queue.patientsAhead > 0
                      ? ` · ${queue.patientsAhead} patient(s) ahead`
                      : ""}
                    {queue.nowServingToken != null
                      ? ` · Now serving #${queue.nowServingToken}`
                      : ""}
                  </p>
                ) : null}
              </div>
            </div>

            <p className="muted" style={{ margin: 0 }}>
              Scheduled (fixed slot): {fmtDateTime(active.scheduledStart)}
            </p>
            <button type="button" className="secondary" onClick={load}>
              Refresh status
            </button>
          </>
        )}
      </section>

      <section className="card stack">
        <h2 style={{ margin: 0 }}>In-app notifications</h2>
        <p className="muted" style={{ margin: 0 }}>
          Same updates are also sent by SMS and email when consent is on.
        </p>
        {appNotes.length === 0 ? (
          <p className="muted">No app alerts yet.</p>
        ) : (
          <ul className="list">
            {appNotes.slice(0, 12).map((n) => (
              <li key={n.id}>
                <span className="badge">{n.eventCode.replaceAll("_", " ")}</span>
                <div>{n.body}</div>
                <div className="muted">{fmtDateTime(n.createdAt)}</div>
              </li>
            ))}
          </ul>
        )}
      </section>

      {active ? (
        <section className="card stack">
          <h2 style={{ margin: 0 }}>Exact timeline</h2>
          <div className="timeline">
            {active.events.map((ev) => (
              <div className="item" key={ev.id}>
                <time>{fmtDateTime(ev.createdAt)}</time>
                <strong>
                  {ev.eventType}
                  {ev.toStatus ? ` → ${ev.toStatus}` : ""}
                </strong>
                <div className="muted">{ev.message}</div>
              </div>
            ))}
          </div>
          {allNotes.length ? (
            <>
              <h3 style={{ marginBottom: 0 }}>All channels</h3>
              <ul className="list">
                {allNotes.slice(0, 15).map((n) => (
                  <li key={n.id}>
                    <span className="badge">
                      {n.channel} · {n.status}
                    </span>{" "}
                    {n.eventCode.replaceAll("_", " ")}
                    <div className="muted">{n.body}</div>
                  </li>
                ))}
              </ul>
            </>
          ) : null}
        </section>
      ) : null}

      <section className="card">
        <h2 style={{ marginTop: 0 }}>History</h2>
        {!data?.visits?.length ? (
          <p className="muted">No visits yet.</p>
        ) : (
          <ul className="list">
            {data.visits.map((v) => (
              <li key={v.id}>
                <strong>{v.status}</strong>
                {v.tokenNumber != null ? ` · Token ${v.tokenNumber}` : ""} ·{" "}
                {v.doctorName ?? "Unassigned"}
                <div className="muted">
                  {v.scheduledStart
                    ? fmtDateTime(v.scheduledStart)
                    : fmtDateTime(v.createdAt)}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
