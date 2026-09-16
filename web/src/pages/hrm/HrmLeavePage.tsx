import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  LeaveBalanceRow,
  LeaveRequestRow,
  LeaveSummary,
  getLeaveSummary,
  requestLeave,
} from "../../api";
import { toast } from "../../toast";

const BALANCE_COLORS = [
  "#a78bfa",
  "#86efac",
  "#38bdf8",
  "#fbbf24",
  "#f472b6",
  "#2dd4bf",
  "#c084fc",
  "#4ade80",
];

function Donut({
  percent,
  color,
  label,
  sub,
}: {
  percent: number;
  color: string;
  label: string;
  sub?: string;
}) {
  const p = Math.max(0, Math.min(100, percent));
  const r = 36;
  const c = 2 * Math.PI * r;
  const dash = (p / 100) * c;
  return (
    <div className="hrm-donut">
      <svg viewBox="0 0 96 96" width="110" height="110" aria-hidden="true">
        <circle cx="48" cy="48" r={r} fill="none" stroke="#e8edf2" strokeWidth="10" />
        <circle
          cx="48"
          cy="48"
          r={r}
          fill="none"
          stroke={color}
          strokeWidth="10"
          strokeLinecap="round"
          strokeDasharray={`${dash} ${c - dash}`}
          transform="rotate(-90 48 48)"
        />
        <text x="48" y="50" textAnchor="middle" className="hrm-donut-center">
          {sub ?? "Leave"}
        </text>
      </svg>
      <p className="hrm-donut-label">{label}</p>
    </div>
  );
}

function BarChart({
  labels,
  values,
  color = "#7c3aed",
}: {
  labels: string[];
  values: number[];
  color?: string;
}) {
  const max = Math.max(1, ...values);
  return (
    <div className="hrm-bars" role="img" aria-label="Bar chart">
      {labels.map((lab, i) => {
        const v = values[i] ?? 0;
        const h = Math.round((v / max) * 100);
        return (
          <div key={lab} className="hrm-bar-col" title={`${lab}: ${v}`}>
            <div className="hrm-bar-track">
              <div
                className="hrm-bar-fill"
                style={{ height: `${h}%`, background: color }}
              />
            </div>
            <span>{lab}</span>
          </div>
        );
      })}
    </div>
  );
}

function ConsumedDonut({
  items,
}: {
  items: { leaveType: string; days: number; color: string }[];
}) {
  const total = items.reduce((s, x) => s + x.days, 0);
  if (total <= 0) {
    return (
      <div className="hrm-donut hrm-donut--empty">
        <svg viewBox="0 0 96 96" width="120" height="120">
          <circle cx="48" cy="48" r="36" fill="none" stroke="#e8edf2" strokeWidth="10" />
          <text x="48" y="52" textAnchor="middle" className="hrm-donut-center">
            Leave Types
          </text>
        </svg>
        <p className="hrm-muted">No leave consumed yet</p>
      </div>
    );
  }
  const r = 36;
  const c = 2 * Math.PI * r;
  let offset = 0;
  return (
    <div className="hrm-donut">
      <svg viewBox="0 0 96 96" width="120" height="120" aria-hidden="true">
        {items.map((it) => {
          const len = (it.days / total) * c;
          const el = (
            <circle
              key={it.leaveType}
              cx="48"
              cy="48"
              r={r}
              fill="none"
              stroke={it.color}
              strokeWidth="10"
              strokeDasharray={`${len} ${c - len}`}
              strokeDashoffset={-offset}
              transform="rotate(-90 48 48)"
            />
          );
          offset += len;
          return el;
        })}
        <text x="48" y="52" textAnchor="middle" className="hrm-donut-center">
          Leave Types
        </text>
      </svg>
      <ul className="hrm-legend">
        {items.map((it) => (
          <li key={it.leaveType}>
            <span style={{ background: it.color }} />
            {it.leaveType} ({it.days})
          </li>
        ))}
      </ul>
    </div>
  );
}

export default function HrmLeavePage() {
  const year = new Date().getFullYear();
  const [data, setData] = useState<LeaveSummary | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({
    leaveTypeId: "",
    fromDate: "",
    toDate: "",
    reason: "",
  });

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const summary = await getLeaveSummary(year);
      setData(summary);
      setForm((f) =>
        f.leaveTypeId || !summary.balances[0]
          ? f
          : { ...f, leaveTypeId: summary.balances[0].leaveTypeId },
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load leave");
    } finally {
      setLoading(false);
    }
  }, [year]);

  useEffect(() => {
    void load();
  }, [load]);

  const pending = data?.pending ?? [];
  const history = data?.history ?? [];
  const balances = data?.balances ?? [];
  const stats = data?.stats;

  const yearLabel = useMemo(() => `Jan ${year} - Dec ${year}`, [year]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form.leaveTypeId || !form.fromDate || !form.toDate) {
      setError("Leave type and dates are required");
      return;
    }
    setSaving(true);
    setError("");
    try {
      await requestLeave({
        leaveTypeId: form.leaveTypeId,
        fromDate: form.fromDate,
        toDate: form.toDate,
        reason: form.reason || undefined,
        autoApprove: false,
      });
      setShowForm(false);
      setForm((f) => ({ ...f, fromDate: "", toDate: "", reason: "" }));
      toast.success("Leave submitted for approval.");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Request failed");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="hrm-leave">
      {error && <p className="error">{error}</p>}
      {loading && !data && <p className="lead">Loading leave…</p>}

      <section className="hrm-leave-pending panel">
        <div className="hrm-leave-pending-head">
          <h2>Pending leave requests</h2>
          <select aria-label="Year range" defaultValue={yearLabel} disabled>
            <option>{yearLabel}</option>
          </select>
        </div>
        <div className="hrm-leave-pending-body">
          <div className="hrm-leave-pending-main">
            {pending.length === 0 ? (
              <div className="hrm-leave-empty">
                <span aria-hidden="true">🎉</span>
                <p>
                  <strong>Hurray! No pending leave requests.</strong>
                  <br />
                  Request leave on the right!
                </p>
              </div>
            ) : (
              <ul className="hrm-leave-pending-list">
                {pending.map((r: LeaveRequestRow) => (
                  <li key={r.id}>
                    <strong>{r.leaveType}</strong> · {r.fromDate} → {r.toDate}
                    {r.reason ? ` · ${r.reason}` : ""}
                  </li>
                ))}
              </ul>
            )}
          </div>
          <aside className="hrm-leave-actions">
            <button
              type="button"
              className="btn primary hrm-request-btn"
              onClick={() => setShowForm((v) => !v)}
            >
              Request Leave
            </button>
            <button type="button" className="linkish">
              Request Credit for Compensatory Off
            </button>
            <button type="button" className="linkish">
              Leave Policy Explanation
            </button>
          </aside>
        </div>

        {showForm && (
          <form className="hrm-leave-form" onSubmit={onSubmit}>
            <label>
              Leave type
              <select
                value={form.leaveTypeId}
                onChange={(e) => setForm({ ...form, leaveTypeId: e.target.value })}
                required
              >
                {balances.map((b: LeaveBalanceRow) => (
                  <option key={b.leaveTypeId} value={b.leaveTypeId}>
                    {b.leaveType} ({b.available} available)
                  </option>
                ))}
              </select>
            </label>
            <label>
              From
              <input
                type="date"
                value={form.fromDate}
                onChange={(e) => setForm({ ...form, fromDate: e.target.value })}
                required
              />
            </label>
            <label>
              To
              <input
                type="date"
                value={form.toDate}
                onChange={(e) => setForm({ ...form, toDate: e.target.value })}
                required
              />
            </label>
            <label className="hrm-leave-form-reason">
              Reason
              <input
                value={form.reason}
                onChange={(e) => setForm({ ...form, reason: e.target.value })}
                placeholder="Optional"
              />
            </label>
            <button type="submit" className="btn primary" disabled={saving}>
              {saving ? "Submitting…" : "Submit leave"}
            </button>
          </form>
        )}
      </section>

      <section className="hrm-leave-stats">
        <h2>My Leave Stats</h2>
        <div className="hrm-leave-stat-grid">
          <article className="panel">
            <h3>Weekly Pattern</h3>
            <BarChart
              labels={stats?.weeklyLabels ?? ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]}
              values={stats?.weeklyPattern ?? [0, 0, 0, 0, 0, 0, 0]}
            />
          </article>
          <article className="panel">
            <h3>Consumed Leave Types</h3>
            <ConsumedDonut items={stats?.consumedByType ?? []} />
          </article>
          <article className="panel">
            <h3>Monthly Stats</h3>
            <BarChart
              labels={stats?.monthlyLabels ?? []}
              values={stats?.monthlyStats ?? []}
            />
          </article>
        </div>
      </section>

      <section className="hrm-leave-balances">
        <h2>Leave Balances</h2>
        <p className="lead">
          Cards come from the database for this doctor. Taking leave updates available days and
          the circle graphs.
        </p>
        <div className="hrm-balance-grid">
          {balances.length === 0 && (
            <article className="panel">
              <p className="hrm-muted">No leave balances yet.</p>
            </article>
          )}
          {balances.map((b, idx) => {
            const color = BALANCE_COLORS[idx % BALANCE_COLORS.length];
            const hasQuota = b.total > 0;
            return (
              <article key={b.id} className="panel hrm-balance-card">
                <div className="hrm-balance-card-head">
                  <h3>{b.leaveType}</h3>
                  <span className="linkish">View details</span>
                </div>
                {hasQuota ? (
                  <>
                    <Donut
                      percent={b.percentAvailable}
                      color={color}
                      label={`${b.available} Day${b.available === 1 ? "" : "s"} Available`}
                      sub={`${Math.round(b.percentAvailable)}%`}
                    />
                    <p className="hrm-balance-meta">
                      Used {b.used} of {b.total}
                    </p>
                  </>
                ) : (
                  <p className="hrm-muted hrm-balance-empty">No data to display.</p>
                )}
              </article>
            );
          })}
        </div>
      </section>

      <section className="hrm-leave-pending panel">
        <div className="hrm-leave-pending-head">
          <h2>My leave</h2>
        </div>
        {history.length === 0 ? (
          <p className="hrm-muted">No leave recorded this year. Use Request Leave to apply.</p>
        ) : (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>From</th>
                  <th>To</th>
                  <th>Status</th>
                  <th>Reason</th>
                </tr>
              </thead>
              <tbody>
                {history.map((r: LeaveRequestRow) => (
                  <tr key={r.id}>
                    <td>{r.leaveType}</td>
                    <td>{r.fromDate}</td>
                    <td>{r.toDate}</td>
                    <td>{r.status}</td>
                    <td>{r.reason || "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
