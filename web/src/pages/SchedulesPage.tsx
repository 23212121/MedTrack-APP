import { FormEvent, useEffect, useState } from "react";
import { api, DOCTOR_ID, ScheduleSlot } from "../api";

const DAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

type Row = { dayOfWeek: number; startTime: string; endTime: string; slotMinutes: number };

export default function SchedulesPage() {
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    setError("");
    try {
      const data = await api.weekly(DOCTOR_ID);
      setRows(
        data.schedules.map((s: ScheduleSlot) => ({
          dayOfWeek: s.dayOfWeek,
          startTime: s.startTime,
          endTime: s.endTime,
          slotMinutes: s.slotMinutes,
        }))
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load schedules");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  function addRow() {
    setRows((r) => [...r, { dayOfWeek: 1, startTime: "09:00", endTime: "13:00", slotMinutes: 15 }]);
  }

  async function onSave(e: FormEvent) {
    e.preventDefault();
    setMessage("");
    setError("");
    try {
      await api.saveWeekly(DOCTOR_ID, rows);
      setMessage("Weekly schedule saved.");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    }
  }

  return (
    <section>
      <h1>Doctor schedules</h1>
      <p className="lead">Weekly working hours for Dr. Mehta. Slots drive booking and the busy chart.</p>
      {error && <div className="msg error">{error}</div>}
      {message && <div className="msg ok">{message}</div>}
      <form className="panel" onSubmit={onSave}>
        {loading ? (
          <p>Loading…</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Day</th>
                <th>Start</th>
                <th>End</th>
                <th>Slot (min)</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((row, idx) => (
                <tr key={idx}>
                  <td>
                    <select
                      value={row.dayOfWeek}
                      onChange={(e) => {
                        const next = [...rows];
                        next[idx] = { ...row, dayOfWeek: Number(e.target.value) };
                        setRows(next);
                      }}
                    >
                      {DAYS.map((d, i) => (
                        <option key={d} value={i}>
                          {d}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <input
                      type="time"
                      value={row.startTime}
                      onChange={(e) => {
                        const next = [...rows];
                        next[idx] = { ...row, startTime: e.target.value };
                        setRows(next);
                      }}
                    />
                  </td>
                  <td>
                    <input
                      type="time"
                      value={row.endTime}
                      onChange={(e) => {
                        const next = [...rows];
                        next[idx] = { ...row, endTime: e.target.value };
                        setRows(next);
                      }}
                    />
                  </td>
                  <td>
                    <input
                      type="number"
                      min={5}
                      max={60}
                      value={row.slotMinutes}
                      onChange={(e) => {
                        const next = [...rows];
                        next[idx] = { ...row, slotMinutes: Number(e.target.value) };
                        setRows(next);
                      }}
                    />
                  </td>
                  <td>
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => setRows(rows.filter((_, i) => i !== idx))}
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <div className="row" style={{ marginTop: "1rem" }}>
          <button type="button" className="secondary" onClick={addRow}>
            Add window
          </button>
          <button type="submit">Save schedule</button>
        </div>
      </form>
    </section>
  );
}
