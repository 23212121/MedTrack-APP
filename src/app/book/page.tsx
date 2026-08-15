"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";

type Clinic = { id: string; name: string; doctorCount: number };
type Doctor = {
  id: string;
  name: string;
  specialty: string;
  department: string | null;
  consultationFee: number;
  consultMinutes: number;
  workingSummary: string;
  schedules: {
    dayOfWeek: number;
    dayName: string;
    startTime: string;
    endTime: string;
    slotMinutes: number;
  }[];
};
type Slot = {
  startsAt: string;
  endsAt: string;
  tokenNumber: number;
  label: string;
};
type Confirmation = {
  visitId: string;
  patient: string;
  phone: string;
  hospital: string;
  doctor: string;
  specialty: string;
  token: number | null;
  appointmentTime: string | null;
  date: string;
  fee: number | null;
  currency: string;
  status: string;
};

function todayYmd() {
  const d = new Date();
  const off = d.getTimezoneOffset();
  return new Date(d.getTime() - off * 60_000).toISOString().slice(0, 10);
}

export default function BookAppointmentPage() {
  const [clinics, setClinics] = useState<Clinic[]>([]);
  const [clinicId, setClinicId] = useState("");
  const [clinicSearch, setClinicSearch] = useState("");

  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [doctorId, setDoctorId] = useState("");
  const [doctorSearch, setDoctorSearch] = useState("");

  const [date, setDate] = useState(todayYmd());
  const [slots, setSlots] = useState<Slot[]>([]);
  const [workingDay, setWorkingDay] = useState(true);
  const [selectedStart, setSelectedStart] = useState("");
  const [loadingSlots, setLoadingSlots] = useState(false);

  const [patientName, setPatientName] = useState("");
  const [phone, setPhone] = useState("");
  const [age, setAge] = useState("");
  const [gender, setGender] = useState("UNKNOWN");
  const [address, setAddress] = useState("");
  const [reason, setReason] = useState("");

  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);

  const selectedDoctor = useMemo(
    () => doctors.find((d) => d.id === doctorId) ?? null,
    [doctors, doctorId]
  );

  const filteredClinics = useMemo(() => {
    const q = clinicSearch.trim().toLowerCase();
    if (!q) return clinics;
    return clinics.filter((c) => c.name.toLowerCase().includes(q));
  }, [clinics, clinicSearch]);

  const filteredDoctors = useMemo(() => {
    const q = doctorSearch.trim().toLowerCase();
    if (!q) return doctors;
    return doctors.filter(
      (d) =>
        d.name.toLowerCase().includes(q) ||
        d.specialty.toLowerCase().includes(q) ||
        (d.department?.toLowerCase().includes(q) ?? false)
    );
  }, [doctors, doctorSearch]);

  const nextSlot = slots[0] ?? null;

  useEffect(() => {
    fetch("/api/public/clinics")
      .then((r) => r.json())
      .then((data) => {
        setClinics(data.clinics ?? []);
        if (data.clinics?.[0]) setClinicId(data.clinics[0].id);
      })
      .catch(() => setError("Could not load hospitals"));
  }, []);

  useEffect(() => {
    if (!clinicId) return;
    setDoctorId("");
    setSlots([]);
    setSelectedStart("");
    fetch(`/api/public/doctors?clinicId=${encodeURIComponent(clinicId)}`)
      .then((r) => r.json())
      .then((data) => {
        setDoctors(data.doctors ?? []);
        if (data.doctors?.[0]) setDoctorId(data.doctors[0].id);
      })
      .catch(() => setError("Could not load doctors"));
  }, [clinicId]);

  useEffect(() => {
    if (!doctorId || !date) return;
    setLoadingSlots(true);
    setSelectedStart("");
    setError("");
    fetch(
      `/api/public/slots?doctorId=${encodeURIComponent(doctorId)}&date=${encodeURIComponent(date)}`
    )
      .then((r) => r.json().then((data) => ({ ok: r.ok, data })))
      .then(({ ok, data }) => {
        if (!ok) {
          setSlots([]);
          setWorkingDay(false);
          setError(data.error || "Could not load slots");
          return;
        }
        setWorkingDay(data.workingDay);
        setSlots(data.slots ?? []);
        if (data.nextSlot) setSelectedStart(data.nextSlot.startsAt);
      })
      .catch(() => setError("Could not load schedule slots"))
      .finally(() => setLoadingSlots(false));
  }, [doctorId, date]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (!clinicId || !doctorId || !selectedStart) {
      setError("Select hospital, doctor, date, and a time slot");
      return;
    }
    setLoading(true);
    try {
      const res = await fetch("/api/public/book", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          clinicId,
          doctorId,
          patientName,
          phone,
          age: age ? Number(age) : undefined,
          gender: gender === "UNKNOWN" ? undefined : gender,
          address: address || undefined,
          reason: reason || undefined,
          appointmentDate: date,
          scheduledStart: selectedStart,
        }),
      });
      const data = await res.json();
      if (!res.ok) {
        setError(data.error || "Booking failed");
        return;
      }
      setConfirmation(data.confirmation);
    } catch {
      setError("Network error — please try again");
    } finally {
      setLoading(false);
    }
  }

  if (confirmation) {
    const when = confirmation.appointmentTime
      ? new Date(confirmation.appointmentTime).toLocaleString()
      : confirmation.date;
    return (
      <main>
        <div className="card" style={{ maxWidth: 560, margin: "2rem auto" }}>
          <p className="success" style={{ marginTop: 0, fontWeight: 700 }}>
            Appointment booked successfully
          </p>
          <h1 className="hero-brand" style={{ fontSize: "1.8rem" }}>
            Confirmation
          </h1>
          <ul className="list" style={{ marginTop: "1rem" }}>
            <li>
              <strong>Patient:</strong> {confirmation.patient}
            </li>
            <li>
              <strong>Phone:</strong> {confirmation.phone}
            </li>
            <li>
              <strong>Hospital:</strong> {confirmation.hospital}
            </li>
            <li>
              <strong>Doctor:</strong> {confirmation.doctor}
              {confirmation.specialty ? ` · ${confirmation.specialty}` : ""}
            </li>
            <li>
              <strong>Token:</strong> {confirmation.token ?? "—"}
            </li>
            <li>
              <strong>Appointment:</strong> {when}
            </li>
            <li>
              <strong>Fee:</strong>{" "}
              {confirmation.fee != null
                ? `${confirmation.currency} ${confirmation.fee}`
                : "—"}
            </li>
          </ul>
          <p className="muted" style={{ fontSize: "0.9rem" }}>
            Confirmation SMS/email was queued for the patient. The doctor was
            notified of this booking.
          </p>
          <div className="row" style={{ marginTop: "1rem" }}>
            <button
              type="button"
              onClick={() => {
                setConfirmation(null);
                setPatientName("");
                setPhone("");
                setAge("");
                setAddress("");
                setReason("");
                setSelectedStart(nextSlot?.startsAt ?? "");
              }}
            >
              Book another
            </button>
            <Link href="/login" className="btn secondary">
              Staff login
            </Link>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main>
      <div className="card" style={{ maxWidth: 720, margin: "2rem auto" }}>
        <div className="topbar" style={{ marginBottom: "0.5rem" }}>
          <div>
            <p className="muted" style={{ marginTop: 0, marginBottom: 0 }}>
              No login required
            </p>
            <h1 className="hero-brand" style={{ fontSize: "1.9rem" }}>
              Book Appointment
            </h1>
          </div>
          <Link href="/login" className="btn secondary">
            Staff login
          </Link>
        </div>
        <p className="muted">
          Choose a hospital and doctor. Times are calculated from the doctor&apos;s
          schedule and existing bookings.
        </p>

        <form className="stack" onSubmit={onSubmit}>
          <section className="stack">
            <h2 style={{ margin: 0, fontSize: "1.1rem" }}>Hospital</h2>
            <label>
              Search hospital
              <input
                value={clinicSearch}
                onChange={(e) => setClinicSearch(e.target.value)}
                placeholder="Search…"
              />
            </label>
            <label>
              Select hospital
              <select
                value={clinicId}
                onChange={(e) => setClinicId(e.target.value)}
                required
              >
                {filteredClinics.length === 0 ? (
                  <option value="">No hospitals found</option>
                ) : (
                  filteredClinics.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name} ({c.doctorCount} doctors)
                    </option>
                  ))
                )}
              </select>
            </label>
          </section>

          <section className="stack">
            <h2 style={{ margin: 0, fontSize: "1.1rem" }}>Doctor</h2>
            <label>
              Search doctor
              <input
                value={doctorSearch}
                onChange={(e) => setDoctorSearch(e.target.value)}
                placeholder="Name or specialty…"
              />
            </label>
            <label>
              Select doctor
              <select
                value={doctorId}
                onChange={(e) => setDoctorId(e.target.value)}
                required
              >
                {filteredDoctors.length === 0 ? (
                  <option value="">No doctors available</option>
                ) : (
                  filteredDoctors.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name} — {d.specialty} · ₹{d.consultationFee}
                    </option>
                  ))
                )}
              </select>
            </label>
            {selectedDoctor ? (
              <div
                className="card"
                style={{
                  background: "#f8fbfb",
                  boxShadow: "none",
                  padding: "0.85rem 1rem",
                }}
              >
                <strong>{selectedDoctor.name}</strong>
                <div className="muted" style={{ fontSize: "0.9rem" }}>
                  {selectedDoctor.specialty}
                  {selectedDoctor.department
                    ? ` · ${selectedDoctor.department}`
                    : ""}
                </div>
                <div style={{ marginTop: "0.35rem" }}>
                  ₹{selectedDoctor.consultationFee} ·{" "}
                  {selectedDoctor.consultMinutes} min consult
                </div>
                <div className="muted" style={{ fontSize: "0.85rem", marginTop: "0.35rem" }}>
                  Working: {selectedDoctor.workingSummary}
                </div>
              </div>
            ) : null}
          </section>

          <section className="stack">
            <h2 style={{ margin: 0, fontSize: "1.1rem" }}>Date & time</h2>
            <label>
              Appointment date
              <input
                type="date"
                value={date}
                min={todayYmd()}
                onChange={(e) => setDate(e.target.value)}
                required
              />
            </label>
            {loadingSlots ? (
              <p className="muted">Loading available slots…</p>
            ) : !workingDay ? (
              <p className="error">
                Doctor is not scheduled on this day. Pick another date (seed
                doctor works Mon–Fri).
              </p>
            ) : slots.length === 0 ? (
              <p className="error">No open slots left for this date.</p>
            ) : (
              <>
                {nextSlot ? (
                  <p className="success" style={{ margin: 0 }}>
                    Next available: Token {nextSlot.tokenNumber} at{" "}
                    {nextSlot.label}
                  </p>
                ) : null}
                <label>
                  Choose time slot
                  <select
                    value={selectedStart}
                    onChange={(e) => setSelectedStart(e.target.value)}
                    required
                  >
                    {slots.map((s) => (
                      <option key={s.startsAt} value={s.startsAt}>
                        Token {s.tokenNumber} — {s.label}
                      </option>
                    ))}
                  </select>
                </label>
              </>
            )}
          </section>

          <section className="stack">
            <h2 style={{ margin: 0, fontSize: "1.1rem" }}>Patient details</h2>
            <div className="grid-2">
              <label>
                Patient name *
                <input
                  value={patientName}
                  onChange={(e) => setPatientName(e.target.value)}
                  required
                />
              </label>
              <label>
                Phone number *
                <input
                  className="phone-input"
                  style={{ fontSize: "1rem" }}
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  placeholder="9876543210"
                  required
                />
              </label>
              <label>
                Age (optional)
                <input
                  type="number"
                  min={1}
                  max={120}
                  value={age}
                  onChange={(e) => setAge(e.target.value)}
                />
              </label>
              <label>
                Gender (optional)
                <select
                  value={gender}
                  onChange={(e) => setGender(e.target.value)}
                >
                  <option value="UNKNOWN">Prefer not to say</option>
                  <option value="FEMALE">Female</option>
                  <option value="MALE">Male</option>
                  <option value="OTHER">Other</option>
                </select>
              </label>
            </div>
            <label>
              Address / local area (optional)
              <input
                value={address}
                onChange={(e) => setAddress(e.target.value)}
              />
            </label>
            <label>
              Symptoms / reason (optional)
              <textarea
                rows={3}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </label>
          </section>

          {error ? <p className="error">{error}</p> : null}

          <button type="submit" disabled={loading || !selectedStart}>
            {loading ? "Booking…" : "Book Appointment"}
          </button>
        </form>
      </div>
    </main>
  );
}
