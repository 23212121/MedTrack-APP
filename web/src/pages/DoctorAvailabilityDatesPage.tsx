import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  api,
  Availability,
  DoctorAvailableDay,
  HospitalDoctor,
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

function inclusiveRangeToInstants(fromYmd: string, toYmd: string) {
  const start = new Date(`${fromYmd}T00:00:00`);
  const endExclusive = new Date(`${toYmd}T00:00:00`);
  endExclusive.setDate(endExclusive.getDate() + 1);
  return { startsAt: start.toISOString(), endsAt: endExclusive.toISOString() };
}

function formatInclusiveRange(startsAt: string, endsAt: string) {
  const start = new Date(startsAt);
  const last = new Date(endsAt);
  last.setMilliseconds(last.getMilliseconds() - 1);
  return `${start.toLocaleDateString("en-GB")} – ${last.toLocaleDateString("en-GB")}`;
}

function doctorLabel(d: HospitalDoctor) {
  const spec = (d.department || d.specialization || "").trim();
  return spec ? `${d.doctorName} · ${spec}` : d.doctorName;
}

function overlapsDay(a: Availability, ymd: string) {
  const { startsAt, endsAt } = inclusiveRangeToInstants(ymd, ymd);
  return a.startsAt < endsAt && a.endsAt > startsAt;
}

function blockType(a: Availability) {
  return (a.availabilityType || "").toUpperCase();
}

function isClosedType(type: string) {
  return ["BUSY", "LEAVE", "BLOCKED", "UNAVAILABLE"].includes(type);
}

function isOpenType(type: string) {
  return ["AVAILABLE", "OPEN", "EXTRA"].includes(type);
}

type Props = { lockedDoctorId?: string };

export default function DoctorAvailabilityDatesPage({ lockedDoctorId }: Props) {
  const hospitalId = session.getHospitalId();
  const [doctors, setDoctors] = useState<HospitalDoctor[]>([]);
  const [selectedDoctorId, setSelectedDoctorId] = useState(lockedDoctorId || "");
  const [days, setDays] = useState<DoctorAvailableDay[]>([]);
  const [blocks, setBlocks] = useState<Availability[]>([]);
  const [mode, setMode] = useState<"AVAILABLE" | "BUSY">("AVAILABLE");
  const [fromDate, setFromDate] = useState(todayYmd());
  const [toDate, setToDate] = useState(addDaysYmd(todayYmd(), 0));
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [loadingDoctors, setLoadingDoctors] = useState(!lockedDoctorId);
  const [loadingDays, setLoadingDays] = useState(false);
  const [saving, setSaving] = useState(false);
  const previewFrom = todayYmd();
  const previewTo = addDaysYmd(todayYmd(), 42);

  const selectedDoctor = useMemo(
    () => doctors.find((d) => d.doctorId === selectedDoctorId) ?? null,
    [doctors, selectedDoctorId],
  );

  const doctorName =
    selectedDoctor?.doctorName ||
    (lockedDoctorId ? session.getUsername() || lockedDoctorId : "");

  async function loadDoctors() {
    if (lockedDoctorId) {
      setSelectedDoctorId(lockedDoctorId);
      setLoadingDoctors(false);
      return;
    }
    if (!hospitalId) {
      setError("Hospital session required. Log in as hospital admin.");
      setLoadingDoctors(false);
      return;
    }
    setLoadingDoctors(true);
    setError("");
    try {
      const docData = await api.doctors();
      const rows = ((docData.doctors ?? []) as unknown as HospitalDoctor[]).filter(
        (d) => d.doctorId && String(d.hospitalId) === String(hospitalId),
      );
      setDoctors(rows);
    } catch (e) {
      setDoctors([]);
      setError(e instanceof Error ? e.message : "Failed to load doctors");
    } finally {
      setLoadingDoctors(false);
    }
  }

  async function loadDoctorDates(doctorId: string) {
    if (!doctorId) {
      setDays([]);
      setBlocks([]);
      return;
    }
    setLoadingDays(true);
    setError("");
    try {
      const fromIso = new Date(`${previewFrom}T00:00:00`).toISOString();
      const toIso = new Date(`${addDaysYmd(previewTo, 1)}T00:00:00`).toISOString();
      const [avail, saved] = await Promise.all([
        api.availableDays(doctorId, previewFrom, previewTo),
        api.availability(doctorId, fromIso, toIso),
      ]);
      setDays(avail.days || []);
      setBlocks(saved.availability || []);
    } catch (e) {
      setDays([]);
      setBlocks([]);
      setError(e instanceof Error ? e.message : "Failed to load doctor dates");
    } finally {
      setLoadingDays(false);
    }
  }

  useEffect(() => {
    void loadDoctors();
  }, [hospitalId, lockedDoctorId]);

  useEffect(() => {
    if (selectedDoctorId) void loadDoctorDates(selectedDoctorId);
    else {
      setDays([]);
      setBlocks([]);
    }
  }, [selectedDoctorId]);

  async function saveRange(
    doctorId: string,
    type: "AVAILABLE" | "BUSY",
    from: string,
    to: string,
    note: string,
  ) {
    if (!hospitalId) {
      throw new Error("Hospital ID is required.");
    }
    if (!from || !to) {
      throw new Error("Choose a start date and end date.");
    }
    if (to < from) {
      throw new Error("End date must be on or after the start date.");
    }
    const { startsAt, endsAt } = inclusiveRangeToInstants(from, to);
    await api.addAvailability({
      doctorId,
      hospitalId: Number(hospitalId),
      startsAt,
      endsAt,
      availabilityType: type,
      reason: note,
    });
  }

  async function onSave(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (!selectedDoctorId) {
      setError("Select a doctor.");
      return;
    }
    setSaving(true);
    try {
      await saveRange(selectedDoctorId, mode, fromDate, toDate, reason);
      toast.success(
        mode === "AVAILABLE"
          ? `Patients can book ${doctorName || "this doctor"} from ${fromDate} to ${toDate}.`
          : `${doctorName || "Doctor"} is marked unavailable from ${fromDate} to ${toDate}.`,
      );
      setReason("");
      await loadDoctorDates(selectedDoctorId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    } finally {
      setSaving(false);
    }
  }

  async function onToggleDay(day: DoctorAvailableDay) {
    if (!selectedDoctorId || saving) return;
    setSaving(true);
    setError("");
    try {
      const dayBlocks = blocks.filter((b) => overlapsDay(b, day.date));
      const closed = dayBlocks.filter((b) => isClosedType(blockType(b)));
      const opened = dayBlocks.filter((b) => isOpenType(blockType(b)));
      if (day.available && opened.length > 0 && closed.length === 0) {
        await Promise.all(opened.map((b) => api.deleteAvailability(b.id)));
        toast.success(`${day.date} is no longer an extra available date.`);
      } else if (day.available) {
        await saveRange(selectedDoctorId, "BUSY", day.date, day.date, "Closed for booking");
        toast.success(`${day.date} is now unavailable for patients.`);
      } else if (closed.length > 0) {
        await Promise.all(closed.map((b) => api.deleteAvailability(b.id)));
        toast.success(`${day.date} is open again.`);
      } else {
        await saveRange(selectedDoctorId, "AVAILABLE", day.date, day.date, "Open for booking");
        toast.success(`${day.date} is now available for patients to book.`);
      }
      await loadDoctorDates(selectedDoctorId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update that date");
    } finally {
      setSaving(false);
    }
  }

  async function onDeleteBlock(id: string) {
    setError("");
    try {
      await api.deleteAvailability(id);
      toast.success("Date rule removed.");
      if (selectedDoctorId) await loadDoctorDates(selectedDoctorId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Delete failed");
    }
  }

  return (
    <section>
      <h1>Doctor booking dates</h1>
      <p className="lead">
        Select a doctor, then add or change the dates patients can book. Available days
        appear on the appointment form when a patient picks that doctor.
      </p>
      {error && <div className="msg error">{error}</div>}

      {!lockedDoctorId && (
        <div className="panel stack">
          <label>
            Doctor
            <select
              value={selectedDoctorId}
              onChange={(e) => setSelectedDoctorId(e.target.value)}
              disabled={loadingDoctors || doctors.length === 0}
            >
              <option value="">
                {loadingDoctors
                  ? "Loading doctors…"
                  : doctors.length === 0
                    ? "No doctors for this hospital"
                    : "Select doctor"}
              </option>
              {doctors.map((d) => (
                <option key={d.doctorId} value={d.doctorId}>
                  {doctorLabel(d)}
                </option>
              ))}
            </select>
          </label>
        </div>
      )}

      {selectedDoctorId && (
        <div className="grid-2">
          <form className="panel stack" onSubmit={onSave}>
            <h2>{mode === "AVAILABLE" ? "Add available dates" : "Block dates"}</h2>
            <p className="muted" style={{ margin: 0 }}>
              {doctorName
                ? `Updating dates for ${doctorName}.`
                : "Updating dates for the selected doctor."}
            </p>
            <label>
              Patients can
              <select
                value={mode}
                onChange={(e) => setMode(e.target.value as "AVAILABLE" | "BUSY")}
              >
                <option value="AVAILABLE">Book these dates</option>
                <option value="BUSY">Not book these dates</option>
              </select>
            </label>
            <div className="grid-2">
              <label>
                From date
                <input
                  type="date"
                  value={fromDate}
                  min={todayYmd()}
                  onChange={(e) => setFromDate(e.target.value)}
                  required
                />
              </label>
              <label>
                To date
                <input
                  type="date"
                  value={toDate}
                  min={fromDate || todayYmd()}
                  onChange={(e) => setToDate(e.target.value)}
                  required
                />
              </label>
            </div>
            <label>
              Note (optional)
              <input
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder={mode === "AVAILABLE" ? "Clinic extra OPD" : "Leave / conference"}
              />
            </label>
            <button type="submit" disabled={saving}>
              {saving ? "Saving…" : mode === "AVAILABLE" ? "Save available dates" : "Save blocked dates"}
            </button>
          </form>

          <div className="panel stack">
            <h2>What patients will see</h2>
            <p className="muted" style={{ margin: 0 }}>
              Click a day to open or close it. Green days can be booked.
            </p>
            {loadingDays ? (
              <p>Loading dates…</p>
            ) : days.length === 0 ? (
              <p className="muted">No dates loaded.</p>
            ) : (
              <div className="doctor-avail-list">
                {days.map((d) => (
                  <button
                    key={d.date}
                    type="button"
                    className={
                      d.available
                        ? "doctor-avail-day is-available"
                        : "doctor-avail-day is-unavailable"
                    }
                    disabled={saving}
                    onClick={() => void onToggleDay(d)}
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
        </div>
      )}

      {selectedDoctorId && (
        <div className="panel" style={{ marginTop: "1rem" }}>
          <h2>Saved date rules</h2>
          <table className="table">
            <thead>
              <tr>
                <th>Dates</th>
                <th>Type</th>
                <th>Note</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {blocks.map((a) => (
                <tr key={a.id}>
                  <td>{formatInclusiveRange(a.startsAt, a.endsAt)}</td>
                  <td>
                    <span className={`badge ${a.availabilityType}`}>{a.availabilityType}</span>
                  </td>
                  <td>{a.reason || "—"}</td>
                  <td>
                    <button type="button" className="secondary" onClick={() => void onDeleteBlock(a.id)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
              {blocks.length === 0 && (
                <tr>
                  <td colSpan={4}>No extra date rules yet. Weekly hours still apply.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
