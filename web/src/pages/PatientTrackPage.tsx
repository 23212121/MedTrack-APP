import { FormEvent, useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import PatientBookingDetails from "../components/PatientBookingDetails";
import PatientQueueStatusView from "../components/PatientQueueStatusView";
import { popupClosePath } from "../auth";
import { api, BookingSummary, PatientQueueStatusResponse } from "../api";

function normalizePhone(raw: string) {
  return raw.replace(/\D/g, "");
}

export default function PatientTrackPage() {
  const [params, setParams] = useSearchParams();
  const initialPhone = params.get("phone") || "";
  const [phoneInput, setPhoneInput] = useState(initialPhone);
  const [trackedPhone, setTrackedPhone] = useState(
    initialPhone.trim() ? normalizePhone(initialPhone) : "",
  );
  const [status, setStatus] = useState<PatientQueueStatusResponse | null>(null);
  const [bookings, setBookings] = useState<BookingSummary[]>([]);
  const [patientId, setPatientId] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!trackedPhone) {
      setStatus(null);
      setBookings([]);
      setPatientId("");
      return;
    }
    setLoading(true);
    setError("");
    try {
      const bookingsRes = await api.patientBookings(trackedPhone);
      setBookings(bookingsRes.upcoming || []);
      if (bookingsRes.patientId) {
        setPatientId(bookingsRes.patientId);
      }

      try {
        const data = await api.patientQueueStatus(trackedPhone);
        setStatus(data);
        if (data.patientId) {
          setPatientId(data.patientId);
        }
      } catch {
        setStatus(null);
        if ((bookingsRes.upcoming || []).length === 0) {
          setError("No appointment found for this mobile number");
        }
      }
    } catch (e) {
      setStatus(null);
      setBookings([]);
      setError(
        e instanceof Error
          ? e.message
          : "No appointment found for this mobile number",
      );
    } finally {
      setLoading(false);
    }
  }, [trackedPhone]);

  useEffect(() => {
    if (!trackedPhone) return;
    load();
    const id = window.setInterval(load, 30_000);
    return () => window.clearInterval(id);
  }, [load, trackedPhone]);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const normalized = normalizePhone(phoneInput);
    if (normalized.length < 8) {
      setError("Enter a valid mobile number (at least 8 digits)");
      setStatus(null);
      setBookings([]);
      return;
    }
    setError("");
    setTrackedPhone(normalized);
    setParams(normalized ? { phone: normalized } : {});
  }

  return (
    <div className="patient-queue-backdrop">
      <section className="patient-queue-modal panel">
        <header className="patient-queue-head">
          <div>
            <p className="patient-booking-brand">MedTrack Clinic</p>
            <h1>Track your appointment</h1>
            <p className="lead patient-booking-lead">
              Enter the mobile number used when the hospital booked your visit
              (or when you self-booked). Example:{" "}
              <code>/track?phone=982972723</code>
            </p>
          </div>
          <Link to={popupClosePath()} className="patient-booking-signin" aria-label="Close">
            ×
          </Link>
        </header>

        <form className="stack patient-queue-token-form" onSubmit={onSubmit}>
          <label>
            Mobile number
            <input
              type="tel"
              inputMode="tel"
              autoComplete="tel"
              placeholder="e.g. 982972723"
              value={phoneInput}
              onChange={(e) => setPhoneInput(e.target.value)}
              required
            />
          </label>
          <div className="row" style={{ gap: "0.75rem", flexWrap: "wrap" }}>
            <button type="submit" disabled={loading}>
              {loading && trackedPhone ? "Updating…" : "Track appointment"}
            </button>
            {trackedPhone && (
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  setPhoneInput("");
                  setTrackedPhone("");
                  setStatus(null);
                  setBookings([]);
                  setPatientId("");
                  setError("");
                  setParams({});
                }}
              >
                Clear
              </button>
            )}
          </div>
        </form>

        {trackedPhone && patientId && (
          <p className="lead patient-note">
            Phone: <strong>{trackedPhone}</strong>
            {" · "}
            Patient ID: <strong>{patientId}</strong>
          </p>
        )}

        {error && !status && bookings.length === 0 && (
          <div className="msg error">{error}</div>
        )}

        {bookings.length > 0 && (
          <section className="panel" style={{ marginTop: "1rem", boxShadow: "none" }}>
            <h2>Upcoming bookings ({bookings.length})</h2>
            <ul className="patient-appointment-list">
              {bookings.map((appt) => (
                <li key={appt.id} className="patient-appointment-item">
                  <div className="patient-booking-card">
                    <PatientBookingDetails appt={appt} compactHeader />
                  </div>
                </li>
              ))}
            </ul>
          </section>
        )}

        {status && (
          <div className="patient-portal-stack" style={{ marginTop: "1.25rem" }}>
            <PatientQueueStatusView
              status={status}
              loading={loading}
              phoneLabel={trackedPhone}
            />
          </div>
        )}

        <p className="lead" style={{ marginTop: "1.25rem" }}>
          <Link to="/book">Book an appointment</Link> ·{" "}
          <Link to="/queue">Doctor queue board</Link>
        </p>
      </section>
    </div>
  );
}
