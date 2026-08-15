import { FormEvent, useEffect, useState } from "react";
import { api, Availability, DOCTOR_ID } from "../api";

function dayBounds() {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const end = new Date(start);
  end.setDate(end.getDate() + 7);
  return { from: start.toISOString(), to: end.toISOString() };
}

export default function AvailabilityPage() {
  const [items, setItems] = useState<Availability[]>([]);
  const [type, setType] = useState("BUSY");
  const [reason, setReason] = useState("");
  const [startsAt, setStartsAt] = useState("");
  const [endsAt, setEndsAt] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function load() {
    setError("");
    try {
      const { from, to } = dayBounds();
      const data = await api.availability(DOCTOR_ID, from, to);
      setItems(data.availability);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load");
    }
  }

  useEffect(() => {
    const soon = new Date();
    soon.setMinutes(0, 0, 0);
    soon.setHours(soon.getHours() + 1);
    const end = new Date(soon.getTime() + 60 * 60 * 1000);
    const local = (d: Date) =>
      new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    setStartsAt(local(soon));
    setEndsAt(local(end));
    load();
  }, []);

  async function onAdd(e: FormEvent) {
    e.preventDefault();
    setMessage("");
    setError("");
    try {
      await api.addAvailability({
        doctorId: DOCTOR_ID,
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
        availabilityType: type,
        reason,
      });
      setMessage("Availability block saved.");
      setReason("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    }
  }

  return (
    <section>
      <h1>Availability & busy blocks</h1>
      <p className="lead">
        Mark leave, busy, or blocked time. These appear on the busy chart and reduce open slots.
      </p>
      {error && <div className="msg error">{error}</div>}
      {message && <div className="msg ok">{message}</div>}
      <div className="grid-2">
        <form className="panel stack" onSubmit={onAdd}>
          <h2>Add block</h2>
          <label>
            Type
            <select value={type} onChange={(e) => setType(e.target.value)}>
              <option value="BUSY">Busy</option>
              <option value="LEAVE">Leave</option>
              <option value="BLOCKED">Blocked</option>
              <option value="AVAILABLE">Extra available</option>
            </select>
          </label>
          <label>
            Starts
            <input type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} required />
          </label>
          <label>
            Ends
            <input type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} required />
          </label>
          <label>
            Reason
            <input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Surgery / meeting" />
          </label>
          <button type="submit">Save block</button>
        </form>
        <div className="panel">
          <h2>Next 7 days</h2>
          <table className="table">
            <thead>
              <tr>
                <th>When</th>
                <th>Type</th>
                <th>Reason</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {items.map((a) => (
                <tr key={a.id}>
                  <td>
                    {new Date(a.startsAt).toLocaleString()}
                    <br />
                    → {new Date(a.endsAt).toLocaleString()}
                  </td>
                  <td>
                    <span className={`badge ${a.availabilityType}`}>{a.availabilityType}</span>
                  </td>
                  <td>{a.reason || "—"}</td>
                  <td>
                    <button
                      type="button"
                      className="secondary"
                      onClick={async () => {
                        await api.deleteAvailability(a.id);
                        await load();
                      }}
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
              {items.length === 0 && (
                <tr>
                  <td colSpan={4}>No blocks yet.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
