import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { requestWorkFromHome } from "../../api";

const WEEK_DAYS = ["M", "T", "W", "T", "F", "S", "S"];

type LogRow = {
  date: string;
  day: string;
  weekOff?: boolean;
  breakTaken: string;
  grossHours: string;
  arrival: string;
  arrivalOk?: boolean;
  late?: string;
  visualPct: number;
};

const SAMPLE_LOGS: LogRow[] = [
  {
    date: "04 Aug",
    day: "Tue",
    breakTaken: "0h 0m",
    grossHours: "6h 4m",
    arrival: "On Time",
    arrivalOk: true,
    visualPct: 68,
  },
  {
    date: "03 Aug",
    day: "Mon",
    breakTaken: "0h 0m",
    grossHours: "0h 0m",
    arrival: "—",
    visualPct: 0,
  },
  {
    date: "02 Aug",
    day: "Sun",
    weekOff: true,
    breakTaken: "—",
    grossHours: "—",
    arrival: "—",
    visualPct: 0,
  },
  {
    date: "01 Aug",
    day: "Sat",
    weekOff: true,
    breakTaken: "—",
    grossHours: "—",
    arrival: "—",
    visualPct: 0,
  },
  {
    date: "31 Jul",
    day: "Fri",
    breakTaken: "0h 32m",
    grossHours: "8h 41m",
    arrival: "On Time",
    arrivalOk: true,
    visualPct: 92,
  },
  {
    date: "30 Jul",
    day: "Thu",
    breakTaken: "0h 15m",
    grossHours: "9h 2m",
    arrival: "0:28:26 late",
    late: "0:28:26 late",
    visualPct: 95,
  },
];

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

export default function HrmAttendancePage() {
  const [now, setNow] = useState(() => new Date());
  const [logTab, setLogTab] = useState<"log" | "calendar" | "requests">("log");
  const [hour24, setHour24] = useState(true);
  const [wfhOpen, setWfhOpen] = useState(false);
  const [wfhFrom, setWfhFrom] = useState("");
  const [wfhTo, setWfhTo] = useState("");
  const [wfhNote, setWfhNote] = useState("");
  const [wfhNotify, setWfhNotify] = useState("");
  const [wfhSaving, setWfhSaving] = useState(false);
  const [wfhError, setWfhError] = useState("");
  const [wfhOk, setWfhOk] = useState("");

  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(id);
  }, []);

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

  function openWfh() {
    setWfhError("");
    setWfhOk("");
    setWfhFrom("");
    setWfhTo("");
    setWfhNote("");
    setWfhNotify("");
    setWfhOpen(true);
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
      setWfhOk(`Work from home requested for ${wfhDays} day${wfhDays === 1 ? "" : "s"}.`);
      setWfhOpen(false);
    } catch (err) {
      setWfhError(err instanceof Error ? err.message : "Request failed");
    } finally {
      setWfhSaving(false);
    }
  }

  return (
    <div className="hrm-attendance">
      {wfhOk && <p className="hrm-wfh-toast">{wfhOk}</p>}
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
                <strong>Me</strong>
              </div>
              <div className="hrm-stat-metrics">
                <div>
                  <span>AVG HRS / DAY</span>
                  <strong>9h 34m</strong>
                </div>
                <div>
                  <span>ON TIME ARRIVAL</span>
                  <strong>25%</strong>
                </div>
              </div>
            </div>
            <div className="hrm-stat-block">
              <div className="hrm-stat-who">
                <span className="hrm-mini-avatar hrm-mini-avatar--team">TM</span>
                <strong>My Team</strong>
              </div>
              <div className="hrm-stat-metrics">
                <div>
                  <span>AVG HRS / DAY</span>
                  <strong>8h 12m</strong>
                </div>
                <div>
                  <span>ON TIME ARRIVAL</span>
                  <strong>62%</strong>
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
          <div className="hrm-shift-bar" aria-hidden="true">
            <div className="hrm-shift-fill" style={{ width: "72%" }} />
            <span className="hrm-shift-break" title="Break" />
          </div>
          <p className="hrm-shift-hours">9h 0m</p>
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
              <button type="button">Web Clock-In</button>
            </li>
            <li>
              <button type="button" onClick={openWfh}>
                Work From Home
              </button>
            </li>
            <li>
              <button type="button">On Duty</button>
            </li>
            <li>
              <Link to="/hrm/leave">Request leave</Link>
            </li>
            <li>
              <button type="button">Attendance Policy</button>
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

        <div className="hrm-log-filters">
          <span>Last 30 Days</span>
          <div className="hrm-month-strip">
            {["FEB", "MAR", "APR", "MAY", "JUN", "JUL"].map((m) => (
              <button type="button" key={m} className="hrm-month-btn">
                {m}
              </button>
            ))}
            <button type="button" className="hrm-month-btn is-active">
              30 DAYS
            </button>
          </div>
        </div>

        {logTab === "log" && (
          <div className="table-scroll">
            <table className="table hrm-log-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Attendance Visual</th>
                  <th>Break Taken</th>
                  <th>Gross Hours</th>
                  <th>Arrival</th>
                  <th>Log</th>
                </tr>
              </thead>
              <tbody>
                {SAMPLE_LOGS.map((row) => (
                  <tr key={`${row.day}-${row.date}`}>
                    <td>
                      <strong>
                        {row.day}, {row.date}
                      </strong>
                      {row.weekOff && <span className="hrm-woff">W-OFF</span>}
                    </td>
                    <td>
                      {row.weekOff ? (
                        <span className="hrm-full-woff">Full day Weekly-off</span>
                      ) : (
                        <div className="hrm-visual-track">
                          <div
                            className="hrm-visual-fill"
                            style={{ width: `${row.visualPct}%` }}
                          />
                        </div>
                      )}
                    </td>
                    <td>{row.breakTaken}</td>
                    <td>{row.grossHours}</td>
                    <td>
                      {row.arrivalOk ? (
                        <span className="hrm-on-time">✓ On Time</span>
                      ) : row.late ? (
                        <span className="hrm-late">{row.late}</span>
                      ) : (
                        row.arrival
                      )}
                    </td>
                    <td>
                      {row.weekOff ? (
                        "—"
                      ) : (
                        <span className="hrm-log-ok" aria-label="Logged">
                          ✓
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {logTab === "calendar" && (
          <p className="lead">Calendar view — coming soon. Use Attendance Log for now.</p>
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
