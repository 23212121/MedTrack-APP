import { useEffect, useState } from "react";
import { api, DOCTOR_ID, Visit } from "../api";

function today() {
  const d = new Date();
  const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

export default function DoctorQueuePage() {
  const [visits, setVisits] = useState<Visit[]>([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function load() {
    try {
      const data = await api.visits(DOCTOR_ID, today());
      setVisits(data.visits);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load queue");
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function act(id: string, action: "start" | "late" | "complete") {
    setError("");
    setMessage("");
    try {
      const fn = action === "start" ? api.start : action === "late" ? api.late : api.complete;
      const v = await fn(id);
      if (action === "complete") {
        setMessage(
          `Completed. Total ${v.feeCurrency} ${v.totalFee}` +
            (v.overtimeFee ? ` (overtime ${v.feeCurrency} ${v.overtimeFee})` : "") +
            ". Patient notified by SMS + email."
        );
      } else if (action === "late") {
        setMessage(`Marked +15 late. Delay alerts sent.`);
      } else {
        setMessage(`Consult started. Checkup notification sent.`);
      }
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Action failed");
    }
  }

  return (
    <section>
      <h1>Doctor queue</h1>
      <p className="lead">Start consult, mark late, or complete — fees and notifications update automatically.</p>
      {error && <div className="msg error">{error}</div>}
      {message && <div className="msg ok">{message}</div>}
      <div className="panel">
        <table className="table">
          <thead>
            <tr>
              <th>Token</th>
              <th>Patient</th>
              <th>Status</th>
              <th>Scheduled</th>
              <th>Fees</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {visits.map((v) => (
              <tr key={v.id}>
                <td>{v.tokenNumber ?? "—"}</td>
                <td>
                  {v.patientName}
                  <div style={{ color: "var(--muted)", fontSize: "0.8rem" }}>{v.reason}</div>
                </td>
                <td>
                  <span className={`badge ${v.status}`}>{v.status}</span>
                  {v.delayMinutes ? ` · +${v.delayMinutes}m` : ""}
                </td>
                <td>{v.scheduledStart ? new Date(v.scheduledStart).toLocaleTimeString() : "—"}</td>
                <td>
                  {v.totalFee != null
                    ? `${v.feeCurrency} ${v.totalFee}`
                    : "—"}
                </td>
                <td className="row">
                  {(v.status === "CHECKED_IN" || v.status === "BOOKED") && (
                    <button onClick={() => act(v.id, "start")}>Start</button>
                  )}
                  {v.status !== "COMPLETED" && v.status !== "CANCELLED" && (
                    <button className="warn" onClick={() => act(v.id, "late")}>
                      +15 late
                    </button>
                  )}
                  {(v.status === "IN_CONSULT" || v.status === "CHECKED_IN") && (
                    <button onClick={() => act(v.id, "complete")}>Complete</button>
                  )}
                </td>
              </tr>
            ))}
            {visits.length === 0 && (
              <tr>
                <td colSpan={6}>No visits today. Check-in a booked patient first.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
