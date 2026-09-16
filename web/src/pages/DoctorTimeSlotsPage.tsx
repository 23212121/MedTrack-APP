import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, ScheduleSlot } from "../api";
import { session } from "../dl/MedTrackSession";
import { toast } from "../toast";

const DAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

type SlotDraft = {
  key: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  slotMinutes: number;
};

function newSlotKey() {
  return `slot-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

function emptySlot(): SlotDraft {
  return {
    key: newSlotKey(),
    dayOfWeek: 1,
    startTime: "09:00",
    endTime: "13:00",
    slotMinutes: 15,
  };
}

function toDrafts(schedules: ScheduleSlot[], doctorId: string): SlotDraft[] {
  return schedules
    .filter((s) => !s.doctorId || s.doctorId === doctorId)
    .map((s) => ({
      key: s.id || newSlotKey(),
      dayOfWeek: s.dayOfWeek,
      startTime: s.startTime,
      endTime: s.endTime,
      slotMinutes: s.slotMinutes,
    }));
}

export default function DoctorTimeSlotsPage() {
  const isDoctor = session.isDoctor();
  const [doctorId, setDoctorId] = useState((session.getUserId() || "").trim());
  const [slots, setSlots] = useState<SlotDraft[]>([]);
  const [loading, setLoading] = useState(false);
  const [savingSlots, setSavingSlots] = useState(false);
  const [error, setError] = useState("");

  async function loadSlots() {
    if (!isDoctor) {
      setError("Sign in with a doctor User ID to manage time slots.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      const id = (session.getUserId() || "").trim();
      setDoctorId(id);
      if (!id) {
        setSlots([]);
        setError("Doctor session is required to load time slots.");
        return;
      }
      const weekly = await api.weekly(id);
      setSlots(toDrafts(weekly.schedules ?? [], id));
    } catch (e) {
      setSlots([]);
      setError(e instanceof Error ? e.message : "Failed to load time slots");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadSlots();
  }, [isDoctor]);

  function updateSlot(key: string, patch: Partial<SlotDraft>) {
    setSlots((rows) => rows.map((row) => (row.key === key ? { ...row, ...patch } : row)));
  }

  function addSlot() {
    setSlots((rows) => [...rows, emptySlot()]);
  }

  async function saveSlots() {
    if (!doctorId) {
      setError("Doctor session is required to save time slots.");
      return;
    }
    if (slots.length === 0) {
      setError("Add at least one time slot before saving.");
      return;
    }
    for (const row of slots) {
      if (!row.startTime || !row.endTime) {
        setError("Each slot needs a start and end time.");
        return;
      }
      if (row.endTime <= row.startTime) {
        setError("Slot end time must be after the start time.");
        return;
      }
    }
    setSavingSlots(true);
    setError("");
    try {
      const hospitalId = session.getHospitalIdNumber();
      const saved = await api.saveWeekly(
        doctorId,
        slots.map((row) => ({
          dayOfWeek: row.dayOfWeek,
          startTime: row.startTime,
          endTime: row.endTime,
          slotMinutes: row.slotMinutes || 15,
          hospitalId: hospitalId || undefined,
        })),
      );
      setSlots(toDrafts(saved.schedules ?? [], doctorId));
      toast.success("Your time slots were saved.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save time slots");
    } finally {
      setSavingSlots(false);
    }
  }

  return (
    <section className="doctor-portal-page">
      <p className="muted" style={{ marginBottom: "0.4rem" }}>
        <Link to="/doctor-portal">← Dashboard</Link>
      </p>
      <h1>Time slots</h1>
      <p className="lead">
        Your weekly windows only. Add, update, and save the slots patients can book.
      </p>

      {error && <div className="msg error">{error}</div>}

      <div className="panel stack">
        <div className="row doctor-portal-summary">
          <h2 style={{ margin: 0, fontSize: "1.05rem" }}>Assigned time slots</h2>
          <span className="badge">{slots.length} slots</span>
          <button type="button" className="secondary" onClick={() => void loadSlots()} disabled={loading}>
            {loading ? "Loading…" : "Refresh"}
          </button>
        </div>
        <p className="muted" style={{ margin: 0 }}>
          Other doctors’ slots are not listed here.
        </p>
        <table className="table">
          <thead>
            <tr>
              <th>Day</th>
              <th>Start</th>
              <th>End</th>
              <th>Slot (min)</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {slots.map((row) => (
              <tr key={row.key}>
                <td>
                  <select
                    value={row.dayOfWeek}
                    onChange={(e) => updateSlot(row.key, { dayOfWeek: Number(e.target.value) })}
                  >
                    {DAYS.map((d, i) => (
                      <option key={d} value={i}>
                        {d}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <input
                    type="time"
                    value={row.startTime}
                    onChange={(e) => updateSlot(row.key, { startTime: e.target.value })}
                  />
                </td>
                <td>
                  <input
                    type="time"
                    value={row.endTime}
                    onChange={(e) => updateSlot(row.key, { endTime: e.target.value })}
                  />
                </td>
                <td>
                  <input
                    type="number"
                    min={5}
                    max={60}
                    value={row.slotMinutes}
                    onChange={(e) => updateSlot(row.key, { slotMinutes: Number(e.target.value) })}
                  />
                </td>
                <td>
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => setSlots((rows) => rows.filter((s) => s.key !== row.key))}
                  >
                    Remove
                  </button>
                </td>
              </tr>
            ))}
            {slots.length === 0 && (
              <tr>
                <td colSpan={5} className="muted">
                  {loading ? "Loading weekly slots…" : "No weekly slots saved yet. Add a slot below."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
        <div className="row">
          <button type="button" className="secondary" onClick={addSlot} disabled={!isDoctor}>
            Add new slot
          </button>
          <button type="button" onClick={() => void saveSlots()} disabled={savingSlots || !isDoctor}>
            {savingSlots ? "Saving…" : "Update time slots"}
          </button>
        </div>
      </div>
    </section>
  );
}
