import { LiveQueueStatus, PatientQueueStatusResponse } from "../api";

function formatDateTime(iso?: string) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString(undefined, {
      dateStyle: "medium",
      timeStyle: "short",
    });
  } catch {
    return iso;
  }
}

function formatTime(iso?: string) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleTimeString(undefined, { timeStyle: "short" });
  } catch {
    return iso;
  }
}

function deriveLiveStatus(
  status: PatientQueueStatusResponse,
): LiveQueueStatus {
  if (status.liveQueueStatus) {
    return status.liveQueueStatus;
  }
  const token = status.yourStatus?.token ?? status.booking?.tokenNumber;
  if (token == null) return "BOOKED";
  const running = status.currentRunning?.token;
  if (running != null && running === token) return "IN_CONSULTATION";
  const before = status.yourStatus?.patientsBefore;
  if (before === 0) return "YOU_ARE_NEXT";
  if (before != null && before > 0) return "WAITING";
  return "BOOKED";
}

function liveStatusLabel(live: LiveQueueStatus) {
  switch (live) {
    case "IN_CONSULTATION":
      return "In consultation now";
    case "YOU_ARE_NEXT":
      return "You are next";
    case "WAITING":
      return "Waiting in queue";
    default:
      return "Appointment booked";
  }
}

function appointmentStatusLabel(status?: string) {
  switch ((status || "").toUpperCase()) {
    case "BOOKED":
      return "Booked";
    case "CHECKED_IN":
      return "Checked in";
    case "CANCELLED":
      return "Cancelled";
    case "COMPLETED":
      return "Completed";
    default:
      return status || "—";
  }
}

function bookedByDisplay(booking?: PatientQueueStatusResponse["booking"]) {
  if (!booking) return "—";
  if (booking.bookedByLabel) return booking.bookedByLabel;
  if (booking.bookedBy === "HOSPITAL") {
    return `Hospital reception · ${booking.hospitalName || "—"}`;
  }
  if (booking.bookedBy === "PATIENT") {
    return "Self (patient portal)";
  }
  return booking.bookedBy || "—";
}

type PatientQueueStatusViewProps = {
  status: PatientQueueStatusResponse;
  loading?: boolean;
  phoneLabel?: string;
  portalMode?: boolean;
};

export default function PatientQueueStatusView({
  status,
  loading = false,
  phoneLabel,
  portalMode = false,
}: PatientQueueStatusViewProps) {
  const booking = status.booking;
  const yours = status.yourStatus;
  const summary = status.queueSummary;
  const yourToken = summary?.yourToken ?? yours?.token ?? booking?.tokenNumber ?? null;
  const patientsBefore =
    summary?.patientsBefore ?? yours?.patientsBefore ?? null;
  const totalPatients =
    summary?.totalTokens ??
    summary?.totalPatients ??
    (status as { totalTokens?: number }).totalTokens ??
    status.progress?.total ??
    0;
  const waitingCount = summary?.waitingCount ?? status.waitingCount ?? 0;
  const upcomingPatientCount =
    summary?.upcomingPatientCount ??
    (status as { upcomingPatientCount?: number }).upcomingPatientCount ??
    status.waitingPatients?.length ??
    0;
  const currentRunningToken =
    summary?.currentRunningToken ??
    yours?.currentRunningToken ??
    status.currentRunning?.token ??
    status.upcoming?.current ??
    null;
  const currentRunningPatient =
    summary?.currentRunningPatient ?? status.currentRunning?.patient ?? null;
  const currentRunningStatus =
    summary?.currentRunningStatus ??
    status.currentRunning?.status ??
    (currentRunningToken != null ? "In Consultation" : null);
  const currentConsultation =
    summary?.currentConsultation ??
    (currentRunningToken != null
      ? {
          token: currentRunningToken,
          patient: currentRunningPatient || "Patient",
          status: currentRunningStatus || "In Consultation",
        }
      : null);
  const patientsAhead =
    summary?.patientsAhead?.length
      ? summary.patientsAhead
      : status.waitingBeforeYou ?? [];
  const effectivePatientsBefore =
    patientsBefore ??
    yours?.patientsBefore ??
    (yourToken != null &&
    currentRunningToken != null &&
    yourToken > currentRunningToken
      ? yourToken - currentRunningToken - 1
      : patientsAhead.length > 0
        ? patientsAhead.length
        : null);
  const liveStatus = deriveLiveStatus(status);
  const progressPct = status.progress?.pct ?? 0;
  const patientsBeforeTokens =
    yours?.patientsBeforeTokens?.length
      ? yours.patientsBeforeTokens.join(", ")
      : yours?.patientsBeforeDisplay ?? "None";

  return (
    <>
      <section className="panel patient-queue-section">
        <div className="patient-live-status-header">
          <div>
            <p className="patient-queue-stat-label">Current status</p>
            <p className={`patient-live-status patient-live-status--${liveStatus.toLowerCase()}`}>
              {liveStatusLabel(liveStatus)}
            </p>
            {portalMode && booking?.status && (
              <p className="patient-appt-status-inline">
                Appointment:{" "}
                <span className={`patient-appt-status patient-appt-status--${(booking.status || "").toLowerCase()}`}>
                  {appointmentStatusLabel(booking.appointmentStatus || booking.status)}
                </span>
              </p>
            )}
          </div>
          {yourToken != null && (
            <div className="patient-token-badge">
              <p className="patient-queue-stat-label">Your appointment #</p>
              <p className="patient-token-badge-value">{yourToken}</p>
            </div>
          )}
        </div>

        <div className="patient-queue-stats patient-queue-stats--wide">
          <div className="patient-queue-stat">
            <p className="patient-queue-stat-label">Total tokens today</p>
            <p className="patient-queue-stat-value">{totalPatients}</p>
          </div>
          <div className="patient-queue-stat">
            <p className="patient-queue-stat-label">Your token</p>
            <p className="patient-queue-stat-value">{yourToken ?? "—"}</p>
          </div>
          <div className="patient-queue-stat">
            <p className="patient-queue-stat-label">Patients before you</p>
            <p className="patient-queue-stat-value">
              {effectivePatientsBefore != null ? effectivePatientsBefore : "—"}
            </p>
          </div>
          <div className="patient-queue-stat">
            <p className="patient-queue-stat-label">Upcoming after you</p>
            <p className="patient-queue-stat-value">{upcomingPatientCount}</p>
          </div>
          <div className="patient-queue-stat">
            <p className="patient-queue-stat-label">Patients waiting</p>
            <p className="patient-queue-stat-value">{waitingCount}</p>
          </div>
          <div className="patient-queue-stat patient-queue-stat--current">
            <p className="patient-queue-stat-label">Current patient</p>
            {currentRunningToken != null ? (
              <>
                <p className="patient-queue-stat-value">Token {currentRunningToken}</p>
                {currentRunningPatient && (
                  <p className="patient-queue-stat-sub">{currentRunningPatient}</p>
                )}
              </>
            ) : (
              <p className="patient-queue-stat-value patient-queue-stat-value--muted">None</p>
            )}
          </div>
        </div>
      </section>

      {currentConsultation && (
        <section className="panel patient-queue-section patient-consult-now">
          <h2>Patient currently with doctor</h2>
          <div className="patient-status-grid">
            <div className="patient-status-card patient-status-card--highlight">
              <p className="patient-queue-stat-label">Token number</p>
              <p className="patient-queue-stat-value">{currentConsultation.token}</p>
            </div>
            <div className="patient-status-card">
              <p className="patient-queue-stat-label">Patient name</p>
              <p className="patient-queue-stat-value patient-queue-stat-value--name">
                {currentConsultation.patient}
              </p>
            </div>
            <div className="patient-status-card">
              <p className="patient-queue-stat-label">Status</p>
              <p className="patient-queue-stat-value">{currentConsultation.status}</p>
            </div>
          </div>
        </section>
      )}

      <section className="panel">
        <h2>Appointment details</h2>
        <div className="patient-queue-meta">
          <div>
            <strong>Token number</strong>
            <span>{yourToken ?? "Not assigned yet"}</span>
          </div>
          {phoneLabel && (
            <div>
              <strong>Mobile</strong>
              <span>{phoneLabel}</span>
            </div>
          )}
          <div>
            <strong>Doctor</strong>
            <span>{status.doctorName || summary?.doctorName || booking?.doctorName || "—"}</span>
          </div>
          <div>
            <strong>Department</strong>
            <span>{status.department || summary?.department || "—"}</span>
          </div>
          <div>
            <strong>Hospital</strong>
            <span>{booking?.hospitalName || "—"}</span>
          </div>
          <div>
            <strong>Booked by</strong>
            <span>{bookedByDisplay(booking)}</span>
          </div>
          {(booking?.createdByName || booking?.createdBy) && (
            <div>
              <strong>Created by</strong>
              <span>{booking.createdByName || booking.createdBy}</span>
            </div>
          )}
          <div>
            <strong>Appointment status</strong>
            <span className={`patient-appt-status patient-appt-status--${(booking?.status || "").toLowerCase()}`}>
              {appointmentStatusLabel(booking?.appointmentStatus || booking?.status)}
            </span>
          </div>
          <div>
            <strong>Appointment date</strong>
            <span>{booking?.appointmentDate || status.queueDate}</span>
          </div>
          <div>
            <strong>Scheduled time</strong>
            <span>{formatTime(booking?.appointmentTime)}</span>
          </div>
          {(status.patientId || booking?.patientId) && (
            <div>
              <strong>Patient ID (User ID)</strong>
              <span>{status.patientId || booking?.patientId}</span>
            </div>
          )}
          <div>
            <strong>Doctor available</strong>
            <span>{status.doctorAvailable ? "Yes" : "Busy / leave"}</span>
          </div>
          <div>
            <strong>Queue date (today)</strong>
            <span>{status.queueDate}</span>
          </div>
          <div>
            <strong>Current time</strong>
            <span>{status.currentTime}</span>
          </div>
          {booking?.reason && (
            <div>
              <strong>Reason</strong>
              <span>{booking.reason}</span>
            </div>
          )}
          {booking?.consultationFee != null && (
            <div>
              <strong>Consultation fee</strong>
              <span>
                {booking.currency ?? "INR"} {booking.consultationFee}
              </span>
            </div>
          )}
        </div>
      </section>

      {(yours || yourToken != null) && (
        <section className="panel patient-queue-section">
          <h2>Your queue position</h2>
          {yours ? (
            <div className="patient-status-grid">
              <div className="patient-status-card patient-status-card--highlight">
                <p className="patient-queue-stat-label">Your appointment number</p>
                <p className="patient-queue-stat-value">{yours.token}</p>
              </div>
              <div className="patient-status-card">
                <p className="patient-queue-stat-label">Current running token</p>
                <p className="patient-queue-stat-value">{currentRunningToken ?? "—"}</p>
              </div>
              <div className="patient-status-card">
                <p className="patient-queue-stat-label">Patients before you</p>
                <p className="patient-queue-stat-value">
                  {yours.patientsBefore ?? effectivePatientsBefore ?? 0}
                </p>
                <p className="patient-queue-stat-sub">Tokens: {patientsBeforeTokens}</p>
              </div>
              <div className="patient-status-card">
                <p className="patient-queue-stat-label">Total in queue today</p>
                <p className="patient-queue-stat-value">{totalPatients}</p>
              </div>
              <div className="patient-status-card">
                <p className="patient-queue-stat-label">Avg. consultation time</p>
                <p className="patient-queue-stat-value">{yours.avgMinutes} min/patient</p>
              </div>
              <div className="patient-status-card patient-status-card--highlight">
                <p className="patient-queue-stat-label">Estimated waiting time</p>
                <p className="patient-queue-stat-value">{yours.waitMinutes} minutes</p>
                <p className="patient-queue-stat-sub">
                  {yours.patientsBefore} × {yours.avgMinutes} = {yours.waitMinutes} min
                </p>
              </div>
              <div className="patient-status-card">
                <p className="patient-queue-stat-label">Expected consultation time</p>
                <p className="patient-queue-stat-value">{yours.expectedTurnTime}</p>
                <p className="patient-queue-stat-sub">
                  Scheduled: {formatDateTime(booking?.appointmentTime)}
                </p>
              </div>
            </div>
          ) : (
            <div className="patient-status-grid">
              <div className="patient-status-card patient-status-card--highlight">
                <p className="patient-queue-stat-label">Your appointment number</p>
                <p className="patient-queue-stat-value">{yourToken}</p>
              </div>
              <div className="patient-status-card">
                <p className="patient-queue-stat-label">Appointment status</p>
                <p className="patient-queue-stat-value">
                  {appointmentStatusLabel(booking?.appointmentStatus || booking?.status)}
                </p>
              </div>
              <div className="patient-status-card">
                <p className="patient-queue-stat-label">Patients before you</p>
                <p className="patient-queue-stat-value">
                  {effectivePatientsBefore ?? "—"}
                </p>
              </div>
              <p className="lead patient-note" style={{ gridColumn: "1 / -1" }}>
                Your token is assigned. Queue position updates when the doctor&apos;s live
                queue is active for today.
              </p>
            </div>
          )}
        </section>
      )}

      {patientsAhead.length > 0 && (
        <section className="panel patient-queue-section">
          <h2>Patients waiting before you</h2>
          <ul className="patient-queue-list">
            {patientsAhead.map((p) => (
              <li key={p.token}>
                Token {p.token} — {p.status}
                {p.patient ? ` · ${p.patient}` : ""}
              </li>
            ))}
          </ul>
        </section>
      )}

      {status.waitingPatients && status.waitingPatients.length > 0 && (
        <section className="panel patient-queue-section">
          <h2>Full waiting list today</h2>
          <ul className="patient-queue-list">
            {status.waitingPatients.map((p) => (
              <li key={p.token}>
                Token {p.token} — {p.patient} ({p.status})
              </li>
            ))}
          </ul>
        </section>
      )}

      {status.upcoming && (
        <section className="panel patient-queue-section">
          <h2>Upcoming tokens</h2>
          <ul className="patient-queue-list">
            <li>Current: {status.upcoming.current ?? "—"}</li>
            <li>Next: {status.upcoming.next ?? "—"}</li>
            <li>Then: {status.upcoming.then ?? "—"}</li>
          </ul>
        </section>
      )}

      <section className="panel">
        <div className="patient-queue-progress-wrap">
          <div className="patient-queue-progress-labels">
            <span>Queue progress</span>
            <span>
              {status.progress?.completed ?? 0}/{totalPatients} completed · {waitingCount} waiting
            </span>
          </div>
          <div className="patient-queue-progress" aria-hidden="true">
            <div
              className="patient-queue-progress-fill"
              style={{ width: `${progressPct}%` }}
            />
          </div>
        </div>
      </section>

      {status.lastCompletedTokens && status.lastCompletedTokens.length > 0 && (
        <section className="panel patient-queue-section">
          <h3>Last completed tokens</h3>
          <p>{status.lastCompletedTokens.join(", ")}</p>
        </section>
      )}

      {status.emergencyCases && status.emergencyCases.length > 0 && (
        <section className="panel patient-queue-section patient-emergency">
          <h3>Emergency cases</h3>
          <ul className="patient-queue-list">
            {status.emergencyCases.map((c) => (
              <li key={c.token}>
                Token {c.token} — {c.patient} ({c.status})
              </li>
            ))}
          </ul>
        </section>
      )}

      <p className="lead patient-note">
        Live queue for today under {status.doctorName}. Auto-refreshes every 30 seconds.
        {loading ? " Updating…" : ""}
      </p>
    </>
  );
}
