import { useEffect, useMemo, useState } from "react";
import {
  api,
  DOCTOR_ID,
  HospitalDoctor,
  SchedulePerson,
  ScheduleSlot,
  StaffMember,
  Visit,
} from "../api";
import { session } from "../dl/MedTrackSession";
import { buildPeople } from "../peopleRoster";
import { toast } from "../toast";

const DAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

function today() {
  const d = new Date();
  const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

export default function DoctorQueuePage() {
  const [visits, setVisits] = useState<Visit[]>([]);
  const [people, setPeople] = useState<SchedulePerson[]>([]);
  const [slots, setSlots] = useState<ScheduleSlot[]>([]);
  const [error, setError] = useState("");

  const nameById = useMemo(() => {
    const map = new Map<string, SchedulePerson>();
    for (const p of people) map.set(p.id, p);
    return map;
  }, [people]);

  async function load() {
    try {
      const hospitalId = session.getHospitalId();
      const [visitData, doctorData, staffData] = await Promise.all([
        api.visits(DOCTOR_ID, today()).catch(() => ({ visits: [] as Visit[] })),
        hospitalId
          ? api.doctorsByHospital(hospitalId).catch(() => api.doctors())
          : api.doctors(),
        api.employees().catch(() => ({ employees: [] as StaffMember[] })),
      ]);
      const doctors = ((doctorData as { doctors?: HospitalDoctor[] }).doctors ?? []).filter(
        (d) => d.doctorId,
      ) as HospitalDoctor[];
      const roster = buildPeople(doctors, staffData.employees ?? []);
      setPeople(roster);
      setVisits(visitData.visits ?? []);
      if (hospitalId) {
        const personIds = roster.map((p) => p.id);
        const scheduleData = await api.hospitalSchedules(hospitalId, personIds);
        setSlots(scheduleData.schedules ?? []);
      } else {
        setSlots([]);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load queue");
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function act(id: string, action: "start" | "late" | "complete") {
    setError("");
    try {
      const fn = action === "start" ? api.start : action === "late" ? api.late : api.complete;
      const v = await fn(id);
      if (action === "complete") {
        toast.success(
          `Completed. Total ${v.feeCurrency} ${v.totalFee}` +
            (v.overtimeFee ? ` (overtime ${v.feeCurrency} ${v.overtimeFee})` : "") +
            ". Patient notified by SMS + email.",
        );
      } else if (action === "late") {
        toast.success(`Marked +15 late. Delay alerts sent.`);
      } else {
        toast.success(`Consult started. Checkup notification sent.`);
      }
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Action failed");
    }
  }

  return (
    <section>
      <h1>Doctor queue</h1>
      {error && <div className="msg error">{error}</div>}

      <div className="panel">
        <h2>Assigned time slots</h2>
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Role</th>
              <th>Day</th>
              <th>Start</th>
              <th>End</th>
              <th>Slot (min)</th>
            </tr>
          </thead>
          <tbody>
            {slots.map((s) => {
              const person = nameById.get(s.doctorId);
              return (
                <tr key={s.id}>
                  <td>{person?.name || s.doctorId}</td>
                  <td>{person?.role || "—"}</td>
                  <td>{DAYS[s.dayOfWeek] ?? s.dayOfWeek}</td>
                  <td>{s.startTime}</td>
                  <td>{s.endTime}</td>
                  <td>{s.slotMinutes}</td>
                </tr>
              );
            })}
            {slots.length === 0 && (
              <tr>
                <td colSpan={6} className="muted">
                  No weekly slots saved yet. Set them on Schedules.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="panel">
        <h2>Today&apos;s visits</h2>
        <table className="table">
          <thead>
            <tr>
              <th>Token</th>
              <th>Patient</th>
              <th>Status</th>
              <th>Scheduled</th>
              <th>Fees</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {visits.map((v) => (
              <tr key={v.id}>
                <td>{v.tokenNumber ?? "—"}</td>
                <td>
                  {v.patientName}
                  <div style={{ color: "var(--muted)", fontSize: "0.8rem" }}>{v.reason}</div>
                </td>
                <td>
                  <span className={`badge ${v.status}`}>{v.status}</span>
                  {v.delayMinutes ? ` · +${v.delayMinutes}m` : ""}
                </td>
                <td>{v.scheduledStart ? new Date(v.scheduledStart).toLocaleTimeString() : "—"}</td>
                <td>
                  {v.totalFee != null ? `${v.feeCurrency} ${v.totalFee}` : "—"}
                </td>
                <td className="row">
                  {(v.status === "CHECKED_IN" || v.status === "BOOKED") && (
                    <button onClick={() => act(v.id, "start")}>Start</button>
                  )}
                  {v.status !== "COMPLETED" && v.status !== "CANCELLED" && (
                    <button className="warn" onClick={() => act(v.id, "late")}>
                      +15 late
                    </button>
                  )}
                  {(v.status === "IN_CONSULT" || v.status === "CHECKED_IN") && (
                    <button onClick={() => act(v.id, "complete")}>Complete</button>
                  )}
                </td>
              </tr>
            ))}
            {visits.length === 0 && (
              <tr>
                <td colSpan={6}>No visits today. Check-in a booked patient first.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
