import { useEffect, useState } from "react";
import { api, NotificationRow } from "../api";

export default function NotificationsPage() {
  const [rows, setRows] = useState<NotificationRow[]>([]);
  const [error, setError] = useState("");

  async function load() {
    setError("");
    try {
      const data = await api.notifications();
      setRows(data.notifications);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load notifications");
    }
  }

  useEffect(() => {
    load();
    const t = setInterval(load, 10000);
    return () => clearInterval(t);
  }, []);

  return (
    <section>
      <h1>Mail & phone notifications</h1>
      <p className="lead">
        Checkup lifecycle alerts: booking, check-in, start, delay, you’re next, completion, and
        overtime fee — SMS (phone) and email.
      </p>
      {error && <div className="msg error">{error}</div>}
      <div className="panel">
        <div className="row" style={{ marginBottom: "0.8rem" }}>
          <button type="button" className="secondary" onClick={load}>
            Refresh
          </button>
        </div>
        <table className="table">
          <thead>
            <tr>
              <th>When</th>
              <th>Event</th>
              <th>Channel</th>
              <th>To</th>
              <th>Status</th>
              <th>Message</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((n) => (
              <tr key={n.id}>
                <td>{new Date(n.createdAt).toLocaleString()}</td>
                <td>{n.eventCode}</td>
                <td>
                  <span className="badge">{n.channel === "SMS" ? "Phone/SMS" : "Email"}</span>
                </td>
                <td>{n.recipient}</td>
                <td>{n.status}</td>
                <td>
                  {n.subject ? <strong>{n.subject}</strong> : null}
                  <div style={{ color: "var(--muted)" }}>{n.body}</div>
                </td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr>
                <td colSpan={6}>No notifications yet. Check in or complete a visit to generate them.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
