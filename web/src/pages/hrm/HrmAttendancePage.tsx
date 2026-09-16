import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  AttendanceBoard,
  AttendanceLog,
  clockIn,
  clockOut,
  getAttendanceBoard,
  requestWorkFromHome,
} from "../../api";
import { toast } from "../../toast";

const WEEK_DAYS = ["M", "T", "W", "T", "F", "S", "S"];
const SHIFT_START = "10:00";

function pad(n: number) {
  return String(n).padStart(2, "0");
}

function dayCount(from: string, to: string) {
  if (!from || !to) return 0;
  const a = new Date(from + "T00:00:00");
  const b = new Date(to + "T00:00:00");
  if (Number.isNaN(a.getTime()) || Number.isNaN(b.getTime()) || b < a) return 0;
  return Math.floor((b.getTime() - a.getTime()) / 86400000) + 1;
}

function formatTime(value?: string | null) {
  if (!value) return "—";
  return value.slice(0, 5);
}

function formatHours(value?: number | null) {
  if (value == null || Number.isNaN(Number(value))) return "—";
  const totalMins = Math.round(Number(value) * 60);
  const h = Math.floor(totalMins / 60);
  const m = totalMins % 60;
  return `${h}h ${m}m`;
}

function formatLogDate(iso: string) {
  const d = new Date(iso + "T00:00:00");
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, {
    weekday: "short",
    day: "2-digit",
    month: "short",
  });
}

function arrivalLabel(inTime?: string | null) {
  if (!inTime) return "—";
  return inTime.slice(0, 5) <= SHIFT_START ? "On Time" : "Late";
}

function visualPct(row: AttendanceLog) {
  if (row.totalHours == null) return row.inTime ? 40 : 0;
  return Math.max(8, Math.min(100, Math.round((Number(row.totalHours) / 9) * 100)));
}

export default function HrmAttendancePage() {
  const [now, setNow] = useState(() => new Date());
  const [logTab, setLogTab] = useState<"log" | "calendar" | "requests">("log");
  const [hour24, setHour24] = useState(true);
  const [board, setBoard] = useState<AttendanceBoard | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [wfhOpen, setWfhOpen] = useState(false);
  const [wfhFrom, setWfhFrom] = useState("");
  const [wfhTo, setWfhTo] = useState("");
  const [wfhNote, setWfhNote] = useState("");
  const [wfhNotify, setWfhNotify] = useState("");
  const [wfhSaving, setWfhSaving] = useState(false);
  const [wfhError, setWfhError] = useState("");

  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(id);
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      setBoard(await getAttendanceBoard());
    } catch (e) {
      setBoard(null);
      setError(e instanceof Error ? e.message : "Failed to load attendance");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const clock = useMemo(() => {
    let h = now.getHours();
    const m = pad(now.getMinutes());
    const s = pad(now.getSeconds());
    if (hour24) return `${pad(h)}:${m}:${s}`;
    const ampm = h >= 12 ? "PM" : "AM";
    h = h % 12 || 12;
    return `${pad(h)}:${m}:${s} ${ampm}`;
  }, [now, hour24]);

  const dateLabel = useMemo(
    () =>
      now.toLocaleDateString(undefined, {
        weekday: "short",
        day: "2-digit",
        month: "short",
        year: "numeric",
      }),
    [now],
  );

  const todayDow = (now.getDay() + 6) % 7; // Mon=0
  const wfhDays = dayCount(wfhFrom, wfhTo);
  const logs = board?.logs ?? [];
  const canClockIn = board?.canClockIn ?? false;
  const canClockOut = board?.canClockOut ?? false;

  function openWfh() {
    setWfhError("");
    setWfhFrom("");
    setWfhTo("");
    setWfhNote("");
    setWfhNotify("");
    setWfhOpen(true);
  }

  async function punch(kind: "in" | "out") {
    setBusy(true);
    setError("");
    try {
      if (kind === "in") {
        await clockIn();
        toast.success("Office in time recorded.");
      } else {
        await clockOut();
        toast.success("Office out time recorded.");
      }
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Clock update failed");
    } finally {
      setBusy(false);
    }
  }

  async function submitWfh(e: FormEvent) {
    e.preventDefault();
    if (!wfhFrom || !wfhTo) {
      setWfhError("Please select From and To dates");
      return;
    }
    if (wfhDays <= 0) {
      setWfhError("To date must be on or after From date");
      return;
    }
    setWfhSaving(true);
    setWfhError("");
    try {
      await requestWorkFromHome({
        fromDate: wfhFrom,
        toDate: wfhTo,
        note: wfhNote || undefined,
        notifyTo: wfhNotify || undefined,
      });
      toast.success(`Work from home requested for ${wfhDays} day${wfhDays === 1 ? "" : "s"}.`);
      setWfhOpen(false);
    } catch (err) {
      setWfhError(err instanceof Error ? err.message : "Request failed");
    } finally {
      setWfhSaving(false);
    }
  }

  return (
    <div className="hrm-attendance">
      {error && <p className="error">{error}</p>}
      <div className="hrm-cards">
        <section className="hrm-card">
          <div className="hrm-card-head">
            <h2>Attendance Stats</h2>
            <select defaultValue="week" aria-label="Stats range">
              <option value="week">Last Week</option>
              <option value="month">Last Month</option>
            </select>
          </div>
          <div className="hrm-stat-grid">
            <div className="hrm-stat-block">
              <div className="hrm-stat-who">
                <span className="hrm-mini-avatar">ME</span>
                <strong>{board?.employeeName || "Me"}</strong>
              </div>
              <div className="hrm-stat-metrics">
                <div>
                  <span>TODAY IN</span>
                  <strong>{formatTime(board?.inTime)}</strong>
                </div>
                <div>
                  <span>TODAY OUT</span>
                  <strong>{formatTime(board?.outTime)}</strong>
                </div>
              </div>
            </div>
            <div className="hrm-stat-block">
              <div className="hrm-stat-who">
                <span className="hrm-mini-avatar hrm-mini-avatar--team">HR</span>
                <strong>Hours</strong>
              </div>
              <div className="hrm-stat-metrics">
                <div>
                  <span>GROSS TODAY</span>
                  <strong>{formatHours(board?.totalHours)}</strong>
                </div>
                <div>
                  <span>LOGS</span>
                  <strong>{logs.length}</strong>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="hrm-card">
          <div className="hrm-card-head">
            <h2>Timings</h2>
          </div>
          <div className="hrm-week-dots">
            {WEEK_DAYS.map((d, i) => (
              <span
                key={`${d}-${i}`}
                className={i === todayDow ? "hrm-week-dot is-today" : "hrm-week-dot"}
              >
                {d}
              </span>
            ))}
          </div>
          <p className="hrm-shift-label">Today (10:00 AM - 7:00 PM)</p>
          <div className="hrm-office-times">
            <div>
              <span className="muted">Office in</span>
              <strong>{loading ? "…" : formatTime(board?.inTime)}</strong>
            </div>
            <div>
              <span className="muted">Office out</span>
              <strong>{loading ? "…" : formatTime(board?.outTime)}</strong>
            </div>
          </div>
        </section>

        <section className="hrm-card hrm-card--actions">
          <div className="hrm-card-head">
            <h2>Actions</h2>
          </div>
          <div className="hrm-clock-block">
            <p className="hrm-clock">{clock}</p>
            <p className="hrm-clock-date">{dateLabel}</p>
          </div>
          <ul className="hrm-action-list">
            <li>
              <button
                type="button"
                disabled={busy || !canClockIn}
                onClick={() => void punch("in")}
              >
                {canClockIn ? "Web Clock-In" : "Clocked in"}
              </button>
            </li>
            <li>
              <button
                type="button"
                disabled={busy || !canClockOut}
                onClick={() => void punch("out")}
              >
                {canClockOut ? "Web Clock-Out" : "Clock out"}
              </button>
            </li>
            <li>
              <button type="button" onClick={openWfh}>
                Work From Home
              </button>
            </li>
            <li>
              <Link to="/hrm/leave">Request leave</Link>
            </li>
            <li>
              <button type="button" className="secondary" onClick={() => void load()} disabled={loading}>
                {loading ? "Loading…" : "Refresh times"}
              </button>
            </li>
          </ul>
        </section>
      </div>

      <section className="hrm-logs panel">
        <div className="hrm-logs-head">
          <h2>Logs &amp; Requests</h2>
          <label className="hrm-toggle">
            <span>24 hour format</span>
            <input
              type="checkbox"
              checked={hour24}
              onChange={(e) => setHour24(e.target.checked)}
            />
          </label>
        </div>

        <div className="hrm-log-tabs">
          <button
            type="button"
            className={logTab === "log" ? "is-active" : undefined}
            onClick={() => setLogTab("log")}
          >
            Attendance Log
          </button>
          <button
            type="button"
            className={logTab === "calendar" ? "is-active" : undefined}
            onClick={() => setLogTab("calendar")}
          >
            Calendar
          </button>
          <button
            type="button"
            className={logTab === "requests" ? "is-active" : undefined}
            onClick={() => setLogTab("requests")}
          >
            Attendance Requests
          </button>
        </div>

        {logTab === "log" && (
          <div className="table-scroll">
            <table className="table hrm-log-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Office in</th>
                  <th>Office out</th>
                  <th>Gross Hours</th>
                  <th>Arrival</th>
                  <th>Log</th>
                </tr>
              </thead>
              <tbody>
                {logs.length === 0 && (
                  <tr>
                    <td colSpan={6} className="muted">
                      {loading
                        ? "Loading attendance…"
                        : "No office in/out times yet. Use Web Clock-In."}
                    </td>
                  </tr>
                )}
                {logs.map((row) => {
                  const arrival = arrivalLabel(row.inTime);
                  return (
                    <tr key={row.id}>
                      <td>
                        <strong>{formatLogDate(row.attendanceDate)}</strong>
                      </td>
                      <td>{formatTime(row.inTime)}</td>
                      <td>{formatTime(row.outTime)}</td>
                      <td>
                        <div className="hrm-visual-track" title={formatHours(row.totalHours)}>
                          <div className="hrm-visual-fill" style={{ width: `${visualPct(row)}%` }} />
                        </div>
                        {formatHours(row.totalHours)}
                      </td>
                      <td>
                        {arrival === "On Time" ? (
                          <span className="hrm-on-time">✓ On Time</span>
                        ) : arrival === "Late" ? (
                          <span className="hrm-late">Late</span>
                        ) : (
                          arrival
                        )}
                      </td>
                      <td>
                        {row.inTime ? (
                          <span className="hrm-log-ok" aria-label="Logged">
                            ✓
                          </span>
                        ) : (
                          "—"
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {logTab === "calendar" && (
          <p className="lead">Calendar view — coming soon. Use Attendance Log for office times.</p>
        )}
        {logTab === "requests" && (
          <p className="lead">No attendance requests pending.</p>
        )}
      </section>

      {wfhOpen && (
        <div
          className="hrm-wfh-backdrop"
          role="presentation"
          onClick={() => !wfhSaving && setWfhOpen(false)}
        >
          <div
            className="hrm-wfh-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="wfh-title"
            onClick={(e) => e.stopPropagation()}
          >
            <header className="hrm-wfh-head">
              <h2 id="wfh-title">Request work from home</h2>
              <button
                type="button"
                className="hrm-wfh-close"
                aria-label="Close"
                disabled={wfhSaving}
                onClick={() => setWfhOpen(false)}
              >
                ×
              </button>
            </header>
            <form onSubmit={submitWfh}>
              <div className="hrm-wfh-dates">
                <label>
                  <span>From</span>
                  <input
                    type="date"
                    value={wfhFrom}
                    onChange={(e) => setWfhFrom(e.target.value)}
                    required
                  />
                </label>
                <span className="hrm-wfh-days">
                  {wfhDays} day{wfhDays === 1 ? "" : "s"}
                </span>
                <label>
                  <span>To</span>
                  <input
                    type="date"
                    value={wfhTo}
                    onChange={(e) => setWfhTo(e.target.value)}
                    required
                  />
                </label>
              </div>
              <label className="hrm-wfh-field">
                <span>Note</span>
                <textarea
                  rows={4}
                  placeholder="Type here"
                  value={wfhNote}
                  onChange={(e) => setWfhNote(e.target.value)}
                />
              </label>
              <label className="hrm-wfh-field">
                <span>Notify</span>
                <input
                  type="text"
                  placeholder="Search employee"
                  value={wfhNotify}
                  onChange={(e) => setWfhNotify(e.target.value)}
                />
              </label>
              {wfhError && <p className="error">{wfhError}</p>}
              <div className="hrm-wfh-actions">
                <button
                  type="button"
                  className="btn"
                  disabled={wfhSaving}
                  onClick={() => setWfhOpen(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn primary hrm-request-btn"
                  disabled={wfhSaving}
                >
                  {wfhSaving ? "Requesting…" : "Request"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
