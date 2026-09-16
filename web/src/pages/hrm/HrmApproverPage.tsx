import { FormEvent, useCallback, useEffect, useState } from "react";
import {
  LeaveRequestRow,
  WfhRequest,
  decideApproverLeave,
  decideApproverWfh,
  getApproverPending,
  getHrmRights,
  grantHrmRights,
} from "../../api";
import { toast } from "../../toast";

const RIGHT_LABELS: Record<string, string> = {
  HRM_HOME: "Home",
  HRM_ATTENDANCE: "Attendance",
  HRM_LEAVE: "Leave",
  HRM_INBOX: "Inbox",
  HRM_PERFORMANCE: "Performance",
  HRM_APPS: "Apps",
  HRM_APPROVER: "Approver",
  HRM_HOLIDAY: "Manage holidays",
};

const DEFAULT_GRANT = [
  "HRM_HOME",
  "HRM_ATTENDANCE",
  "HRM_LEAVE",
  "HRM_INBOX",
  "HRM_PERFORMANCE",
  "HRM_APPS",
];

export default function HrmApproverPage() {
  const [leave, setLeave] = useState<LeaveRequestRow[]>([]);
  const [wfh, setWfh] = useState<WfhRequest[]>([]);
  const [catalog, setCatalog] = useState<string[]>(Object.keys(RIGHT_LABELS));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busyId, setBusyId] = useState("");
  const [grantUser, setGrantUser] = useState("");
  const [grantRights, setGrantRights] = useState<string[]>([...DEFAULT_GRANT, "HRM_APPROVER"]);
  const [granting, setGranting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [pending, rights] = await Promise.all([getApproverPending(), getHrmRights()]);
      setLeave(pending.leave || []);
      setWfh(pending.wfh || []);
      if (rights.catalog?.length) setCatalog(rights.catalog);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load approver inbox");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function decideLeave(row: LeaveRequestRow, status: "Approved" | "Rejected") {
    setBusyId(row.id);
    try {
      await decideApproverLeave(row.id, status);
      toast.success(`${status} leave for ${row.employeeName || row.employeeId || "employee"}`);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Decision failed");
    } finally {
      setBusyId("");
    }
  }

  async function decideWfh(row: WfhRequest, status: "Approved" | "Rejected") {
    setBusyId(row.id);
    try {
      await decideApproverWfh(row.id, status);
      toast.success(`${status} WFH for ${row.employeeName || row.employeeId || "employee"}`);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Decision failed");
    } finally {
      setBusyId("");
    }
  }

  function toggleRight(code: string) {
    setGrantRights((curr) =>
      curr.includes(code) ? curr.filter((c) => c !== code) : [...curr, code],
    );
  }

  async function onGrant(e: FormEvent) {
    e.preventDefault();
    const userId = grantUser.trim();
    if (!userId) {
      setError("Enter the staff user id (login username)");
      return;
    }
    setGranting(true);
    setError("");
    try {
      await grantHrmRights(userId, grantRights);
      toast.success(`HRM tabs granted to ${userId}`);
      setGrantUser("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Grant failed");
    } finally {
      setGranting(false);
    }
  }

  return (
    <div className="hrm-approver">
      <header className="hrm-approver-head">
        <div>
          <h2>Approver</h2>
          <p className="lead">Approve or reject pending leave and work-from-home requests.</p>
        </div>
      </header>

      {error && <p className="error">{error}</p>}
      {loading && !error && <p className="lead">Loading pending requests…</p>}

      <section className="panel">
        <h3>Pending leave</h3>
        {leave.length === 0 ? (
          <p className="muted">No pending leave requests.</p>
        ) : (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Employee</th>
                  <th>Type</th>
                  <th>From</th>
                  <th>To</th>
                  <th>Days</th>
                  <th>Reason</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {leave.map((row) => (
                  <tr key={row.id}>
                    <td>{row.employeeName || row.employeeId || "—"}</td>
                    <td>{row.leaveType}</td>
                    <td>{row.fromDate}</td>
                    <td>{row.toDate}</td>
                    <td>{row.days ?? "—"}</td>
                    <td>{row.reason || "—"}</td>
                    <td className="hrm-approver-actions">
                      <button
                        type="button"
                        className="btn primary"
                        disabled={busyId === row.id}
                        onClick={() => void decideLeave(row, "Approved")}
                      >
                        Approve
                      </button>
                      <button
                        type="button"
                        className="btn"
                        disabled={busyId === row.id}
                        onClick={() => void decideLeave(row, "Rejected")}
                      >
                        Reject
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="panel">
        <h3>Pending work from home</h3>
        {wfh.length === 0 ? (
          <p className="muted">No pending WFH requests.</p>
        ) : (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Employee</th>
                  <th>From</th>
                  <th>To</th>
                  <th>Days</th>
                  <th>Note</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {wfh.map((row) => (
                  <tr key={row.id}>
                    <td>{row.employeeName || row.employeeId || "—"}</td>
                    <td>{row.fromDate}</td>
                    <td>{row.toDate}</td>
                    <td>{row.days ?? "—"}</td>
                    <td>{row.note || "—"}</td>
                    <td className="hrm-approver-actions">
                      <button
                        type="button"
                        className="btn primary"
                        disabled={busyId === row.id}
                        onClick={() => void decideWfh(row, "Approved")}
                      >
                        Approve
                      </button>
                      <button
                        type="button"
                        className="btn"
                        disabled={busyId === row.id}
                        onClick={() => void decideWfh(row, "Rejected")}
                      >
                        Reject
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="panel">
        <h3>Grant HRM tabs</h3>
        <p className="lead">
          Save rights in <code>company_application_setting</code>. The staff member will only see
          the tabs you select.
        </p>
        <form className="hrm-grant-form" onSubmit={onGrant}>
          <label>
            User id (login username)
            <input
              value={grantUser}
              onChange={(e) => setGrantUser(e.target.value)}
              placeholder="DOC-SEED-0001"
            />
          </label>
          <fieldset className="hrm-grant-rights">
            <legend>Tabs</legend>
            {catalog.map((code) => (
              <label key={code} className="hrm-grant-check">
                <input
                  type="checkbox"
                  checked={grantRights.includes(code)}
                  onChange={() => toggleRight(code)}
                />
                {RIGHT_LABELS[code] || code}
              </label>
            ))}
          </fieldset>
          <button type="submit" className="btn primary" disabled={granting}>
            {granting ? "Saving…" : "Save rights"}
          </button>
        </form>
      </section>
    </div>
  );
}
