import { useEffect, useState } from "react";
import { api, Appointment } from "../api";
import { session } from "../dl/MedTrackSession";

type DoctorPatient = Appointment & {
  phoneNumber?: string;
  patientPhone?: string;
  appointmentTimeLabel?: string;
  withinSchedule?: boolean;
  timingOk?: boolean;
};

type DayBoard = {
  date: string;
  hospitalId: number;
  doctorId: string;
  doctorName?: string;
  hospitalName?: string;
  tokenCount: number;
  withinClinicHours?: boolean;
  scheduleWindows?: { startTime: string; endTime: string; slotMinutes?: number }[];
  patients: DoctorPatient[];
};

type DetailDoc = {
  id: string;
  slot: number;
  patientName?: string;
  documentType: string;
  documentName: string;
  uploadDate?: string;
};

function todayIso() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export default function DoctorPortalPage() {
  const hospitalId = session.getHospitalId();
  const loginType = session.getLoginType();
  const doctorId =
    loginType === "USER"
      ? session.getUserId() || ""
      : session.getUserId()?.startsWith("DOC-")
        ? session.getUserId() || ""
        : "";

  const [showPatients, setShowPatients] = useState(false);
  const [board, setBoard] = useState<DayBoard | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<DoctorPatient | null>(null);
  const [documents, setDocuments] = useState<DetailDoc[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [busyDoc, setBusyDoc] = useState("");

  async function loadBoard() {
    if (!hospitalId) {
      setError("Hospital session required.");
      return;
    }
    if (!doctorId) {
      setError("Doctor login required. Sign in as a doctor (User) to open the portal.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      const data = await api.doctorTodayPatients(todayIso());
      setBoard(data as DayBoard);
    } catch (e) {
      setBoard(null);
      setError(e instanceof Error ? e.message : "Failed to load patients");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (showPatients) {
      void loadBoard();
    }
  }, [showPatients, hospitalId, doctorId]);

  async function openToken(patient: DoctorPatient) {
    setSelected(patient);
    setDocuments([]);
    setDetailLoading(true);
    setError("");
    try {
      const data = await api.doctorPatientDetail(patient.id);
      setSelected({ ...(data.patient as DoctorPatient) });
      setDocuments((data.documents as DetailDoc[]) || []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load patient details");
    } finally {
      setDetailLoading(false);
    }
  }

  async function downloadDoc(doc: DetailDoc) {
    const key = `${doc.id}-${doc.slot}`;
    setBusyDoc(key);
    try {
      await api.downloadPatientDocument(doc.id, doc.slot, doc.documentName);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Download failed");
    } finally {
      setBusyDoc("");
    }
  }

  const patients = board?.patients ?? [];

  return (
    <section className="doctor-portal-page">
      <h1>Doctor portal</h1>
      <p className="lead">
        Same-day patients for your hospital and doctor id only. Other doctors’ appointments stay
        hidden.
      </p>

      {error && <div className="msg error">{error}</div>}

      <div className="panel stack">
        <div className="row doctor-portal-meta">
          <div>
            <strong>Hospital</strong>
            <div className="muted">
              {board?.hospitalName || session.getHospitalName() || hospitalId || "—"}
            </div>
          </div>
          <div>
            <strong>Doctor</strong>
            <div className="muted">
              {board?.doctorName || doctorId || "Not signed in as doctor"}
            </div>
          </div>
          <div>
            <strong>Date</strong>
            <div className="muted">{board?.date || todayIso()}</div>
          </div>
        </div>

        <div className="row doctor-portal-actions">
          <button
            type="button"
            onClick={() => {
              setShowPatients(true);
              void loadBoard();
            }}
            disabled={loading || !doctorId}
          >
            {loading ? "Loading…" : "Show patient"}
          </button>
          {showPatients && (
            <button type="button" className="secondary" onClick={() => void loadBoard()}>
              Refresh
            </button>
          )}
        </div>
      </div>

      {showPatients && (
        <div className="panel">
          <div className="row doctor-portal-summary">
            <h2 style={{ margin: 0, fontSize: "1.05rem" }}>Patient list</h2>
            <span className="badge">
              Tokens today: {board?.tokenCount ?? patients.length}
            </span>
            {board?.withinClinicHours != null && (
              <span className={`badge ${board.withinClinicHours ? "ok" : "warn"}`}>
                {board.withinClinicHours ? "Within clinic hours" : "Outside clinic hours"}
              </span>
            )}
          </div>

          {board?.scheduleWindows && board.scheduleWindows.length > 0 && (
            <p className="muted doctor-portal-windows">
              Schedule:{" "}
              {board.scheduleWindows
                .map((w) => `${w.startTime}–${w.endTime}`)
                .join(" · ")}
            </p>
          )}

          {loading ? (
            <p className="muted">Loading today’s patients…</p>
          ) : patients.length === 0 ? (
            <p className="muted">No appointments for you at this hospital today.</p>
          ) : (
            <div className="doctor-token-grid">
              {patients.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  className={`doctor-token-tile${
                    p.withinSchedule === false ? " doctor-token-tile--warn" : ""
                  }`}
                  onClick={() => void openToken(p)}
                  title={p.patientName}
                >
                  <span className="doctor-token-num">{p.tokenNumber ?? "—"}</span>
                  <span className="doctor-token-name">{p.patientName}</span>
                  <span className="doctor-token-time">
                    {p.appointmentTimeLabel || ""}
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {selected && (
        <div
          className="modal-backdrop"
          role="presentation"
          onClick={() => setSelected(null)}
        >
          <div
            className="modal panel doctor-portal-modal"
            role="dialog"
            aria-modal="true"
            onClick={(e) => e.stopPropagation()}
          >
            <header className="modal-header">
              <div>
                <p className="modal-eyebrow">Token {selected.tokenNumber ?? "—"}</p>
                <h2>{selected.patientName}</h2>
              </div>
              <button
                type="button"
                className="modal-close"
                aria-label="Close"
                onClick={() => setSelected(null)}
              >
                ×
              </button>
            </header>

            {detailLoading ? (
              <p className="muted">Loading details…</p>
            ) : (
              <>
                <div className="doctor-portal-detail-grid">
                  <div>
                    <span className="muted">Phone</span>
                    <div>{selected.phoneNumber || selected.patientPhone || "—"}</div>
                  </div>
                  <div>
                    <span className="muted">Age / Gender</span>
                    <div>
                      {selected.patientAge ?? "—"}
                      {selected.gender ? ` · ${selected.gender}` : ""}
                    </div>
                  </div>
                  <div>
                    <span className="muted">Time</span>
                    <div>
                      {selected.appointmentTimeLabel || selected.appointmentTime || "—"}
                    </div>
                  </div>
                  <div>
                    <span className="muted">Status</span>
                    <div>{selected.status}</div>
                  </div>
                  <div className="doctor-portal-detail-span">
                    <span className="muted">Reason</span>
                    <div>{selected.reason || "—"}</div>
                  </div>
                  <div className="doctor-portal-detail-span">
                    <span className="muted">Address</span>
                    <div>{selected.address || "—"}</div>
                  </div>
                  {selected.withinSchedule === false && (
                    <div className="doctor-portal-detail-span msg error">
                      Appointment time is outside this doctor’s weekly schedule.
                    </div>
                  )}
                </div>

                <h3 className="doctor-portal-docs-title">Hospital reports / documents</h3>
                {documents.length === 0 ? (
                  <p className="muted">
                    No documents uploaded by this hospital for this patient.
                  </p>
                ) : (
                  <table className="table">
                    <thead>
                      <tr>
                        <th>Type</th>
                        <th>Document</th>
                        <th>Uploaded</th>
                      </tr>
                    </thead>
                    <tbody>
                      {documents.map((d) => {
                        const key = `${d.id}-${d.slot}`;
                        return (
                          <tr key={key}>
                            <td>{d.documentType}</td>
                            <td>
                              <button
                                type="button"
                                className="link-button"
                                disabled={busyDoc === key}
                                onClick={() => void downloadDoc(d)}
                              >
                                {busyDoc === key ? "Downloading…" : d.documentName}
                              </button>
                            </td>
                            <td>
                              {d.uploadDate
                                ? new Date(d.uploadDate).toLocaleString()
                                : "—"}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                )}
              </>
            )}

            <div className="modal-actions">
              <button type="button" className="secondary" onClick={() => setSelected(null)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
