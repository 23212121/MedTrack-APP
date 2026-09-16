import { useEffect, useMemo, useState } from "react";
import { api, HospitalDoctor, Visit } from "../api";
import { toast } from "../toast";

function doctorLabel(d: HospitalDoctor) {
  const dept = d.department?.trim() || d.specialization?.trim() || "General";
  return `${d.doctorName} — ${dept}`;
}

export default function CheckInPage() {
  const [visits, setVisits] = useState<Visit[]>([]);
  const [doctors, setDoctors] = useState<HospitalDoctor[]>([]);
  const [selectedDoctorId, setSelectedDoctorId] = useState("");
  const [loadingDoctors, setLoadingDoctors] = useState(false);
  const [loadingBoard, setLoadingBoard] = useState(false);
  const [error, setError] = useState("");
  const [busyId, setBusyId] = useState("");

  const selectedDoctor = useMemo(
    () => doctors.find((d) => d.doctorId === selectedDoctorId) ?? null,
    [doctors, selectedDoctorId]
  );

  async function loadDoctors() {
    setLoadingDoctors(true);
    setError("");
    try {
      // /api/doctors returns doctorName, department, hospitalId for dropdown + backend filter
      const data = await api.doctors();
      const rows = (data.doctors ?? []) as unknown as HospitalDoctor[];
      setDoctors(
        rows
          .filter((d) => d.doctorId && d.hospitalId != null)
          .map((d) => ({
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
          }))
      );
    } catch (e) {
      setDoctors([]);
      setError(e instanceof Error ? e.message : "Failed to load doctors");
    } finally {
      setLoadingDoctors(false);
    }
  }

  async function loadBoard(doctor: HospitalDoctor | null) {
    if (!doctor?.doctorId || doctor.hospitalId == null) {
      setVisits([]);
      return;
    }
    setLoadingBoard(true);
    setError("");
    try {
      // hospitalId + doctorId are sent to the backend only (not shown in the UI)
      const data = await api.checkInBoard({
        hospitalId: doctor.hospitalId,
        doctorId: doctor.doctorId,
      });
      setVisits(data.visits);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load board");
      setVisits([]);
    } finally {
      setLoadingBoard(false);
    }
  }

  useEffect(() => {
    void loadDoctors();
  }, []);

  useEffect(() => {
    void loadBoard(selectedDoctor);
  }, [selectedDoctorId, doctors]);

  useEffect(() => {
    if (!selectedDoctor) return;
    const t = setInterval(() => {
      void loadBoard(selectedDoctor);
    }, 15000);
    return () => clearInterval(t);
  }, [selectedDoctorId, doctors]);

  async function checkIn(id: string) {
    setBusyId(id);
    setError("");
    try {
      const v = await api.checkIn(id);
      toast.success(
        `Checked in ${v.patientName} — token ${v.tokenNumber}. SMS + email sent.`
      );
      await loadBoard(selectedDoctor);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Check-in failed");
    } finally {
      setBusyId("");
    }
  }

  return (
    <section>
      <h1>Patient check-in</h1>
      {error && <div className="msg error">{error}</div>}

      <div className="panel stack checkin-filters">
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
                  ? "No doctors available"
                  : "Select doctor"}
            </option>
            {doctors.map((d) => (
              <option key={d.doctorId} value={d.doctorId}>
                {doctorLabel(d)}
              </option>
            ))}
          </select>
        </label>
        <div className="row" style={{ justifyContent: "space-between" }}>
          <span className="field-hint">
            {loadingBoard
              ? "Loading patients…"
              : selectedDoctor
                ? `${visits.length} patient(s)`
                : "Select a doctor to load patients"}
          </span>
          <button
            type="button"
            className="secondary"
            disabled={!selectedDoctor || loadingBoard}
            onClick={() => loadBoard(selectedDoctor)}
          >
            Refresh
          </button>
        </div>
      </div>

      <div className="panel">
        <table className="table">
          <thead>
            <tr>
              <th>Patient</th>
              <th>Phone</th>
              <th>Doctor</th>
              <th>Scheduled</th>
              <th>Status</th>
              <th>Token</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {visits.map((v) => (
              <tr key={v.id}>
                <td>{v.patientName}</td>
                <td>{v.patientPhone}</td>
                <td>{v.doctorName}</td>
                <td>
                  {v.scheduledStart
                    ? new Date(v.scheduledStart).toLocaleString()
                    : "—"}
                </td>
                <td>
                  <span className={`badge ${v.status}`}>{v.status}</span>
                </td>
                <td>{v.tokenNumber ?? "—"}</td>
                <td>
                  {v.status === "BOOKED" || v.status === "CALLED" ? (
                    <button
                      disabled={busyId === v.id}
                      onClick={() => checkIn(v.id)}
                    >
                      Check in
                    </button>
                  ) : (
                    "—"
                  )}
                </td>
              </tr>
            ))}
            {visits.length === 0 && (
              <tr>
                <td colSpan={7}>
                  {!selectedDoctor
                    ? "Select a doctor to load patients for check-in."
                    : "No patients booked for this doctor."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
