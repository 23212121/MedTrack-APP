import { FormEvent, useEffect, useMemo, useState } from "react";
import { api, SchedulePerson, ScheduleSlot } from "../api";
import PeopleMultiSelect from "../components/PeopleMultiSelect";
import { session } from "../dl/MedTrackSession";
import { loadHospitalPeople } from "../peopleRoster";
import { toast } from "../toast";

const DAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

type Row = { dayOfWeek: number; startTime: string; endTime: string; slotMinutes: number };

function emptyRow(): Row {
  return { dayOfWeek: 1, startTime: "09:00", endTime: "13:00", slotMinutes: 15 };
}

function slotsToRows(schedules: ScheduleSlot[]): Row[] {
  return schedules.map((s) => ({
    dayOfWeek: s.dayOfWeek,
    startTime: s.startTime,
    endTime: s.endTime,
    slotMinutes: s.slotMinutes,
  }));
}

export default function SchedulesPage() {
  const [people, setPeople] = useState<SchedulePerson[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState("");
  const [loadingPeople, setLoadingPeople] = useState(true);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [saving, setSaving] = useState(false);

  const selectedPeople = useMemo(
    () => people.filter((p) => selectedIds.includes(p.id)),
    [people, selectedIds],
  );

  async function loadPeople() {
    setLoadingPeople(true);
    setError("");
    try {
      setPeople(await loadHospitalPeople());
    } catch (e) {
      setPeople([]);
      setError(e instanceof Error ? e.message : "Failed to load doctors and staff");
    } finally {
      setLoadingPeople(false);
    }
  }

  async function loadSlotsFor(ids: string[]) {
    if (ids.length !== 1) return;
    setLoadingSlots(true);
    setError("");
    try {
      const data = await api.weekly(ids[0]);
      const next = slotsToRows(data.schedules ?? []);
      setRows(next.length ? next : [emptyRow()]);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load schedule");
    } finally {
      setLoadingSlots(false);
    }
  }

  useEffect(() => {
    void loadPeople();
  }, []);

  function togglePerson(id: string) {
    setSelectedIds((prev) => {
      const next = prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id];
      if (next.length === 1) {
        void loadSlotsFor(next);
      } else if (next.length === 0) {
        setRows([]);
      } else if (rows.length === 0) {
        setRows([emptyRow()]);
      }
      return next;
    });
  }

  function addRow() {
    setRows((r) => [...r, emptyRow()]);
  }

  async function onSave(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (selectedIds.length === 0) {
      setError("Select at least one doctor or staff member.");
      return;
    }
    if (rows.length === 0) {
      setError("Add at least one time window.");
      return;
    }
    setSaving(true);
    try {
      const hospitalId = session.getHospitalId();
      const payload = rows.map((row) => ({
        ...row,
        hospitalId: hospitalId ? Number(hospitalId) : undefined,
      }));
      try {
        await api.saveWeeklyBatch(selectedIds, payload, hospitalId || undefined);
      } catch {
        await Promise.all(selectedIds.map((id) => api.saveWeekly(id, payload)));
      }
      const names = selectedPeople.map((p) => p.name).join(", ");
      toast.success(`Weekly schedule saved for ${names}.`);
      if (selectedIds.length === 1) await loadSlotsFor(selectedIds);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section>
      <h1>Doctor schedules</h1>
      {error && <div className="msg error">{error}</div>}

      <form className="panel stack" onSubmit={onSave}>
        <PeopleMultiSelect
          people={people}
          selectedIds={selectedIds}
          onToggle={togglePerson}
          loading={loadingPeople}
          label="Doctors and staff"
        />

        {loadingSlots ? (
          <p>Loading schedule…</p>
        ) : (
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
              {rows.map((row, idx) => (
                <tr key={idx}>
                  <td>
                    <select
                      value={row.dayOfWeek}
                      onChange={(e) => {
                        const next = [...rows];
                        next[idx] = { ...row, dayOfWeek: Number(e.target.value) };
                        setRows(next);
                      }}
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
                      onChange={(e) => {
                        const next = [...rows];
                        next[idx] = { ...row, startTime: e.target.value };
                        setRows(next);
                      }}
                    />
                  </td>
                  <td>
                    <input
                      type="time"
                      value={row.endTime}
                      onChange={(e) => {
                        const next = [...rows];
                        next[idx] = { ...row, endTime: e.target.value };
                        setRows(next);
                      }}
                    />
                  </td>
                  <td>
                    <input
                      type="number"
                      min={5}
                      max={60}
                      value={row.slotMinutes}
                      onChange={(e) => {
                        const next = [...rows];
                        next[idx] = { ...row, slotMinutes: Number(e.target.value) };
                        setRows(next);
                      }}
                    />
                  </td>
                  <td>
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => setRows(rows.filter((_, i) => i !== idx))}
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={5} className="muted">
                    {selectedIds.length === 0
                      ? "Select a doctor or staff member, then add a time window."
                      : "No windows yet. Click Add window."}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
        <div className="row" style={{ marginTop: "1rem" }}>
          <button type="button" className="secondary" onClick={addRow}>
            Add window
          </button>
          <button type="submit" disabled={saving || selectedIds.length === 0}>
            {saving ? "Saving…" : "Save schedule"}
          </button>
        </div>
      </form>
    </section>
  );
}
