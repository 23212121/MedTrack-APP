import { FormEvent, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  api,
  Booking,
  CreateBookingBody,
  DoctorAvailableDay,
  HospitalDoctor,
  HospitalRegistrationSummary,
} from "../api";
import { session } from "../dl/MedTrackSession";
import { toast } from "../toast";

function todayYmd() {
  const d = new Date();
  const off = d.getTimezoneOffset();
  return new Date(d.getTime() - off * 60_000).toISOString().slice(0, 10);
}

function addDaysYmd(ymd: string, days: number) {
  const d = new Date(`${ymd}T00:00:00`);
  d.setDate(d.getDate() + days);
  const off = d.getTimezoneOffset();
  return new Date(d.getTime() - off * 60_000).toISOString().slice(0, 10);
}

function defaultTimeLocal() {
  const d = new Date();
  d.setMinutes(0, 0, 0);
  d.setHours(d.getHours() + 1);
  const off = d.getTimezoneOffset();
  return new Date(d.getTime() - off * 60_000).toISOString().slice(11, 16);
}

/** Combine YYYY-MM-DD + HH:mm (local) into ISO-8601 Instant for JPA. */
function toInstant(date: string, time: string) {
  return new Date(`${date}T${time}:00`).toISOString();
}

function doctorLabel(d: HospitalDoctor) {
  const dept = d.department?.trim() || d.specialization?.trim();
  return dept ? `${d.doctorName} — ${dept}` : d.doctorName;
}

const emptyForm = {
  patientName: "",
  patientPhone: "",
  patientEmail: "",
  patientAge: "",
  gender: "",
  address: "",
  reason: "",
  appointmentDate: todayYmd(),
  appointmentTime: defaultTimeLocal(),
  doctorId: "",
  doctorName: "",
  hospitalName: "",
  hospitalId: "" as string | number | "",
  consultationFee: "",
};

/** Patient portal booking — start with every field empty (no session prefill). */
const blankPublicForm = {
  ...emptyForm,
  appointmentDate: "",
  appointmentTime: "",
};

type BookingsPageProps = {
  /** Patient self-booking — no login, hide staff list */
  publicMode?: boolean;
  /** Limit doctors to this hospital (from share link) */
  hospitalIdFilter?: string;
  /** Pre-fill logged-in patient phone */
  patientPhone?: string;
  /** Pre-fill logged-in patient name */
  patientName?: string;
  /** Called after a successful book (e.g. refresh upcoming list on portal) */
  onBooked?: () => void;
};

function mapDoctor(d: HospitalDoctor): HospitalDoctor {
  return {
    doctorId: d.doctorId,
    doctorName: d.doctorName,
    firstName: d.firstName,
    middleName: d.middleName,
    lastName: d.lastName,
    specialization: d.specialization,
    department: d.department,
    hospitalId: d.hospitalId,
    hospitalName: d.hospitalName,
    clinicName: d.clinicName,
    consultationFee: d.consultationFee,
  };
}

export default function BookingsPage({
  publicMode = false,
  hospitalIdFilter,
  patientPhone,
  patientName,
  onBooked,
}: BookingsPageProps = {}) {
  const navigate = useNavigate();
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [hospitals, setHospitals] = useState<HospitalRegistrationSummary[]>([]);
  const [allDoctors, setAllDoctors] = useState<HospitalDoctor[]>([]);
  /** Optional hospital filter — empty means show all doctors */
  const [selectedHospitalId, setSelectedHospitalId] = useState(
    hospitalIdFilter ? String(hospitalIdFilter) : "",
  );
  const [form, setForm] = useState(publicMode ? blankPublicForm : emptyForm);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [loadingDoctors, setLoadingDoctors] = useState(false);
  const [loadingHospitals, setLoadingHospitals] = useState(false);
  const [availFrom, setAvailFrom] = useState(todayYmd());
  const [availTo, setAvailTo] = useState(addDaysYmd(todayYmd(), 14));
  const [availDays, setAvailDays] = useState<DoctorAvailableDay[]>([]);
  const [loadingAvail, setLoadingAvail] = useState(false);
  const [availNote, setAvailNote] = useState("");
  const [dateWarning, setDateWarning] = useState("");

  const doctors = useMemo(() => {
    if (!selectedHospitalId) return allDoctors;
    return allDoctors.filter(
      (d) => String(d.hospitalId) === String(selectedHospitalId),
    );
  }, [allDoctors, selectedHospitalId]);

  async function load() {
    if (publicMode) return;
    setError("");
    try {
      const data = await api.bookings();
      setBookings(data.bookings);
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Failed to load bookings — is booking-service running?"
      );
    }
  }

  async function loadHospitals() {
    setLoadingHospitals(true);
    try {
      const data = await api.hospitals();
      const rows = (data.hospitals ?? []).slice().sort((a, b) =>
        String(a.hospitalName).localeCompare(String(b.hospitalName)),
      );
      setHospitals(rows);
    } catch {
      setHospitals([]);
    } finally {
      setLoadingHospitals(false);
    }
  }

  async function loadDoctors() {
    setLoadingDoctors(true);
    setError("");
    try {
      const data = await api.doctors();
      const rows = (data.doctors ?? []) as unknown as HospitalDoctor[];
      const hospitalDoctors = rows
        .filter((d) => d.doctorId && d.hospitalId != null)
        .map(mapDoctor);
      setAllDoctors(hospitalDoctors);
      setForm((prev) => {
        if (!prev.doctorId) return prev;
        const pool = selectedHospitalId
          ? hospitalDoctors.filter(
              (d) => String(d.hospitalId) === String(selectedHospitalId),
            )
          : hospitalDoctors;
        const stillThere = pool.some((d) => d.doctorId === prev.doctorId);
        if (stillThere) return prev;
        return {
          ...prev,
          doctorId: "",
          doctorName: "",
          hospitalName: "",
          hospitalId: selectedHospitalId || "",
          consultationFee: "",
        };
      });
    } catch (e) {
      setAllDoctors([]);
      setError(e instanceof Error ? e.message : "Failed to load doctors");
    } finally {
      setLoadingDoctors(false);
    }
  }

  useEffect(() => {
    if (hospitalIdFilter) {
      setSelectedHospitalId(String(hospitalIdFilter));
    }
  }, [hospitalIdFilter]);

  useEffect(() => {
    // Hospital booking desk may prefill patient; patient portal stays blank.
    if (publicMode) return;
    if (patientPhone || patientName) {
      setForm((prev) => ({
        ...prev,
        patientPhone: patientPhone || prev.patientPhone,
        patientName: patientName || prev.patientName,
      }));
    }
  }, [publicMode, patientPhone, patientName]);

  useEffect(() => {
    load();
    void loadHospitals();
    void loadDoctors();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [publicMode]);

  useEffect(() => {
    // When hospital filter changes, clear doctor if it no longer belongs
    setForm((prev) => {
      if (!prev.doctorId) return prev;
      const stillThere = doctors.some((d) => d.doctorId === prev.doctorId);
      if (stillThere) return prev;
      return {
        ...prev,
        doctorId: "",
        doctorName: "",
        clinicName: "",
        hospitalName: "",
        hospitalId: selectedHospitalId || "",
        consultationFee: "",
      };
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedHospitalId, doctors]);

  function setField<K extends keyof typeof emptyForm>(key: K, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  function onHospitalChange(hospitalId: string) {
    setSelectedHospitalId(hospitalId);
    setForm((prev) => ({
      ...prev,
      doctorId: "",
      doctorName: "",
      hospitalName: "",
      hospitalId: hospitalId || "",
      consultationFee: "",
    }));
  }

  function onDoctorChange(doctorId: string) {
    const selected = doctors.find((d) => d.doctorId === doctorId);
    if (!selected) {
      setForm((prev) => ({
        ...prev,
        doctorId: "",
        doctorName: "",
        clinicName: "",
        hospitalName: "",
        hospitalId: selectedHospitalId || "",
        consultationFee: "",
      }));
      setAvailDays([]);
      setAvailNote("");
      setDateWarning("");
      return;
    }
    setDateWarning("");
    // Selecting a doctor also sets hospital (even if hospital dropdown was skipped)
    if (!selectedHospitalId && selected.hospitalId != null) {
      setSelectedHospitalId(String(selected.hospitalId));
    }
    setForm((prev) => ({
      ...prev,
      doctorId: selected.doctorId,
      doctorName: selected.doctorName,
      hospitalName: selected.hospitalName || selected.clinicName || "",
      hospitalId: selected.hospitalId,
      consultationFee:
        selected.consultationFee != null
          ? String(selected.consultationFee)
          : prev.consultationFee,
    }));
  }

  async function loadDoctorAvailability(doctorId: string, from: string, to: string) {
    if (!doctorId || !from || !to) {
      setAvailDays([]);
      setAvailNote("");
      return;
    }
    if (to < from) {
      setAvailNote("To date must be on or after From date");
      setAvailDays([]);
      return;
    }
    setLoadingAvail(true);
    setAvailNote("");
    try {
      const data = await api.availableDays(doctorId, from, to);
      const days = data.days || [];
      setAvailDays(days);
      setForm((prev) => {
        if (!prev.appointmentDate) return prev;
        const picked = days.find((d) => d.date === prev.appointmentDate);
        if (picked && !picked.available) {
          setDateWarning(
            unavailableMessage(
              prev.doctorName,
              prev.appointmentDate,
              picked.summary,
              (data.availableDates || []).filter((d) => d !== prev.appointmentDate),
            ),
          );
          return { ...prev, appointmentDate: "" };
        }
        return prev;
      });
      if (!data.hasWeeklySchedule) {
        setAvailNote(
          "No weekly schedule set for this doctor yet — availability may show as Off."
        );
      } else if ((data.availableDates || []).length === 0) {
        setAvailNote("Doctor is not available in the selected date range.");
      } else {
        setAvailNote(
          `${data.availableDates.length} available day(s) in range. Click a day to book.`
        );
      }
    } catch (e) {
      setAvailDays([]);
      setAvailNote(e instanceof Error ? e.message : "Failed to load availability");
    } finally {
      setLoadingAvail(false);
    }
  }

  useEffect(() => {
    if (!form.doctorId) {
      setAvailDays([]);
      setAvailNote("");
      return;
    }
    void loadDoctorAvailability(form.doctorId, availFrom, availTo);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form.doctorId, availFrom, availTo]);

  function doctorOptionLabel(d: HospitalDoctor) {
    const base = doctorLabel(d);
    if (selectedHospitalId) return base;
    const hosp = d.hospitalName?.trim() || (d.hospitalId != null ? `#${d.hospitalId}` : "");
    return hosp ? `${base} (${hosp})` : base;
  }

  function formatSuggestDates(dates: string[], max = 5) {
    const slice = dates.slice(0, max);
    if (slice.length === 0) return "";
    const extra = dates.length > max ? ` (+${dates.length - max} more)` : "";
    return slice.join(", ") + extra;
  }

  function unavailableMessage(
    doctorName: string,
    date: string,
    daySummary: string | undefined,
    suggestDates: string[],
  ) {
    const name = doctorName || "Doctor";
    const why = daySummary ? ` (${daySummary})` : "";
    const suggest = formatSuggestDates(suggestDates);
    if (suggest) {
      return `${name} is not available on ${date}${why}. Please choose an available date: ${suggest}.`;
    }
    return `${name} is not available on ${date}${why}. Please check availability dates above and pick another date.`;
  }

  async function checkAppointmentDateAvailable(
    doctorId: string,
    doctorName: string,
    appointmentDate: string,
  ): Promise<{ ok: boolean; message: string; suggestDates: string[] }> {
    if (!doctorId || !appointmentDate) {
      return { ok: true, message: "", suggestDates: [] };
    }

    // Prefer already-loaded range; otherwise fetch a window around the chosen date
    let days = availDays;
    let suggestDates = days.filter((d) => d.available).map((d) => d.date);

    const inLoadedRange =
      days.length > 0 &&
      appointmentDate >= (days[0]?.date || "") &&
      appointmentDate <= (days[days.length - 1]?.date || "");

    if (!inLoadedRange) {
      const from = appointmentDate < todayYmd() ? todayYmd() : appointmentDate;
      const to = addDaysYmd(from, 21);
      try {
        const data = await api.availableDays(doctorId, from, to);
        days = data.days || [];
        suggestDates = data.availableDates || [];
      } catch {
        return {
          ok: false,
          message: "Could not verify doctor availability. Please try again.",
          suggestDates: [],
        };
      }
    }

    const day = days.find((d) => d.date === appointmentDate);
    if (day?.available) {
      return { ok: true, message: "", suggestDates };
    }

    // Also suggest from the filter panel range if different
    if (suggestDates.length === 0) {
      suggestDates = availDays.filter((d) => d.available).map((d) => d.date);
    }

    return {
      ok: false,
      message: unavailableMessage(
        doctorName,
        appointmentDate,
        day?.summary,
        suggestDates.filter((d) => d !== appointmentDate),
      ),
      suggestDates,
    };
  }

  async function onAppointmentDateChange(date: string) {
    setField("appointmentDate", date);
    setDateWarning("");
    setError("");
    if (!form.doctorId || !date) return;
    const result = await checkAppointmentDateAvailable(
      form.doctorId,
      form.doctorName,
      date,
    );
    if (!result.ok) {
      setDateWarning(result.message);
      setField("appointmentDate", "");
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form.doctorId) {
      setError("Select a doctor");
      return;
    }

    setSaving(true);
    setError("");
    setDateWarning("");
    try {
      const availability = await checkAppointmentDateAvailable(
        form.doctorId,
        form.doctorName,
        form.appointmentDate,
      );
      if (!availability.ok) {
        setDateWarning(availability.message);
        setError(availability.message);
        setSaving(false);
        return;
      }

      const loggedInPatientId = session.isPatient() ? session.getPatientId() : "";
      const body: CreateBookingBody = {
        doctorId: form.doctorId,
        hospitalId:
          form.hospitalId !== "" ? Number(form.hospitalId) : undefined,
        patientId: loggedInPatientId || undefined,
        patientName: form.patientName.trim(),
        patientPhone: form.patientPhone.trim(),
        patientEmail: form.patientEmail.trim() || undefined,
        patientAge: form.patientAge ? Number(form.patientAge) : undefined,
        gender: form.gender || undefined,
        address: form.address.trim() || undefined,
        reason: form.reason.trim() || undefined,
        appointmentDate: form.appointmentDate,
        appointmentTime: toInstant(form.appointmentDate, form.appointmentTime),
        bookedBy: publicMode ? "PATIENT" : "HOSPITAL",
        createdBy: publicMode
          ? "PATIENT"
          : session.getUsername() || "HOSPITAL",
      };
      const res = await api.createBooking(body);
      toast.success(
        `${res.message}. Token # ${res.booking.tokenNumber} for ${res.booking.patientName}. SMS confirmation queued${
          form.patientEmail.trim()
            ? `; email will be sent to ${form.patientEmail.trim()} if SMTP is configured`
            : ""
        }.`,
      );
      onBooked?.();
      if (publicMode && session.isPatient() && !onBooked) {
        navigate("/patient/status");
        return;
      }
      if (publicMode) {
        setForm(blankPublicForm);
        if (!hospitalIdFilter) setSelectedHospitalId("");
        setAvailDays([]);
        setAvailNote("");
        setDateWarning("");
      } else {
        setForm({
          ...emptyForm,
          appointmentDate: form.appointmentDate,
          appointmentTime: form.appointmentTime,
          doctorId: form.doctorId,
          doctorName: form.doctorName,
          hospitalName: form.hospitalName,
          hospitalId: form.hospitalId,
          consultationFee: form.consultationFee,
        });
      }
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className={publicMode ? "patient-booking-form" : undefined}>
      {error && <div className="msg error">{error}</div>}

      <form className={publicMode ? "stack" : "panel stack"} onSubmit={onSubmit}>
        {!publicMode && (
          <div className="row" style={{ justifyContent: "flex-end", alignItems: "center" }}>
            <button type="button" className="secondary" onClick={load}>
              Refresh list
            </button>
          </div>
        )}

        <div className="booking-hospital-doctor-row">
          <label className="booking-doctor-field">
            <span>
              Hospital{" "}
              <span style={{ color: "var(--muted)", fontWeight: 500 }}>(optional)</span>
            </span>
            <select
              value={selectedHospitalId}
              onChange={(e) => onHospitalChange(e.target.value)}
              disabled={loadingHospitals}
            >
              <option value="">
                {loadingHospitals
                  ? "Loading hospitals…"
                  : "All hospitals — or pick one to filter doctors"}
              </option>
              {hospitals.map((h) => (
                <option key={h.id ?? h.hospitalId} value={String(h.id ?? h.hospitalId)}>
                  {h.hospitalName}
                  {h.city ? ` — ${h.city}` : ""} (ID {h.id ?? h.hospitalId})
                </option>
              ))}
            </select>
          </label>

          <label className="booking-doctor-field">
            <span>
              Doctor
              <span className="required-mark" aria-hidden="true">
                *
              </span>
            </span>
            <select
              value={form.doctorId}
              onChange={(e) => onDoctorChange(e.target.value)}
              required
              disabled={loadingDoctors || doctors.length === 0}
            >
              <option value="">
                {loadingDoctors
                  ? "Loading doctors…"
                  : doctors.length === 0
                    ? selectedHospitalId
                      ? "No doctors for this hospital"
                      : "No doctors available"
                    : selectedHospitalId
                      ? "Select doctor"
                      : "Select doctor (any hospital)"}
              </option>
              {doctors.map((d) => (
                <option key={d.doctorId} value={d.doctorId}>
                  {doctorOptionLabel(d)}
                </option>
              ))}
            </select>
          </label>
        </div>

        {form.doctorId && (
          <div className="doctor-avail-panel">
            <h3 className="doctor-avail-title">Doctor availability</h3>
            <p className="lead" style={{ marginBottom: "0.65rem" }}>
              Choose From / To dates to see when {form.doctorName || "this doctor"} is available.
            </p>
            <div className="grid-2">
              <label>
                From date
                <input
                  type="date"
                  value={availFrom}
                  min={todayYmd()}
                  onChange={(e) => setAvailFrom(e.target.value)}
                />
              </label>
              <label>
                To date
                <input
                  type="date"
                  value={availTo}
                  min={availFrom || todayYmd()}
                  onChange={(e) => setAvailTo(e.target.value)}
                />
              </label>
            </div>
            {loadingAvail && (
              <p className="lead" style={{ margin: "0.55rem 0 0" }}>
                Checking availability…
              </p>
            )}
            {availNote && !loadingAvail && (
              <p className="lead" style={{ margin: "0.55rem 0 0" }}>
                {availNote}
              </p>
            )}
            {!loadingAvail && availDays.length > 0 && (
              <div className="doctor-avail-list">
                {availDays.map((d) => (
                  <button
                    key={d.date}
                    type="button"
                    className={
                      d.available
                        ? form.appointmentDate === d.date
                          ? "doctor-avail-day is-available is-selected"
                          : "doctor-avail-day is-available"
                        : "doctor-avail-day is-unavailable"
                    }
                    disabled={!d.available}
                    onClick={() => {
                      if (!d.available) return;
                      void onAppointmentDateChange(d.date);
                      if (d.windows?.[0]?.start) {
                        setField("appointmentTime", d.windows[0].start.slice(0, 5));
                      }
                    }}
                    title={d.summary}
                  >
                    <strong>{d.date}</strong>
                    <span>{d.dayName.slice(0, 3)}</span>
                    <em>{d.summary}</em>
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        <div className="grid-2">
          <label>
            <span>
              Patient name
              <span className="required-mark" aria-hidden="true">*</span>
            </span>
            <input
              value={form.patientName}
              onChange={(e) => setField("patientName", e.target.value)}
              required
            />
          </label>
          <label>
            <span>
              Patient phone
              <span className="required-mark" aria-hidden="true">*</span>
            </span>
            <input
              value={form.patientPhone}
              onChange={(e) => setField("patientPhone", e.target.value)}
              placeholder="9876543210"
              required
            />
          </label>
          <label>
            <span>
              Patient email
              {publicMode ? (
                <span style={{ color: "var(--muted)", fontWeight: 500 }}>
                  {" "}
                  (for booking confirmation)
                </span>
              ) : null}
            </span>
            <input
              type="email"
              value={form.patientEmail}
              onChange={(e) => setField("patientEmail", e.target.value)}
              placeholder="azherkhan061@gmail.com"
              required={publicMode}
            />
          </label>
          <label>
            Age
            <input
              type="number"
              min={1}
              max={120}
              value={form.patientAge}
              onChange={(e) => setField("patientAge", e.target.value)}
            />
          </label>
          <label>
            Gender
            <select
              value={form.gender}
              onChange={(e) => setField("gender", e.target.value)}
            >
              <option value="">—</option>
              <option value="FEMALE">Female</option>
              <option value="MALE">Male</option>
              <option value="OTHER">Other</option>
              <option value="UNKNOWN">Prefer not to say</option>
            </select>
          </label>
        </div>

        <label>
          Address
          <input
            value={form.address}
            onChange={(e) => setField("address", e.target.value)}
          />
        </label>
        <label>
          Reason / symptoms
          <textarea
            rows={2}
            value={form.reason}
            onChange={(e) => setField("reason", e.target.value)}
          />
        </label>

        {form.doctorId && form.consultationFee && (
          <p className="lead" style={{ margin: 0 }}>
            Consultation fee (from doctor profile): INR {form.consultationFee}
            {form.hospitalName ? ` · ${form.hospitalName}` : ""}
          </p>
        )}

        <div className="grid-2">
          <label>
            <span>
              Appointment date
              <span className="required-mark" aria-hidden="true">*</span>
            </span>
            <input
              type="date"
              value={form.appointmentDate}
              min={todayYmd()}
              onChange={(e) => void onAppointmentDateChange(e.target.value)}
              required
              aria-invalid={!!dateWarning}
            />
            {availDays.some((d) => !d.available) && (
              <span className="muted" style={{ display: "block", marginTop: "0.35rem" }}>
                Grey / disabled days above cannot be booked (doctor is busy).
              </span>
            )}
          </label>
          <label>
            <span>
              Appointment time
              <span className="required-mark" aria-hidden="true">*</span>
            </span>
            <input
              type="time"
              value={form.appointmentTime}
              onChange={(e) => setField("appointmentTime", e.target.value)}
              required
            />
          </label>
        </div>

        {dateWarning && (
          <div className="msg error" role="alert">
            {dateWarning}
            {availDays.some((d) => d.available) && (
              <div className="doctor-avail-suggest">
                <span>Suggested available dates:</span>
                <div className="doctor-avail-suggest-chips">
                  {availDays
                    .filter((d) => d.available)
                    .slice(0, 8)
                    .map((d) => (
                      <button
                        key={d.date}
                        type="button"
                        className="doctor-avail-chip"
                        onClick={() => {
                          void onAppointmentDateChange(d.date);
                          if (d.windows?.[0]?.start) {
                            setField("appointmentTime", d.windows[0].start.slice(0, 5));
                          }
                        }}
                      >
                        {d.date}
                      </button>
                    ))}
                </div>
              </div>
            )}
          </div>
        )}

        <button type="submit" disabled={saving || !form.doctorId || !!dateWarning}>
          {saving ? "Saving…" : publicMode ? "Book appointment" : "Save booking"}
        </button>
      </form>

      {!publicMode && (
      <div className="panel">
        <h2>Bookings ({bookings.length})</h2>
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th>Token</th>
                <th>Patient</th>
                <th>Phone</th>
                <th>Age</th>
                <th>Gender</th>
                <th>Hospital</th>
                <th>Doctor ID</th>
                <th>Date</th>
                <th>Time</th>
                <th>Reason</th>
                <th>Fee</th>
                <th>Status</th>
                <th>Address</th>
              </tr>
            </thead>
            <tbody>
              {bookings.map((b) => (
                <tr key={b.id}>
                  <td>{b.tokenNumber ?? "—"}</td>
                  <td>{b.patientName}</td>
                  <td>{b.patientPhone}</td>
                  <td>{b.patientAge ?? "—"}</td>
                  <td>{b.gender || "—"}</td>
                  <td>{b.hospitalName ?? "—"}</td>
                  <td>{b.doctorId}</td>
                  <td>{b.appointmentDate}</td>
                  <td>
                    {b.appointmentTime
                      ? new Date(b.appointmentTime).toLocaleTimeString()
                      : "—"}
                  </td>
                  <td>{b.reason || "—"}</td>
                  <td>
                    {b.consultationFee != null
                      ? `${b.currency ?? "INR"} ${b.consultationFee}`
                      : "—"}
                  </td>
                  <td>
                    <span className={`badge ${b.status}`}>{b.status}</span>
                  </td>
                  <td>{b.address || "—"}</td>
                </tr>
              ))}
              {bookings.length === 0 && (
                <tr>
                  <td colSpan={13}>
                    No bookings yet. Submit the form to create one.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
      )}
    </section>
  );
}
