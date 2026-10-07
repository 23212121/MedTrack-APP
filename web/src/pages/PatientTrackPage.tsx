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
  const [trackedPhone, setTrackedPhone] = useState("");
  const [otpRequired, setOtpRequired] = useState(true);
  const [otpSent, setOtpSent] = useState(false);
  const [otpInput, setOtpInput] = useState("");
  const [debugOtp, setDebugOtp] = useState("");
  const [status, setStatus] = useState<PatientQueueStatusResponse | null>(null);
  const [bookings, setBookings] = useState<BookingSummary[]>([]);
  const [patientId, setPatientId] = useState("");
  const [error, setError] = useState("");
  const [hint, setHint] = useState("");
  const [loading, setLoading] = useState(false);
  const [flagLoaded, setFlagLoaded] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api
      .trackOtpFlag()
      .then((res) => {
        if (cancelled) return;
        const required = !!res.otpRequired;
        setOtpRequired(required);
        setFlagLoaded(true);
        const seed = normalizePhone(initialPhone);
        if (!required && seed.length >= 8) {
          setTrackedPhone(seed);
        }
      })
      .catch(() => {
        if (cancelled) return;
        setOtpRequired(true);
        setFlagLoaded(true);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  function resetDetails() {
    setTrackedPhone("");
    setStatus(null);
    setBookings([]);
    setPatientId("");
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const normalized = normalizePhone(phoneInput);
    if (normalized.length < 8) {
      setError("Enter a valid mobile number (at least 8 digits)");
      resetDetails();
      return;
    }
    setError("");
    setHint("");
    if (!otpRequired) {
      setTrackedPhone(normalized);
      setParams(normalized ? { phone: normalized } : {});
      return;
    }
    setLoading(true);
    try {
      if (!otpSent) {
        const sent = await api.sendTrackOtp(normalized);
        if (!sent.otpRequired) {
          setOtpRequired(false);
          setTrackedPhone(normalized);
          setParams({ phone: normalized });
          return;
        }
        setOtpSent(true);
        setOtpInput("");
        setDebugOtp(sent.debugOtp || "");
        setHint(sent.message || "OTP sent to this mobile number.");
        setParams({ phone: normalized });
        return;
      }
      const verified = await api.verifyTrackOtp(normalized, otpInput);
      if (!verified.verified) {
        setError("OTP was not confirmed");
        return;
      }
      setHint("Mobile confirmed. Showing appointments for this number.");
      setTrackedPhone(normalized);
      setParams({ phone: normalized });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not confirm this mobile number");
      resetDetails();
    } finally {
      setLoading(false);
    }
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
              (or when you self-booked). If OTP is on, we send a code to that
              number first, then show the matching appointments.
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
              onChange={(e) => {
                setPhoneInput(e.target.value);
                setOtpSent(false);
                setOtpInput("");
                setDebugOtp("");
                setHint("");
                resetDetails();
              }}
              required
            />
          </label>
          {otpRequired && otpSent && !trackedPhone ? (
            <label>
              OTP
              <input
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                placeholder="6-digit code"
                maxLength={6}
                value={otpInput}
                onChange={(e) => setOtpInput(e.target.value.replace(/\D/g, "").slice(0, 6))}
                required
              />
            </label>
          ) : null}
          {debugOtp && otpSent && !trackedPhone ? (
            <p className="patient-note">Dev SMS (console): {debugOtp}</p>
          ) : null}
          {hint && !error ? <p className="patient-note">{hint}</p> : null}
          <div className="row" style={{ gap: "0.75rem", flexWrap: "wrap" }}>
            <button type="submit" disabled={loading || !flagLoaded}>
              {loading
                ? otpRequired && !trackedPhone
                  ? otpSent
                    ? "Confirming…"
                    : "Sending OTP…"
                  : "Updating…"
                : otpRequired && !trackedPhone
                  ? otpSent
                    ? "Confirm OTP"
                    : "Send OTP"
                  : "Track appointment"}
            </button>
            {otpRequired && otpSent && !trackedPhone ? (
              <button
                type="button"
                className="secondary"
                disabled={loading}
                onClick={() => {
                  setOtpSent(false);
                  setOtpInput("");
                  setDebugOtp("");
                  setHint("");
                  setError("");
                  const normalized = normalizePhone(phoneInput);
                  if (normalized.length < 8) return;
                  setLoading(true);
                  api
                    .sendTrackOtp(normalized)
                    .then((sent) => {
                      setOtpSent(true);
                      setDebugOtp(sent.debugOtp || "");
                      setHint(sent.message || "OTP sent to this mobile number.");
                    })
                    .catch((err) => {
                      setError(err instanceof Error ? err.message : "Could not resend OTP");
                    })
                    .finally(() => setLoading(false));
                }}
              >
                Resend OTP
              </button>
            ) : null}
            {(trackedPhone || otpSent) && (
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  setPhoneInput("");
                  setOtpSent(false);
                  setOtpInput("");
                  setDebugOtp("");
                  setHint("");
                  setError("");
                  resetDetails();
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
