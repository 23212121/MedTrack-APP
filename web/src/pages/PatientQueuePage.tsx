import { FormEvent, useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  api,
  HospitalDoctor,
  HospitalRegistrationSummary,
  QueueStatus,
} from "../api";
import { getHospitalId, isLoggedIn } from "../auth";

export default function PatientQueuePage() {
  const [params] = useSearchParams();
  const sessionHospital = isLoggedIn() ? getHospitalId() : "";
  const [hospitals, setHospitals] = useState<HospitalRegistrationSummary[]>([]);
  const [doctors, setDoctors] = useState<HospitalDoctor[]>([]);
  const [hospitalId, setHospitalId] = useState(
    params.get("hospitalId") || sessionHospital || ""
  );
  const [doctorId, setDoctorId] = useState(params.get("doctorId") || "");
  const [tokenInput, setTokenInput] = useState(params.get("tokenNo") || "");
  const [tokenNo, setTokenNo] = useState<number | undefined>(
    params.get("tokenNo") ? Number(params.get("tokenNo")) : undefined
  );
  const [status, setStatus] = useState<QueueStatus | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

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
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load queue");
    } finally {
      setLoading(false);
    }
  }, [hospitalId, doctorId, tokenNo]);

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
      setError("Enter a valid token number");
      return;
    }
    setTokenNo(n);
  }

  const selectedDoctor = doctors.find((d) => d.doctorId === doctorId);
  const progressPct = status?.progress?.pct ?? 0;

  return (
    <div className="patient-queue-backdrop">
      <section className="patient-queue-modal panel">
        <header className="patient-queue-head">
          <div>
            <p className="patient-booking-brand">MedTrack Clinic</p>
            <h1>Doctor Consultation Queue</h1>
            <p className="lead patient-booking-lead">
              Live today&apos;s queue from the hospital database — waiting count
              and who is with the doctor now.
            </p>
          </div>
          <Link to="/login" className="patient-booking-signin">
            Staff sign in
          </Link>
        </header>

        <div className="stack patient-queue-filters">
          <label>
            Hospital
            <select
              value={hospitalId}
              onChange={(e) => {
                setHospitalId(e.target.value);
                setDoctorId("");
                setStatus(null);
              }}
            >
              <option value="">Select hospital</option>
              {hospitals.map((h) => (
                <option key={h.id ?? h.hospitalId} value={String(h.id ?? h.hospitalId)}>
                  {h.hospitalName} (ID {h.id ?? h.hospitalId})
                </option>
              ))}
            </select>
          </label>
          <label>
            Doctor
            <select
              value={doctorId}
              onChange={(e) => {
                setDoctorId(e.target.value);
                setStatus(null);
              }}
              disabled={!hospitalId}
            >
              <option value="">Select doctor</option>
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

        {hospitalId && doctorId && (
          <>
            <div className="patient-queue-meta">
              <div>
                <strong>Doctor</strong>
                <span>
                  {status?.doctorName || selectedDoctor?.doctorName || doctorId}
                </span>
              </div>
              <div>
                <strong>Department</strong>
                <span>
                  {status?.department ||
                    selectedDoctor?.department ||
                    "General Medicine"}
                </span>
              </div>
              <div>
                <strong>Queue date (today)</strong>
                <span>{status?.queueDate || "—"}</span>
              </div>
              <div>
                <strong>Current time</strong>
                <span>{status?.currentTime || "—"}</span>
              </div>
              <div>
                <strong>Doctor available</strong>
                <span>
                  {status
                    ? status.doctorAvailable
                      ? "Yes"
                      : "Busy / leave"
                    : "—"}
                </span>
              </div>
              <div>
                <strong>Data source</strong>
                <span>Database · today only</span>
              </div>
            </div>

            <div className="patient-queue-stats">
              <div className="patient-queue-stat">
                <p className="patient-queue-stat-label">Patients waiting</p>
                <p className="patient-queue-stat-value">
                  {loading && !status ? "…" : status?.waitingCount ?? 0}
                </p>
              </div>
              <div className="patient-queue-stat patient-queue-stat--current">
                <p className="patient-queue-stat-label">Currently consulting</p>
                {status?.currentRunning ? (
                  <>
                    <p className="patient-queue-stat-value">
                      Token {status.currentRunning.token}
                    </p>
                    <p className="patient-queue-stat-sub">
                      {status.currentRunning.patient} · {status.currentRunning.status}
                    </p>
                  </>
                ) : (
                  <p className="patient-queue-stat-value patient-queue-stat-value--muted">
                    None
                  </p>
                )}
              </div>
            </div>

            <div className="patient-queue-progress-wrap">
              <div className="patient-queue-progress-labels">
                <span>Queue progress</span>
                <span>
                  {status?.progress?.completed ?? 0}/{status?.progress?.total ?? 0}{" "}
                  completed
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
                Your token number (optional)
                <input
                  type="number"
                  min={1}
                  value={tokenInput}
                  onChange={(e) => setTokenInput(e.target.value)}
                  placeholder="e.g. 15"
                />
              </label>
              <button type="submit" disabled={!tokenInput}>
                Track my token
              </button>
            </form>

            {status?.yourStatus && (
              <div className="patient-queue-section">
                <h2>Your Queue Status</h2>
                <dl className="patient-queue-dl">
                  <div>
                    <dt>Your Token No</dt>
                    <dd>{status.yourStatus.token}</dd>
                  </div>
                  <div>
                    <dt>Patients Before You</dt>
                    <dd>
                      {status.yourStatus.patientsBeforeDisplay ||
                        (status.yourStatus.patientsBeforeTokens?.length
                          ? status.yourStatus.patientsBeforeTokens.join(", ")
                          : status.yourStatus.patientsBefore === 0
                            ? "None"
                            : String(status.yourStatus.patientsBefore))}
                    </dd>
                  </div>
                  <div>
                    <dt>Average Time Per Patient</dt>
                    <dd>{status.yourStatus.avgMinutes} Minutes</dd>
                  </div>
                  <div>
                    <dt>Estimated Wait Time</dt>
                    <dd>{status.yourStatus.waitMinutes} Minutes</dd>
                  </div>
                  <div>
                    <dt>Expected Turn Time</dt>
                    <dd>{status.yourStatus.expectedTurnTime}</dd>
                  </div>
                </dl>
              </div>
            )}

            {status?.waitingBeforeYou && status.waitingBeforeYou.length > 0 && (
              <div className="patient-queue-section">
                <h2>Patients Waiting Before You</h2>
                <ul className="patient-queue-list">
                  {status.waitingBeforeYou.map((p) => (
                    <li key={p.token}>
                      Token {p.token} — {p.status}
                      {p.patient ? ` (${p.patient})` : ""}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {status?.upcoming && (
              <div className="patient-queue-section">
                <h2>Upcoming Queue</h2>
                <ul className="patient-queue-list">
                  <li>
                    Current : {status.upcoming.current ?? "—"}
                  </li>
                  <li>Next : {status.upcoming.next ?? "—"}</li>
                  <li>Then : {status.upcoming.then ?? "—"}</li>
                </ul>
              </div>
            )}

            {status?.waitingPatients && status.waitingPatients.length > 0 && (
              <div className="patient-queue-section">
                <h2>Waiting list</h2>
                <ul className="patient-queue-list">
                  {status.waitingPatients.map((p) => (
                    <li key={p.token}>
                      Token {p.token} — {p.patient} ({p.status})
                    </li>
                  ))}
                </ul>
              </div>
            )}

            <p className="lead" style={{ marginBottom: 0, fontSize: "0.85rem" }}>
              Auto-refreshes every 30 seconds.
              {loading ? " Updating…" : ""}
            </p>
          </>
        )}
      </section>
    </div>
  );
}
