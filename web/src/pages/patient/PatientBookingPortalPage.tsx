import { FormEvent, useEffect, useState } from "react";
import { api, Booking } from "../../api";
import { useCareChatUnread } from "../../careChatUnread";
import CareChatWindow from "../../components/CareChatWindow";
import ChatLink from "../../components/ChatLink";
import { getPatientId, getPatientPhone } from "../../auth";
import { session } from "../../dl/MedTrackSession";
import BookingsPage from "../BookingsPage";
import { toast } from "../../toast";

function formatInstant(iso: string) {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

export default function PatientBookingPortalPage() {
  const phone = getPatientPhone();
  const [upcoming, setUpcoming] = useState<Booking[]>([]);
  const [error, setError] = useState("");
  const [chatBooking, setChatBooking] = useState<Booking | null>(null);
  const [rescheduleId, setRescheduleId] = useState<string | null>(null);
  const [rescheduleForm, setRescheduleForm] = useState({
    appointmentDate: "",
    appointmentTime: "",
    reason: "",
  });

  async function load() {
    if (!phone) return;
    try {
      const data = await api.patientBookings(phone, getPatientId() || undefined);
      setUpcoming(data.upcoming);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load bookings");
    }
  }

  useEffect(() => {
    load();
  }, [phone]);

  const unread = useCareChatUnread(
    upcoming.map((b) => b.id),
    "PATIENT",
  );

  async function onCancel(id: string) {
    if (!phone || !window.confirm("Cancel this appointment?")) return;
    setError("");
    try {
      await api.cancelBooking(id, phone);
      toast.success("Appointment cancelled");
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Cancel failed");
    }
  }

  async function onReschedule(e: FormEvent) {
    e.preventDefault();
    if (!phone || !rescheduleId) return;
    setError("");
    try {
      const instant = new Date(
        `${rescheduleForm.appointmentDate}T${rescheduleForm.appointmentTime}:00`,
      ).toISOString();
      await api.rescheduleBooking(rescheduleId, phone, {
        appointmentDate: rescheduleForm.appointmentDate,
        appointmentTime: instant,
        reason: rescheduleForm.reason || undefined,
      });
      toast.success("Appointment rescheduled");
      setRescheduleId(null);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Reschedule failed");
    }
  }

  return (
    <div className="patient-portal-stack">
      <section className="panel">
        <h2>Book a new appointment</h2>
        <BookingsPage
          publicMode
          onBooked={() => {
            void load();
          }}
        />
      </section>

      {rescheduleId && (
        <section className="panel">
          <h3>Reschedule appointment</h3>
          <form className="stack" onSubmit={onReschedule}>
            <label>
              Date
              <input
                type="date"
                value={rescheduleForm.appointmentDate}
                onChange={(e) =>
                  setRescheduleForm({ ...rescheduleForm, appointmentDate: e.target.value })
                }
                required
              />
            </label>
            <label>
              Time
              <input
                type="time"
                value={rescheduleForm.appointmentTime}
                onChange={(e) =>
                  setRescheduleForm({ ...rescheduleForm, appointmentTime: e.target.value })
                }
                required
              />
            </label>
            <label>
              Reason (optional)
              <input
                value={rescheduleForm.reason}
                onChange={(e) =>
                  setRescheduleForm({ ...rescheduleForm, reason: e.target.value })
                }
              />
            </label>
            <div className="row patient-appointment-actions">
              <button type="submit">Save</button>
              <button type="button" onClick={() => setRescheduleId(null)}>
                Close
              </button>
            </div>
          </form>
        </section>
      )}

      <section className="panel">
        <h2>Upcoming appointments</h2>
        {error && <div className="msg error">{error}</div>}
        {upcoming.length === 0 ? (
          <p className="lead">No upcoming appointments.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Token</th>
                  <th>Doctor</th>
                  <th>Date</th>
                  <th>Status</th>
                  <th>Chat</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {upcoming.map((b) => (
                  <tr key={b.id}>
                    <td>{b.tokenNumber ?? "—"}</td>
                    <td>{b.doctorName || b.doctorId}</td>
                    <td>
                      {b.appointmentDate} · {formatInstant(b.appointmentTime)}
                    </td>
                    <td>{b.status}</td>
                    <td>
                      <ChatLink unread={unread[b.id] ?? 0} onClick={() => setChatBooking(b)} />
                    </td>
                    <td className="row">
                      <button
                        type="button"
                        onClick={() => {
                          setRescheduleId(b.id);
                          setRescheduleForm({
                            appointmentDate: b.appointmentDate,
                            appointmentTime: new Date(b.appointmentTime)
                              .toISOString()
                              .slice(11, 16),
                            reason: b.reason || "",
                          });
                        }}
                      >
                        Reschedule
                      </button>
                      <button type="button" className="btn-danger" onClick={() => onCancel(b.id)}>
                        Cancel
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      {chatBooking && phone && (
        <CareChatWindow
          appointmentId={chatBooking.id}
          title={`${chatBooking.hospitalName || "Hospital"} · ${chatBooking.doctorName || "Doctor"}`}
          subtitle={`Token ${chatBooking.tokenNumber ?? "—"} · chat with hospital and doctor`}
          senderType="PATIENT"
          senderName={session.getPatientName() || chatBooking.patientName || "Patient"}
          phone={phone}
          onClose={() => setChatBooking(null)}
        />
      )}
    </div>
  );
}
