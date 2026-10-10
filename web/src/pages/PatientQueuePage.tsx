import { FormEvent, useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  api,
  Appointment,
  HospitalDoctor,
  HospitalRegistrationSummary,
  QueueStatus,
} from "../api";
import { useCareChatUnread, type CareChatRole } from "../careChatUnread";
import CareChatWindow from "../components/CareChatWindow";
import ChatLink from "../components/ChatLink";
import { getHospitalId, isLoggedIn } from "../auth";
import { session } from "../dl/MedTrackSession";
import { useT } from "../i18n";

function formatApptTime(value?: string | number | null): string {
  if (value == null || value === "") return "—";
  if (typeof value === "number") {
    const d = new Date(value * (value < 1e12 ? 1000 : 1));
    return Number.isNaN(d.getTime())
      ? "—"
      : d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  }
  const raw = String(value);
  if (/^\d{1,2}:\d{2}/.test(raw)) return raw.slice(0, 5);
  const d = new Date(raw);
  return Number.isNaN(d.getTime())
    ? raw
    : d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function formatApptDate(value?: string | number[] | null): string {
  if (value == null || value === "") return "—";
  if (Array.isArray(value) && value.length >= 3) {
    const [y, m, d] = value;
    return `${y}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
  }
  const raw = String(value);
  if (/^\d{4}-\d{2}-\d{2}/.test(raw)) return raw.slice(0, 10);
  const d = new Date(raw);
  return Number.isNaN(d.getTime()) ? raw : d.toISOString().slice(0, 10);
}

export default function PatientQueuePage() {
  const t = useT();
  const [params] = useSearchParams();
  const sessionHospital = isLoggedIn() ? getHospitalId() : "";
  const [hospitals, setHospitals] = useState<HospitalRegistrationSummary[]>([]);
  const [doctors, setDoctors] = useState<HospitalDoctor[]>([]);
  const [hospitalId, setHospitalId] = useState(
    params.get("hospitalId") || sessionHospital || "",
  );
  const [doctorId, setDoctorId] = useState(params.get("doctorId") || "");
  const [tokenInput, setTokenInput] = useState(params.get("tokenNo") || "");
  const [tokenNo, setTokenNo] = useState<number | undefined>(
    params.get("tokenNo") ? Number(params.get("tokenNo")) : undefined,
  );
  const [status, setStatus] = useState<QueueStatus | null>(null);
  const [rows, setRows] = useState<Appointment[]>([]);
  const [chatRow, setChatRow] = useState<Appointment | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const hospitalLoggedIn = isLoggedIn() && !session.isPatient();
  const patientLoggedIn = session.isPatient();
  const senderType: CareChatRole = patientLoggedIn
    ? "PATIENT"
    : session.isDoctor()
      ? "DOCTOR"
      : "HOSPITAL";
  const senderName =
    session.getUsername() ||
    session.getPatientName() ||
    (patientLoggedIn ? "Patient" : session.isDoctor() ? "Doctor" : "Hospital");

  useEffect(() => {
    api
      .hospitals()
      .then((d) => setHospitals(d.hospitals || []))
      .catch(() => setHospitals([]));
  }, []);

  useEffect(() => {
    if (!hospitalId) {
      setDoctors([]);
      return;
    }
    api
      .doctorsByHospital(hospitalId)
      .then((d) => setDoctors(d.doctors || []))
      .catch(() => setDoctors([]));
  }, [hospitalId]);

  const load = useCallback(async () => {
    if (!hospitalId || !doctorId) {
      setStatus(null);
      setRows([]);
      return;
    }
    setLoading(true);
    setError("");
    try {
      const data =
        tokenNo != null && !Number.isNaN(tokenNo)
          ? await api.queueStatus(hospitalId, doctorId, tokenNo)
          : await api.queueBoard(hospitalId, doctorId);
      setStatus(data);
      try {
        const booked = await api.appointments({
          hospitalId: Number(hospitalId),
          doctorId,
        });
        setRows(booked.appointments || []);
      } catch {
        setRows([]);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : t("Failed to load queue"));
    } finally {
      setLoading(false);
    }
  }, [hospitalId, doctorId, tokenNo, t]);

  useEffect(() => {
    load();
    if (!hospitalId || !doctorId) return;
    const id = window.setInterval(load, 30_000);
    return () => window.clearInterval(id);
  }, [load, hospitalId, doctorId]);

  function onTrackToken(e: FormEvent) {
    e.preventDefault();
    const n = Number(tokenInput);
    if (!Number.isFinite(n) || n <= 0) {
      setError(t("Enter a valid token number"));
      return;
    }
    setTokenNo(n);
  }

  const chatAppointmentIds = rows.map((row) => row.id).filter(Boolean);
  const unread = useCareChatUnread(chatAppointmentIds, senderType);

  const selectedDoctor = doctors.find((d) => d.doctorId === doctorId);
  const progressPct = status?.progress?.pct ?? 0;
  const doctorName = status?.doctorName || selectedDoctor?.doctorName || doctorId;
  const queueTableRows =
    rows.length > 0
      ? rows.map((row) => ({
          key: row.id,
          appointmentId: row.id,
          token: row.tokenNumber,
          patientName: row.patientName,
          phone: row.phoneNumber || row.patientPhone || "—",
          age: row.patientAge ?? "—",
          gender: row.gender || "—",
          doctorName: row.doctorName || doctorName,
          department: row.department || status?.department || selectedDoctor?.department || "—",
          status: row.status,
          date: formatApptDate(row.appointmentDate as string),
          time: formatApptTime(row.appointmentTime),
          paymentStatus: row.paymentStatus || "—",
        }))
      : (status?.waitingPatients || []).map((p) => ({
          key: `token-${p.token}`,
          appointmentId: undefined as string | undefined,
          token: p.token,
          patientName: p.patient,
          phone: "—",
          age: "—" as string | number,
          gender: "—",
          doctorName,
          department: status?.department || selectedDoctor?.department || "—",
          status: p.status,
          date: formatApptDate(status?.queueDate),
          time: "—",
          paymentStatus: "—",
        }));

  return (
    <section className="panel patient-queue-page" aria-label={t("Doctor Consultation Queue")}>
      <header className="patient-queue-head">
        <div>
          <p className="patient-booking-brand">MedTrack Clinic</p>
          <h1>{t("Doctor Consultation Queue")}</h1>
          <p className="lead" style={{ marginBottom: 0 }}>
            {t("Select a hospital and doctor to view today’s patient queue.")}
          </p>
        </div>
      </header>

      <div className="patient-queue-filters grid-2">
        <label>
          {t("Hospital")}
          <select
            value={hospitalId}
            onChange={(e) => {
              setHospitalId(e.target.value);
              setDoctorId("");
              setStatus(null);
              setRows([]);
            }}
          >
            <option value="">{t("Select hospital")}</option>
            {hospitals.map((h) => (
              <option key={h.id ?? h.hospitalId} value={String(h.id ?? h.hospitalId)}>
                {h.hospitalName} (ID {h.id ?? h.hospitalId})
              </option>
            ))}
          </select>
        </label>
        <label>
          {t("Doctor")}
          <select
            value={doctorId}
            onChange={(e) => {
              setDoctorId(e.target.value);
              setStatus(null);
              setRows([]);
            }}
            disabled={!hospitalId}
          >
            <option value="">{t("Select doctor")}</option>
            {doctors.map((d) => (
              <option key={d.doctorId} value={d.doctorId}>
                {d.doctorName}
                {d.department ? ` — ${d.department}` : ""}
              </option>
            ))}
          </select>
        </label>
      </div>

      {error && <div className="msg error">{error}</div>}

      {!doctorId && hospitalId && (
        <p className="muted">{t("Select a doctor to load patient queue details.")}</p>
      )}

      {hospitalId && doctorId && (
        <>
          <div className="patient-queue-meta">
            <div>
              <strong>{t("Doctor")}</strong>
              <span>{status?.doctorName || selectedDoctor?.doctorName || doctorId}</span>
            </div>
            <div>
              <strong>{t("Department")}</strong>
              <span>
                {status?.department || selectedDoctor?.department || t("General Medicine")}
              </span>
            </div>
            <div>
              <strong>{t("Queue date (today)")}</strong>
              <span>{status?.queueDate || "—"}</span>
            </div>
            <div>
              <strong>{t("Current time")}</strong>
              <span>{status?.currentTime || "—"}</span>
            </div>
            <div>
              <strong>{t("Doctor available")}</strong>
              <span>
                {status ? (status.doctorAvailable ? t("Yes") : t("Busy / leave")) : "—"}
              </span>
            </div>
            <div>
              <strong>{t("Patients waiting")}</strong>
              <span>{loading && !status ? "…" : (status?.waitingCount ?? 0)}</span>
            </div>
          </div>

          <div className="patient-queue-stats">
            <div className="patient-queue-stat">
              <p className="patient-queue-stat-label">{t("Patients waiting")}</p>
              <p className="patient-queue-stat-value">
                {loading && !status ? "…" : (status?.waitingCount ?? 0)}
              </p>
            </div>
            <div className="patient-queue-stat patient-queue-stat--current">
              <p className="patient-queue-stat-label">{t("Currently consulting")}</p>
              {status?.currentRunning ? (
                <>
                  <p className="patient-queue-stat-value">
                    {t("Token")} {status.currentRunning.token}
                  </p>
                  <p className="patient-queue-stat-sub">
                    {status.currentRunning.patient} · {status.currentRunning.status}
                  </p>
                </>
              ) : (
                <p className="patient-queue-stat-value patient-queue-stat-value--muted">
                  {t("None")}
                </p>
              )}
            </div>
          </div>

          <div className="patient-queue-progress-wrap">
            <div className="patient-queue-progress-labels">
              <span>{t("Queue progress")}</span>
              <span>
                {status?.progress?.completed ?? 0}/{status?.progress?.total ?? 0}{" "}
                {t("completed")}
              </span>
            </div>
            <div className="patient-queue-progress" aria-hidden="true">
              <div
                className="patient-queue-progress-fill"
                style={{ width: `${progressPct}%` }}
              />
            </div>
          </div>

          <form className="row patient-queue-token-form" onSubmit={onTrackToken}>
            <label style={{ flex: 1 }}>
              {t("Your token number (optional)")}
              <input
                type="number"
                min={1}
                value={tokenInput}
                onChange={(e) => setTokenInput(e.target.value)}
                placeholder="e.g. 15"
              />
            </label>
            <button type="submit" disabled={!tokenInput}>
              {t("Track my token")}
            </button>
          </form>

          {status?.yourStatus && (
            <div className="patient-queue-section">
              <h2>{t("Your Queue Status")}</h2>
              <dl className="patient-queue-dl">
                <div>
                  <dt>{t("Your Token No")}</dt>
                  <dd>{status.yourStatus.token}</dd>
                </div>
                <div>
                  <dt>{t("Patients Before You")}</dt>
                  <dd>
                    {status.yourStatus.patientsBeforeDisplay ||
                      (status.yourStatus.patientsBeforeTokens?.length
                        ? status.yourStatus.patientsBeforeTokens.join(", ")
                        : status.yourStatus.patientsBefore === 0
                          ? t("None")
                          : String(status.yourStatus.patientsBefore))}
                  </dd>
                </div>
                <div>
                  <dt>{t("Average Time Per Patient")}</dt>
                  <dd>
                    {status.yourStatus.avgMinutes} {t("Minutes")}
                  </dd>
                </div>
                <div>
                  <dt>{t("Estimated Wait Time")}</dt>
                  <dd>
                    {status.yourStatus.waitMinutes} {t("Minutes")}
                  </dd>
                </div>
                <div>
                  <dt>{t("Expected Turn Time")}</dt>
                  <dd>{status.yourStatus.expectedTurnTime}</dd>
                </div>
              </dl>
            </div>
          )}

          <div className="patient-queue-section">
            <h2>{t("Patient details")}</h2>
            <div className="table-wrap patient-queue-table-wrap">
              <table className="table patient-queue-table">
                <thead>
                  <tr>
                    <th>{t("Token")}</th>
                    <th>{t("Patient name")}</th>
                    <th>{t("Phone")}</th>
                    <th>{t("Age")}</th>
                    <th>{t("Gender")}</th>
                    <th>{t("Department")}</th>
                    <th>{t("Doctor")}</th>
                    <th>{t("Date")}</th>
                    <th>{t("Time")}</th>
                    <th>{t("Status")}</th>
                    <th>{t("Payment")}</th>
                    {(hospitalLoggedIn || patientLoggedIn) && <th>{t("Chat")}</th>}
                  </tr>
                </thead>
                <tbody>
                  {queueTableRows.length === 0 ? (
                    <tr>
                      <td
                        colSpan={hospitalLoggedIn || patientLoggedIn ? 12 : 11}
                        className="muted"
                      >
                        {loading
                          ? t("Loading…")
                          : t("No patients in this queue yet. Select another doctor or date.")}
                      </td>
                    </tr>
                  ) : (
                    queueTableRows.map((row) => (
                      <tr key={row.key}>
                        <td>{row.token ?? "—"}</td>
                        <td>{row.patientName || "—"}</td>
                        <td>{row.phone}</td>
                        <td>{row.age}</td>
                        <td>{row.gender}</td>
                        <td>{row.department}</td>
                        <td>{row.doctorName}</td>
                        <td>{row.date}</td>
                        <td>{row.time}</td>
                        <td>
                          <span className={`badge ${String(row.status || "").toUpperCase()}`}>
                            {row.status || "—"}
                          </span>
                        </td>
                        <td>{row.paymentStatus}</td>
                        {(hospitalLoggedIn || patientLoggedIn) && (
                          <td>
                            {row.appointmentId ? (
                              <ChatLink
                                unread={unread[row.appointmentId] ?? 0}
                                onClick={() =>
                                  setChatRow({
                                    id: row.appointmentId as string,
                                    doctorId,
                                    patientId: "",
                                    patientName: row.patientName,
                                    appointmentDate: row.date || "",
                                    appointmentTime: "",
                                    status: row.status,
                                    createdDate: "",
                                  })
                                }
                              />
                            ) : (
                              <Link
                                className="chat-link"
                                to={patientLoggedIn ? "/patient/chat" : "/patient/login"}
                              >
                                {t("Chat")}
                              </Link>
                            )}
                          </td>
                        )}
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>

          <p className="lead" style={{ marginBottom: 0, fontSize: "0.85rem" }}>
            {t("Auto-refreshes every 30 seconds.")}
            {loading ? ` ${t("Updating…")}` : ""}
          </p>
        </>
      )}

      {chatRow && (
        <CareChatWindow
          appointmentId={chatRow.id}
          title={`${chatRow.patientName} · ${chatRow.doctorName || doctorName}`}
          subtitle={`${t("Token")} ${chatRow.tokenNumber ?? "—"} · chat with hospital and doctor`}
          senderType={senderType}
          senderName={senderName}
          phone={patientLoggedIn ? session.getPatientPhone() : undefined}
          onClose={() => setChatRow(null)}
        />
      )}
    </section>
  );
}
