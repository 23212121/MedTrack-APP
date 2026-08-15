"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";

type Patient = {
  id: string;
  phone: string;
  fullName: string;
  dateOfBirth: string | null;
  gender: string;
  email: string | null;
  visits: { id: string; status: string; scheduledStart: string | null }[];
};

type Doctor = {
  id: string;
  specialty: string | null;
  departmentId: string | null;
  user: { fullName: string };
  department: { id: string; name: string } | null;
};

type Visit = {
  id: string;
  status: string;
  reason: string | null;
  tokenNumber?: number | null;
  delayMinutes?: number;
  scheduledStart: string | null;
  createdByUserId?: string | null;
  isPatientBooking?: boolean;
  patient: { fullName: string; phone: string };
  doctor: { user: { fullName: string } } | null;
  events: { id: string; eventType: string; message: string | null; createdAt: string; toStatus: string | null }[];
  notes?: { id: string; noteType: string; body: string }[];
  notifications?: { id: string; channel: string; status: string; body: string }[];
};

function formatDob(value: string | null) {
  if (!value) return "—";
  return new Date(value).toLocaleDateString();
}

type DeskClientProps = {
  role: string;
  lockedDoctorId?: string | null;
  initialDoctors?: Doctor[];
  initialBoard?: Visit[];
};

export function DeskClient({
  role,
  lockedDoctorId = null,
  initialDoctors = [],
  initialBoard = [],
}: DeskClientProps) {
  const router = useRouter();
  const isDoctor = role === "DOCTOR";
  const [phone, setPhone] = useState("9876543210");
  const [patients, setPatients] = useState<Patient[]>([]);
  const [selectedPatientId, setSelectedPatientId] = useState<string>("");
  const [doctors, setDoctors] = useState<Doctor[]>(initialDoctors);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [searching, setSearching] = useState(false);
  const [creatingPatient, setCreatingPatient] = useState(false);
  const [savingVisit, setSavingVisit] = useState(false);
  const [createdVisit, setCreatedVisit] = useState<Visit | null>(null);

  const [newName, setNewName] = useState("");
  const [newDob, setNewDob] = useState("");
  const [newGender, setNewGender] = useState("UNKNOWN");
  const [newEmail, setNewEmail] = useState("");

  const [doctorId, setDoctorId] = useState(
    lockedDoctorId || initialDoctors[0]?.id || ""
  );
  const [boardDoctorId, setBoardDoctorId] = useState(
    lockedDoctorId || initialDoctors[0]?.id || ""
  );
  const [scheduledStart, setScheduledStart] = useState("");
  const [reason, setReason] = useState("");
  const [staffNote, setStaffNote] = useState("");
  const [status, setStatus] = useState<"BOOKED" | "CALLED">("BOOKED");
  const [board, setBoard] = useState<Visit[]>(initialBoard);
  const [boardBusy, setBoardBusy] = useState<string | null>(null);

  const selectedPatient = useMemo(
    () => patients.find((p) => p.id === selectedPatientId) ?? null,
    [patients, selectedPatientId]
  );

  const patientBookings = useMemo(
    () => board.filter((v) => v.isPatientBooking && v.status === "BOOKED"),
    [board]
  );

  const loadBoard = useCallback(
    async (signal?: AbortSignal) => {
      try {
        const params = new URLSearchParams({ open: "1" });
        const filterDoctor = lockedDoctorId || boardDoctorId;
        if (filterDoctor) params.set("doctorId", filterDoctor);

        const res = await fetch(`/api/visits?${params}`, { signal });
        if (res.status === 401) {
          router.push("/login");
          router.refresh();
          return;
        }
        const data = await res.json().catch(() => ({}));
        if (!res.ok) return;
        setBoard(
          (data.visits ?? []).map((v: Visit) => ({
            ...v,
            isPatientBooking:
              v.isPatientBooking ?? v.createdByUserId == null,
          }))
        );
      } catch (err) {
        if (err instanceof DOMException && err.name === "AbortError") return;
        if (signal?.aborted) return;
      }
    },
    [router, lockedDoctorId, boardDoctorId]
  );

  useEffect(() => {
    setBoard(initialBoard);
  }, [initialBoard]);

  useEffect(() => {
    if (initialDoctors.length) {
      setDoctors(initialDoctors);
      if (!doctorId) {
        const id = lockedDoctorId || initialDoctors[0]?.id || "";
        setDoctorId(id);
        setBoardDoctorId(id);
      }
    }
  }, [initialDoctors, lockedDoctorId, doctorId]);

  useEffect(() => {
    const ac = new AbortController();

    if (!initialDoctors.length && !isDoctor) {
      fetch("/api/doctors", { signal: ac.signal })
        .then((r) => {
          if (r.status === 401) {
            router.push("/login");
            router.refresh();
            return null;
          }
          return r.json();
        })
        .then((data) => {
          if (!data) return;
          setDoctors(data.doctors ?? []);
          if (data.doctors?.[0]) {
            setDoctorId((prev) => prev || data.doctors[0].id);
            setBoardDoctorId((prev) => prev || data.doctors[0].id);
          }
        })
        .catch(() => undefined);
    }

    const soon = new Date(Date.now() + 60 * 60 * 1000);
    soon.setMinutes(0, 0, 0);
    const local = new Date(soon.getTime() - soon.getTimezoneOffset() * 60000)
      .toISOString()
      .slice(0, 16);
    setScheduledStart(local);
    void loadBoard(ac.signal);
    const t = setInterval(() => void loadBoard(), 10_000);
    return () => {
      ac.abort();
      clearInterval(t);
    };
  }, [loadBoard, router, initialDoctors.length, isDoctor]);

  async function boardAction(
    visitId: string,
    action: "check-in" | "late" | "start" | "complete"
  ) {
    setBoardBusy(visitId);
    setError("");
    setMessage("");
    const res = await fetch(`/api/visits/${visitId}/${action}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: action === "late" ? JSON.stringify({ minutes: 15 }) : undefined,
    });
    const data = await res.json();
    setBoardBusy(null);
    if (!res.ok) {
      setError(data.error || "Action failed");
      return;
    }
    setMessage(
      action === "check-in"
        ? `Checked in — token ${data.visit?.tokenNumber}. Patient notified (SMS + email + app).`
        : action === "late"
          ? "Delay marked. Waiting patients notified on phone, email, and app."
          : action === "complete"
            ? "Completed. Next patient got timeline notification on all channels."
            : "Status updated. Patient notified."
    );
    await loadBoard();
  }

  async function searchPatients(e?: FormEvent) {
    e?.preventDefault();
    setSearching(true);
    setError("");
    setMessage("");
    setCreatedVisit(null);
    const res = await fetch(
      `/api/patients/search?phone=${encodeURIComponent(phone)}`
    );
    const data = await res.json();
    setSearching(false);
    if (!res.ok) {
      setError(data.error || "Search failed");
      return;
    }
    setPatients(data.patients ?? []);
    setSelectedPatientId(data.patients?.[0]?.id ?? "");
    if (!data.patients?.length) {
      setMessage("No patient found — create one below.");
      setCreatingPatient(true);
    } else {
      setCreatingPatient(false);
      setMessage(`${data.count} patient(s) matched. Confirm name/DOB, then create visit.`);
    }
  }

  async function createPatient(e: FormEvent) {
    e.preventDefault();
    setError("");
    const res = await fetch("/api/patients", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        phone,
        fullName: newName,
        dateOfBirth: newDob || undefined,
        gender: newGender,
        email: newEmail || undefined,
      }),
    });
    const data = await res.json();
    if (!res.ok) {
      setError(data.error || "Could not create patient");
      return;
    }
    setPatients((prev) => [data.patient, ...prev]);
    setSelectedPatientId(data.patient.id);
    setCreatingPatient(false);
    setMessage("Patient created. Continue to create visit.");
  }

  async function createVisit(e: FormEvent) {
    e.preventDefault();
    if (!selectedPatientId) {
      setError("Select or create a patient first");
      return;
    }
    setSavingVisit(true);
    setError("");
    setMessage("");
    const res = await fetch("/api/visits", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        patientId: selectedPatientId,
        doctorId: doctorId || undefined,
        scheduledStart: scheduledStart || undefined,
        reason: reason || undefined,
        staffNote: staffNote || undefined,
        status,
      }),
    });
    const data = await res.json();
    setSavingVisit(false);
    if (!res.ok) {
      setError(data.error || "Could not create visit");
      return;
    }
    setCreatedVisit(data.visit);
    setMessage(`Visit created (${data.visit.status}).`);
    setReason("");
    setStaffNote("");
    await loadBoard();
  }

  const boardDoctorName =
    doctors.find((d) => d.id === (lockedDoctorId || boardDoctorId))?.user
      .fullName ?? "Selected doctor";

  return (
    <div className="stack">
      <section className="card stack">
        <div className="row" style={{ justifyContent: "space-between" }}>
          <h2 style={{ margin: 0 }}>
            {isDoctor ? "Your appointment board" : "Doctor board"}
          </h2>
          <button type="button" className="secondary" onClick={() => void loadBoard()}>
            Refresh
          </button>
        </div>
        {!isDoctor ? (
          <label style={{ maxWidth: 360 }}>
            Show bookings for doctor
            <select
              value={boardDoctorId}
              onChange={(e) => {
                setBoardDoctorId(e.target.value);
                setDoctorId(e.target.value);
              }}
            >
              <option value="">All doctors</option>
              {doctors.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.user.fullName}
                </option>
              ))}
            </select>
          </label>
        ) : (
          <p className="muted" style={{ margin: 0 }}>
            Showing online patient bookings and desk visits for {boardDoctorName}.
          </p>
        )}
        {patientBookings.length > 0 ? (
          <p className="success" style={{ margin: 0 }}>
            {patientBookings.length} online patient booking(s) waiting for{" "}
            {boardDoctorId || lockedDoctorId ? boardDoctorName : "your clinic"}.
          </p>
        ) : (
          <p className="muted" style={{ margin: 0 }}>
            No online patient bookings yet for this doctor. New /book
            appointments appear here automatically.
          </p>
        )}
        {board.length === 0 ? (
          <p className="muted">No open visits for this doctor.</p>
        ) : (
          <ul className="list">
            {board.map((v) => (
              <li key={v.id}>
                <div className="row" style={{ justifyContent: "space-between" }}>
                  <div>
                    <strong>
                      #{v.tokenNumber ?? "—"} {v.patient.fullName}
                    </strong>{" "}
                    <span className="badge">{v.status}</span>
                    {v.isPatientBooking ? (
                      <span className="badge">Patient booking</span>
                    ) : null}
                    {v.delayMinutes ? (
                      <span className="badge">+{v.delayMinutes}m</span>
                    ) : null}
                    <div className="muted">
                      {v.doctor?.user.fullName ?? "Unassigned"} ·{" "}
                      {v.scheduledStart
                        ? new Date(v.scheduledStart).toLocaleString()
                        : "Unscheduled"}{" "}
                      · {v.patient.phone}
                    </div>
                  </div>
                  <div className="row">
                    {!isDoctor && ["BOOKED", "CALLED"].includes(v.status) ? (
                      <button
                        type="button"
                        disabled={boardBusy === v.id}
                        onClick={() => boardAction(v.id, "check-in")}
                      >
                        Check in
                      </button>
                    ) : null}
                    {["CHECKED_IN", "BOOKED"].includes(v.status) ? (
                      <button
                        type="button"
                        className="secondary"
                        disabled={boardBusy === v.id}
                        onClick={() => boardAction(v.id, "start")}
                      >
                        Start
                      </button>
                    ) : null}
                    {!["COMPLETED", "CANCELLED"].includes(v.status) ? (
                      <button
                        type="button"
                        className="secondary"
                        disabled={boardBusy === v.id}
                        onClick={() => boardAction(v.id, "late")}
                      >
                        +15 late
                      </button>
                    ) : null}
                    {["IN_CONSULT", "CHECKED_IN"].includes(v.status) ? (
                      <button
                        type="button"
                        disabled={boardBusy === v.id}
                        onClick={() => boardAction(v.id, "complete")}
                      >
                        Complete → next
                      </button>
                    ) : null}
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      {error ? <p className="error">{error}</p> : null}
      {message ? <p className="success">{message}</p> : null}

      {!isDoctor ? (
      <>
      <section className="card">
        <h2 style={{ marginTop: 0 }}>Patient phone</h2>
        <form className="row" onSubmit={searchPatients}>
          <input
            className="phone-input"
            style={{ flex: 1, minWidth: 220 }}
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="Enter phone number"
            aria-label="Patient phone"
          />
          <button type="submit" disabled={searching}>
            {searching ? "Searching…" : "Search"}
          </button>
        </form>
      </section>

      <div className="grid-2">
        <section className="card stack">
          <div className="row" style={{ justifyContent: "space-between" }}>
            <h2 style={{ margin: 0 }}>Matches</h2>
            <button
              type="button"
              className="secondary"
              onClick={() => setCreatingPatient((v) => !v)}
            >
              New patient
            </button>
          </div>

          {patients.length === 0 ? (
            <p className="muted">Search by phone to load patients.</p>
          ) : (
            <ul className="list">
              {patients.map((p) => (
                <li
                  key={p.id}
                  className={p.id === selectedPatientId ? "selected" : ""}
                  onClick={() => setSelectedPatientId(p.id)}
                  style={{ cursor: "pointer" }}
                >
                  <strong>{p.fullName}</strong>{" "}
                  <span className="badge">{p.gender}</span>
                  <div className="muted">
                    DOB {formatDob(p.dateOfBirth)} · {p.phone}
                  </div>
                  {p.visits?.[0] ? (
                    <div className="muted">
                      Last: {p.visits[0].status}
                    </div>
                  ) : null}
                </li>
              ))}
            </ul>
          )}

          {creatingPatient ? (
            <form className="stack" onSubmit={createPatient}>
              <h3 style={{ marginBottom: 0 }}>Create patient</h3>
              <label>
                Full name
                <input
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  required
                />
              </label>
              <label>
                Date of birth
                <input
                  type="date"
                  value={newDob}
                  onChange={(e) => setNewDob(e.target.value)}
                />
              </label>
              <label>
                Gender
                <select
                  value={newGender}
                  onChange={(e) => setNewGender(e.target.value)}
                >
                  <option value="UNKNOWN">Unknown</option>
                  <option value="FEMALE">Female</option>
                  <option value="MALE">Male</option>
                  <option value="OTHER">Other</option>
                </select>
              </label>
              <label>
                Email
                <input
                  type="email"
                  value={newEmail}
                  onChange={(e) => setNewEmail(e.target.value)}
                />
              </label>
              <button type="submit">Save patient</button>
            </form>
          ) : null}
        </section>

        <section className="card">
          <h2 style={{ marginTop: 0 }}>Create visit</h2>
          {selectedPatient ? (
            <p className="muted">
              For <strong>{selectedPatient.fullName}</strong> — confirm identity
              before saving.
            </p>
          ) : (
            <p className="muted">Select a patient to continue.</p>
          )}
          <form className="stack" onSubmit={createVisit}>
            <label>
              Doctor
              <select
                value={doctorId}
                onChange={(e) => setDoctorId(e.target.value)}
              >
                <option value="">—</option>
                {doctors.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.user.fullName}
                    {d.department ? ` · ${d.department.name}` : ""}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Scheduled start
              <input
                type="datetime-local"
                value={scheduledStart}
                onChange={(e) => setScheduledStart(e.target.value)}
              />
            </label>
            <label>
              Reason
              <input
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Fever, 2 days"
              />
            </label>
            <label>
              Staff note
              <textarea
                rows={2}
                value={staffNote}
                onChange={(e) => setStaffNote(e.target.value)}
                placeholder="Needs wheelchair"
              />
            </label>
            <label>
              Status on save
              <select
                value={status}
                onChange={(e) =>
                  setStatus(e.target.value as "BOOKED" | "CALLED")
                }
              >
                <option value="BOOKED">Booked (notify patient)</option>
                <option value="CALLED">Called (intake only)</option>
              </select>
            </label>
            <button type="submit" disabled={!selectedPatientId || savingVisit}>
              {savingVisit ? "Saving…" : "Save & track visit"}
            </button>
          </form>
        </section>
      </div>
      </>
      ) : null}

      {createdVisit ? (
        <section className="card stack">
          <h2 style={{ marginTop: 0 }}>Visit timeline</h2>
          <p>
            <span className="badge">{createdVisit.status}</span>{" "}
            {createdVisit.patient.fullName} ·{" "}
            {createdVisit.doctor?.user.fullName ?? "Unassigned"}
          </p>
          <div className="timeline">
            {createdVisit.events.map((ev) => (
              <div className="item" key={ev.id}>
                <time>{new Date(ev.createdAt).toLocaleString()}</time>
                <strong>{ev.eventType}</strong>
                {ev.toStatus ? ` → ${ev.toStatus}` : ""}
                <div className="muted">{ev.message}</div>
              </div>
            ))}
          </div>
          {createdVisit.notifications?.length ? (
            <div>
              <h3>Notifications</h3>
              <ul className="list">
                {createdVisit.notifications.map((n) => (
                  <li key={n.id}>
                    <span className="badge">
                      {n.channel} · {n.status}
                    </span>
                    <div className="muted">{n.body}</div>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}
