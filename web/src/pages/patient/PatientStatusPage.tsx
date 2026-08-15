import { useCallback, useEffect, useState } from "react";
import {
  api,
  BookingSummary,
  LiveQueueStatus,
  PatientQueueStatusResponse,
} from "../../api";
import PatientBookingDetails from "../../components/PatientBookingDetails";
import PatientQueueStatusView from "../../components/PatientQueueStatusView";
import { getPatientId, getPatientPhone } from "../../auth";

function localTodayIso() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
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

function statusClass(status?: string) {
  return `patient-appt-status patient-appt-status--${(status || "unknown").toLowerCase()}`;
}

function deriveLiveStatus(
  queue: PatientQueueStatusResponse,
): LiveQueueStatus | undefined {
  if (queue.liveQueueStatus) return queue.liveQueueStatus;
  const token = queue.yourStatus?.token ?? queue.booking?.tokenNumber;
  if (token == null) return "BOOKED";
  const running = queue.currentRunning?.token;
  if (running != null && running === token) return "IN_CONSULTATION";
  const before = queue.yourStatus?.patientsBefore;
  if (before === 0) return "YOU_ARE_NEXT";
  if (before != null && before > 0) return "WAITING";
  return "BOOKED";
}

async function loadQueueForAppointment(
  appt: BookingSummary,
): Promise<PatientQueueStatusResponse | null> {
  if (!appt.hospitalId || !appt.doctorId) return null;
  try {
    const q = await api.queueStatus(
      appt.hospitalId,
      appt.doctorId,
      appt.tokenNumber,
    );
    return {
      ...q,
      booking: appt,
      patientId: appt.patientId,
      liveQueueStatus: deriveLiveStatus({ ...q, booking: appt }),
    };
  } catch {
    return null;
  }
}

export default function PatientStatusPage() {
  const phone = getPatientPhone();
  const patientId = getPatientId();
  const [appointments, setAppointments] = useState<BookingSummary[]>([]);
  const [resolvedPatientId, setResolvedPatientId] = useState(patientId);
  const [status, setStatus] = useState<PatientQueueStatusResponse | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!phone && !patientId) return;
    setLoading(true);
    setError("");
    try {
      // Prefer User ID (USR…) so appointments.patient_id matches even without patients-table row
      const bookingsRes = await api.patientBookings(phone || "", patientId || undefined);
      setAppointments(bookingsRes.upcoming);
      if (bookingsRes.patientId) {
        setResolvedPatientId(bookingsRes.patientId);
      }

      const today = localTodayIso();
      const todayAppt = bookingsRes.upcoming.find((a) => a.appointmentDate === today);

      let queueRes: PatientQueueStatusResponse | null = null;
      try {
        queueRes = await api.patientQueueStatus(phone || "", patientId || undefined);
      } catch {
        // Fall through — load live board by hospitalId + doctorId + token from today's booking
      }

      if (!queueRes && todayAppt) {
        queueRes = await loadQueueForAppointment(todayAppt);
      }

      setStatus(queueRes);

      if (!queueRes && bookingsRes.upcoming.length === 0) {
        setError("No upcoming appointments found for this User ID.");
      }
    } catch (e) {
      setAppointments([]);
      setStatus(null);
      setError(e instanceof Error ? e.message : "Failed to load appointment status");
    } finally {
      setLoading(false);
    }
  }, [phone, patientId]);

  useEffect(() => {
    load();
    const id = window.setInterval(load, 30_000);
    return () => window.clearInterval(id);
  }, [load]);

  useEffect(() => {
    const onFocus = () => load();
    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, [load]);

  const today = localTodayIso();
  const todayAppointment =
    appointments.find((a) => a.appointmentDate === today) || status?.booking;

  return (
    <div className="patient-portal-stack">
      {resolvedPatientId && (
        <p className="lead patient-note">
          Patient ID (User ID): <strong>{resolvedPatientId}</strong>
        </p>
      )}

      <section className="panel">
        <h2>All upcoming bookings ({appointments.length})</h2>
        {loading && appointments.length === 0 && !status && (
          <p className="lead">Loading…</p>
        )}
        {appointments.length === 0 && !loading ? (
          <p className="lead">No upcoming appointments.</p>
        ) : (
          <ul className="patient-appointment-list">
            {appointments.map((appt) => {
              const isToday = appt.appointmentDate === today;
              const isActive = status?.booking?.id === appt.id;
              return (
                <li
                  key={appt.id}
                  className={`patient-appointment-item${isToday ? " patient-appointment-item--today" : ""}${isActive ? " patient-appointment-item--active" : ""}`}
                >
                  <div className="patient-booking-card">
                    <div className="patient-appt-status-row">
                      <strong>{appt.doctorName || appt.doctorId}</strong>
                      <span className={statusClass(appt.appointmentStatus || appt.status)}>
                        {appointmentStatusLabel(appt.appointmentStatus || appt.status)}
                      </span>
                      {appt.tokenNumber != null && (
                        <span className="patient-appt-token-tag">
                          Token # {appt.tokenNumber}
                        </span>
                      )}
                      {isToday && (
                        <span className="patient-appt-today-tag">Today</span>
                      )}
                    </div>
                    <PatientBookingDetails appt={appt} compactHeader />
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {error && !status && appointments.length === 0 && (
        <div className="msg error">{error}</div>
      )}

      {status && (
        <>
          {todayAppointment && (
            <p className="lead patient-note">
              Live queue for <strong>{todayAppointment.doctorName || todayAppointment.doctorId}</strong>
              {todayAppointment.tokenNumber != null
                ? ` · Your token # ${todayAppointment.tokenNumber}`
                : ""}
            </p>
          )}
          <PatientQueueStatusView
            status={status}
            loading={loading}
            phoneLabel={phone}
            portalMode
          />
        </>
      )}

      {!status && todayAppointment && (
        <section className="panel">
          <h2>Today&apos;s appointment details</h2>
          <PatientBookingDetails appt={todayAppointment} />
        </section>
      )}
    </div>
  );
}
