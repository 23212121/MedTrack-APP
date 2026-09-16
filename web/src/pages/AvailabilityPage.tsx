import { FormEvent, useEffect, useMemo, useState } from "react";
import { api, Availability, SchedulePerson } from "../api";
import PeopleMultiSelect from "../components/PeopleMultiSelect";
import { session } from "../dl/MedTrackSession";
import { loadHospitalPeople } from "../peopleRoster";
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

/** Inclusive calendar dates → ISO instants (end is midnight after the last day). */
function inclusiveRangeToInstants(fromYmd: string, toYmd: string) {
  const start = new Date(`${fromYmd}T00:00:00`);
  const endExclusive = new Date(`${toYmd}T00:00:00`);
  endExclusive.setDate(endExclusive.getDate() + 1);
  return { startsAt: start.toISOString(), endsAt: endExclusive.toISOString() };
}

function listWindow() {
  return {
    from: new Date(`${addDaysYmd(todayYmd(), -7)}T00:00:00`).toISOString(),
    to: new Date(`${addDaysYmd(todayYmd(), 90)}T00:00:00`).toISOString(),
  };
}

function formatInclusiveRange(startsAt: string, endsAt: string) {
  const start = new Date(startsAt);
  const last = new Date(endsAt);
  last.setMilliseconds(last.getMilliseconds() - 1);
  return `${start.toLocaleDateString("en-GB")} – ${last.toLocaleDateString("en-GB")}`;
}

export default function AvailabilityPage() {
  const [people, setPeople] = useState<SchedulePerson[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [items, setItems] = useState<Availability[]>([]);
  const [type, setType] = useState("BUSY");
  const [reason, setReason] = useState("");
  const [fromDate, setFromDate] = useState(todayYmd());
  const [toDate, setToDate] = useState(addDaysYmd(todayYmd(), 3));
  const [error, setError] = useState("");
  const [loadingPeople, setLoadingPeople] = useState(true);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const selectedPeople = useMemo(
    () => people.filter((p) => selectedIds.includes(p.id)),
    [people, selectedIds],
  );

  const nameById = useMemo(() => {
    const map = new Map<string, string>();
    for (const p of people) map.set(p.id, p.name);
    return map;
  }, [people]);

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

  async function loadBlocks() {
    const hospitalId = session.getHospitalId();
    if (!hospitalId) {
      setItems([]);
      return;
    }
    setLoading(true);
    setError("");
    try {
      const { from, to } = listWindow();
      const data = await api.hospitalAvailability(hospitalId, from, to);
      setItems(data.availability ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadPeople();
    void loadBlocks();
  }, []);

  function togglePerson(id: string) {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    );
  }

  async function onAdd(e: FormEvent) {
    e.preventDefault();
    setError("");
    const hospitalId = session.getHospitalId();
    if (!hospitalId) {
      setError("Hospital ID is required. Sign in with a hospital account.");
      return;
    }
    if (selectedIds.length === 0) {
      setError("Select at least one doctor.");
      return;
    }
    if (!fromDate || !toDate) {
      setError("Choose a start date and end date.");
      return;
    }
    if (toDate < fromDate) {
      setError("End date must be on or after the start date.");
      return;
    }
    setSaving(true);
    try {
      const { startsAt, endsAt } = inclusiveRangeToInstants(fromDate, toDate);
      const payload = {
        hospitalId: Number(hospitalId),
        startsAt,
        endsAt,
        availabilityType: type,
        reason,
      };
      try {
        await api.addAvailabilityBatch({ personIds: selectedIds, ...payload });
      } catch {
        await Promise.all(
          selectedIds.map((doctorId) => api.addAvailability({ doctorId, ...payload })),
        );
      }
      const names = selectedPeople.map((p) => p.name).join(", ");
      toast.success(
        `Busy schedule saved for ${names} (${fromDate.split("-").reverse().join("/")} to ${toDate.split("-").reverse().join("/")}). Patients cannot book those dates.`,
      );
      setReason("");
      await loadBlocks();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    } finally {
      setSaving(false);
    }
  }

  const visibleItems =
    selectedIds.length === 0
      ? items
      : items.filter((a) => selectedIds.includes(a.doctorId));

  return (
    <section>
      <h1>Availability & busy blocks</h1>
      {error && <div className="msg error">{error}</div>}
      <div className="grid-2">
        <form className="panel stack" onSubmit={onAdd}>
          <h2>Add block</h2>
          <PeopleMultiSelect
            people={people}
            selectedIds={selectedIds}
            onToggle={togglePerson}
            loading={loadingPeople}
            label="Doctors and staff"
          />
          <label>
            Type
            <select value={type} onChange={(e) => setType(e.target.value)}>
              <option value="BUSY">Busy</option>
              <option value="LEAVE">Leave</option>
              <option value="BLOCKED">Blocked</option>
              <option value="AVAILABLE">Extra available</option>
            </select>
          </label>
          <div className="grid-2">
            <label>
              From date
              <input
                type="date"
                value={fromDate}
                onChange={(e) => setFromDate(e.target.value)}
                required
              />
            </label>
            <label>
              To date
              <input
                type="date"
                value={toDate}
                min={fromDate}
                onChange={(e) => setToDate(e.target.value)}
                required
              />
            </label>
          </div>
          <label>
            Reason
            <input
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Surgery / meeting"
            />
          </label>
          <button type="submit" disabled={saving || selectedIds.length === 0}>
            {saving ? "Saving…" : "Save block"}
          </button>
        </form>
        <div className="panel">
          <h2>Saved blocks</h2>
          {loading ? (
            <p>Loading…</p>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Doctor</th>
                  <th>Dates</th>
                  <th>Type</th>
                  <th>Reason</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {visibleItems.map((a) => (
                  <tr key={a.id}>
                    <td>{nameById.get(a.doctorId) || a.doctorId}</td>
                    <td>{formatInclusiveRange(a.startsAt, a.endsAt)}</td>
                    <td>
                      <span className={`badge ${a.availabilityType}`}>{a.availabilityType}</span>
                    </td>
                    <td>{a.reason || "—"}</td>
                    <td>
                      <button
                        type="button"
                        className="secondary"
                        onClick={async () => {
                          await api.deleteAvailability(a.id);
                          await loadBlocks();
                          toast.success("Busy block removed.");
                        }}
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
                {visibleItems.length === 0 && (
                  <tr>
                    <td colSpan={5}>No blocks yet.</td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </section>
  );
}
