import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, type HospitalPatientListRow } from "../api";
import { useCareChatUnread } from "../careChatUnread";
import CareChatWindow from "../components/CareChatWindow";
import ChatLink from "../components/ChatLink";
import { session } from "../dl/MedTrackSession";

type Filters = {
  patientName: string;
  doctorName: string;
  tokenNumber: string;
  chatCount: string;
  createdDate: string;
  createdUser: string;
};

const EMPTY: Filters = {
  patientName: "",
  doctorName: "",
  tokenNumber: "",
  chatCount: "",
  createdDate: "",
  createdUser: "",
};

function includes(value: string | number | undefined, filter: string) {
  if (!filter.trim()) return true;
  return String(value ?? "").toLowerCase().includes(filter.trim().toLowerCase());
}

export default function HospitalPatientListPage() {
  const [params, setParams] = useSearchParams();
  const [rows, setRows] = useState<HospitalPatientListRow[]>([]);
  const [hospitalName, setHospitalName] = useState("");
  const [totals, setTotals] = useState({ patients: 0, doctors: 0, bookings: 0 });
  const [filters, setFilters] = useState<Filters>(EMPTY);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [open, setOpen] = useState<HospitalPatientListRow | null>(null);

  function openChat(row: HospitalPatientListRow) {
    setOpen(row);
    setParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set("chat", row.appointmentId);
      return next;
    }, { replace: true });
  }

  function closeChat() {
    setOpen(null);
    setParams((prev) => {
      const next = new URLSearchParams(prev);
      next.delete("chat");
      return next;
    }, { replace: true });
    void load();
  }

  async function load() {
    setLoading(true);
    try {
      const data = await api.hospitalPatientList();
      setRows(data.rows || []);
      setHospitalName(data.hospitalName || session.getHospitalName() || "");
      setTotals({
        patients: data.totalPatients || 0,
        doctors: data.totalDoctors || 0,
        bookings: data.totalBookings || 0,
      });
      setError("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load patient list");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  useEffect(() => {
    const chatId = params.get("chat");
    if (!chatId || rows.length === 0) return;
    const match = rows.find((row) => row.appointmentId === chatId);
    if (match && open?.appointmentId !== match.appointmentId) {
      setOpen(match);
    }
  }, [params, rows, open?.appointmentId]);

  const preloaded = useMemo(() => {
    const map: Record<string, string[] | undefined> = {};
    for (const row of rows) map[row.appointmentId] = row.otherMessageAts;
    return map;
  }, [rows]);
  const fallbackCounts = useMemo(() => {
    const map: Record<string, number | undefined> = {};
    for (const row of rows) map[row.appointmentId] = row.unreadCount ?? row.chatCount;
    return map;
  }, [rows]);
  const unread = useCareChatUnread(
    rows.map((row) => row.appointmentId),
    "HOSPITAL",
    preloaded,
    fallbackCounts,
  );

  const visible = useMemo(
    () =>
      rows.filter(
        (row) =>
          includes(row.patientName, filters.patientName) &&
          includes(row.doctorName, filters.doctorName) &&
          includes(row.tokenNumber, filters.tokenNumber) &&
          includes(unread[row.appointmentId] ?? 0, filters.chatCount) &&
          includes(row.createdDate, filters.createdDate) &&
          includes(row.createdUser, filters.createdUser),
      ),
    [rows, filters, unread],
  );

  function setFilter(key: keyof Filters, value: string) {
    setFilters((prev) => ({ ...prev, [key]: value }));
  }

  return (
    <section className="stack">
      <div className="hospital-patient-list-head">
        <div>
          <h1>Patient list</h1>
          <p className="lead">
            Bookings at {hospitalName || "your hospital"}. Open chat to message the patient or doctor.
          </p>
        </div>
        <button type="button" className="secondary" onClick={() => void load()}>
          Refresh
        </button>
      </div>
      {error && <div className="msg error">{error}</div>}
      <div className="hospital-patient-stats">
        <div className="hospital-patient-stat">
          <span>Patients</span>
          <strong>{totals.patients}</strong>
        </div>
        <div className="hospital-patient-stat">
          <span>Doctors</span>
          <strong>{totals.doctors}</strong>
        </div>
        <div className="hospital-patient-stat">
          <span>Appointments</span>
          <strong>{totals.bookings}</strong>
        </div>
      </div>
      {loading ? (
        <p className="muted">Loading…</p>
      ) : (
        <div className="table-wrap">
          <table className="table hospital-patient-table">
            <thead>
              <tr>
                <th>Patient name</th>
                <th>Doctor name</th>
                <th>Token</th>
                <th>Chat</th>
                <th>Created date</th>
                <th>Created user</th>
              </tr>
              <tr className="hospital-patient-filters">
                <th>
                  <input
                    value={filters.patientName}
                    onChange={(e) => setFilter("patientName", e.target.value)}
                    placeholder="Filter"
                  />
                </th>
                <th>
                  <input
                    value={filters.doctorName}
                    onChange={(e) => setFilter("doctorName", e.target.value)}
                    placeholder="Filter"
                  />
                </th>
                <th>
                  <input
                    value={filters.tokenNumber}
                    onChange={(e) => setFilter("tokenNumber", e.target.value)}
                    placeholder="Filter"
                  />
                </th>
                <th>
                  <input
                    value={filters.chatCount}
                    onChange={(e) => setFilter("chatCount", e.target.value)}
                    placeholder="Filter"
                  />
                </th>
                <th>
                  <input
                    value={filters.createdDate}
                    onChange={(e) => setFilter("createdDate", e.target.value)}
                    placeholder="Filter"
                  />
                </th>
                <th>
                  <input
                    value={filters.createdUser}
                    onChange={(e) => setFilter("createdUser", e.target.value)}
                    placeholder="Filter"
                  />
                </th>
              </tr>
            </thead>
            <tbody>
              {visible.length === 0 ? (
                <tr>
                  <td colSpan={6} className="muted">
                    No bookings match these filters.
                  </td>
                </tr>
              ) : (
                visible.map((row) => (
                  <tr key={row.appointmentId}>
                    <td>{row.patientName}</td>
                    <td>{row.doctorName || row.doctorId}</td>
                    <td>{row.tokenNumber ?? "—"}</td>
                    <td>
                      <ChatLink
                        href={row.chatLink || `/patient-list?chat=${encodeURIComponent(row.appointmentId)}`}
                        unread={unread[row.appointmentId] ?? 0}
                        onClick={() => openChat(row)}
                      />
                    </td>
                    <td>
                      {row.createdDate ? new Date(row.createdDate).toLocaleString() : row.appointmentDate || "—"}
                    </td>
                    <td>{row.createdUser || "—"}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
      {open && (
        <CareChatWindow
          appointmentId={open.appointmentId}
          title={`${open.patientName} · ${open.doctorName || "Doctor"}`}
          subtitle={`Token ${open.tokenNumber ?? "—"} · hospital, doctor, and patient`}
          senderType="HOSPITAL"
          senderName={session.getUsername() || session.getHospitalName() || "Hospital"}
          onClose={closeChat}
        />
      )}
    </section>
  );
}
