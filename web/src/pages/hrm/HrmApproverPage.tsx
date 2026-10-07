import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  ApproverEmployee,
  ApproverEmployeeFile,
  AttendanceLog,
  LeaveRequestRow,
  WfhRequest,
  decideApproverAttendance,
  decideApproverLeave,
  decideApproverWfh,
  getApproverEmployeeFile,
  getApproverEmployees,
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

function formatTime(value?: string | null) {
  if (!value) return "—";
  const text = String(value);
  return text.length >= 5 ? text.slice(0, 5) : text;
}

function formatHours(value?: number | null) {
  if (value == null || Number.isNaN(Number(value))) return "—";
  const totalMins = Math.round(Number(value) * 60);
  const h = Math.floor(totalMins / 60);
  const m = totalMins % 60;
  return `${h}h ${m}m`;
}

function pendingStatus(status?: string) {
  return !status || status.toLowerCase() === "pending" || status.toLowerCase() === "present";
}

export default function HrmApproverPage() {
  const [leave, setLeave] = useState<LeaveRequestRow[]>([]);
  const [wfh, setWfh] = useState<WfhRequest[]>([]);
  const [employees, setEmployees] = useState<ApproverEmployee[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [file, setFile] = useState<ApproverEmployeeFile | null>(null);
  const [query, setQuery] = useState("");
  const [catalog, setCatalog] = useState<string[]>(Object.keys(RIGHT_LABELS));
  const [loading, setLoading] = useState(true);
  const [fileLoading, setFileLoading] = useState(false);
  const [error, setError] = useState("");
  const [busyId, setBusyId] = useState("");
  const [grantUser, setGrantUser] = useState("");
  const [grantRights, setGrantRights] = useState<string[]>([...DEFAULT_GRANT, "HRM_APPROVER"]);
  const [granting, setGranting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [pending, roster, rights] = await Promise.all([
        getApproverPending(),
        getApproverEmployees(),
        getHrmRights(),
      ]);
      setLeave(pending.leave || []);
      setWfh(pending.wfh || []);
      setEmployees(roster.employees || []);
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

  const loadEmployee = useCallback(async (employeeId: string) => {
    if (!employeeId) {
      setFile(null);
      return;
    }
    setFileLoading(true);
    setError("");
    try {
      setFile(await getApproverEmployeeFile(employeeId));
    } catch (err) {
      setFile(null);
      setError(err instanceof Error ? err.message : "Could not load employee HRM");
    } finally {
      setFileLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadEmployee(selectedId);
  }, [selectedId, loadEmployee]);

  const filteredEmployees = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return employees;
    return employees.filter((row) =>
      `${row.name || ""} ${row.employeeId} ${row.department || ""} ${row.designation || ""}`
        .toLowerCase()
        .includes(q),
    );
  }, [employees, query]);

  async function decideLeave(row: LeaveRequestRow, status: "Approved" | "Dismissed") {
    setBusyId(row.id);
    try {
      await decideApproverLeave(row.id, status);
      toast.success(`${status} leave for ${row.employeeName || row.employeeId || "employee"}`);
      await load();
      if (selectedId) await loadEmployee(selectedId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Decision failed");
    } finally {
      setBusyId("");
    }
  }

  async function decideWfh(row: WfhRequest, status: "Approved" | "Dismissed") {
    setBusyId(row.id);
    try {
      await decideApproverWfh(row.id, status);
      toast.success(`${status} WFH for ${row.employeeName || row.employeeId || "employee"}`);
      await load();
      if (selectedId) await loadEmployee(selectedId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Decision failed");
    } finally {
      setBusyId("");
    }
  }

  async function decideTime(row: AttendanceLog, status: "Approved" | "Dismissed") {
    setBusyId(row.id);
    try {
      await decideApproverAttendance(row.id, status);
      toast.success(`${status} time for ${row.employeeName || row.attendanceDate}`);
      if (selectedId) await loadEmployee(selectedId);
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

  const selected = file?.employee;

  return (
    <div className="hrm-approver">
      <header className="hrm-approver-head">
        <div>
          <h2>Approver</h2>
          <p className="lead">
            Check any employee’s time and leave. Approve or dismiss pending requests.
          </p>
        </div>
      </header>

      {error && <p className="error">{error}</p>}
      {loading && !error && <p className="lead">Loading approver workspace…</p>}

      <section className="panel hrm-approver-employee">
        <h3>Employee HRM</h3>
        <label>
          Search employee
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Name, employee id, department"
          />
        </label>
        <label>
          Employee
          <select
            value={selectedId}
            onChange={(e) => setSelectedId(e.target.value)}
            aria-label="Select employee"
          >
            <option value="">Select an employee</option>
            {filteredEmployees.map((row) => (
              <option key={row.employeeId} value={row.employeeId}>
                {row.name || row.employeeId}
                {row.designation ? ` · ${row.designation}` : ""}
              </option>
            ))}
          </select>
        </label>
        {selected && (
          <p className="muted">
            {selected.name} ({selected.employeeId})
            {selected.department ? ` · ${selected.department}` : ""}
            {selected.designation ? ` · ${selected.designation}` : ""}
          </p>
        )}
        {file?.balances?.length ? (
          <p className="muted">
            Leave balance:{" "}
            {file.balances
              .map((b) => `${b.leaveType || "Leave"} ${b.available ?? 0}/${b.total ?? 0}`)
              .join(" · ")}
          </p>
        ) : null}
      </section>

      {fileLoading && selectedId && <p className="lead">Loading employee time and leave…</p>}

      {file && (
        <>
          <section className="panel">
            <h3>Time</h3>
            {(file.attendance || []).length === 0 ? (
              <p className="muted">No clock-in records for this employee.</p>
            ) : (
              <div className="table-scroll">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>In</th>
                      <th>Out</th>
                      <th>Hours</th>
                      <th>Status</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {file.attendance.map((row) => (
                      <tr key={row.id}>
                        <td>{row.attendanceDate}</td>
                        <td>{formatTime(row.inTime)}</td>
                        <td>{formatTime(row.outTime)}</td>
                        <td>{formatHours(row.totalHours)}</td>
                        <td>{row.status || "—"}</td>
                        <td className="hrm-approver-actions">
                          {pendingStatus(row.status) ? (
                            <>
                              <button
                                type="button"
                                className="btn primary"
                                disabled={busyId === row.id}
                                onClick={() => void decideTime(row, "Approved")}
                              >
                                Approve
                              </button>
                              <button
                                type="button"
                                className="btn"
                                disabled={busyId === row.id}
                                onClick={() => void decideTime(row, "Dismissed")}
                              >
                                Dismiss
                              </button>
                            </>
                          ) : (
                            "—"
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <section className="panel">
            <h3>Leave</h3>
            {(file.leave || []).length === 0 ? (
              <p className="muted">No leave requests for this employee.</p>
            ) : (
              <div className="table-scroll">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Type</th>
                      <th>From</th>
                      <th>To</th>
                      <th>Days</th>
                      <th>Reason</th>
                      <th>Status</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {file.leave.map((row) => (
                      <tr key={row.id}>
                        <td>{row.leaveType}</td>
                        <td>{row.fromDate}</td>
                        <td>{row.toDate}</td>
                        <td>{row.days ?? "—"}</td>
                        <td>{row.reason || "—"}</td>
                        <td>{row.status}</td>
                        <td className="hrm-approver-actions">
                          {pendingStatus(row.status) ? (
                            <>
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
                                onClick={() => void decideLeave(row, "Dismissed")}
                              >
                                Dismiss
                              </button>
                            </>
                          ) : (
                            "—"
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}

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
                    <td>
                      <button
                        type="button"
                        className="linkish"
                        onClick={() => setSelectedId(row.employeeId || "")}
                      >
                        {row.employeeName || row.employeeId || "—"}
                      </button>
                    </td>
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
                        onClick={() => void decideLeave(row, "Dismissed")}
                      >
                        Dismiss
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
                    <td>
                      <button
                        type="button"
                        className="linkish"
                        onClick={() => setSelectedId(row.employeeId || "")}
                      >
                        {row.employeeName || row.employeeId || "—"}
                      </button>
                    </td>
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
                        onClick={() => void decideWfh(row, "Dismissed")}
                      >
                        Dismiss
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
