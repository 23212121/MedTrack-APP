import { FormEvent, useEffect, useMemo, useState } from "react";
import { api, FeeRule, HospitalDoctor } from "../api";
import CheckboxMultiSelect from "../components/CheckboxMultiSelect";
import { CLINICAL_DEPARTMENTS, mergeDepartmentOptions } from "../clinicalDepartments";
import { session } from "../dl/MedTrackSession";
import { toast } from "../toast";

function defaultRule(doctorId: string, consultFee?: number): FeeRule {
  return {
    id: `fee-${doctorId}`,
    doctorId,
    baseConsultFee: consultFee && consultFee > 0 ? consultFee : 500,
    fixedConsultMinutes: 15,
    overtimeFeeAmount: 200,
    overtimeFeePerBlockMinutes: 15,
    currency: "INR",
  };
}

function doctorDept(d: HospitalDoctor) {
  return (d.department || d.specialization || "").trim();
}

export default function FeesPage() {
  const hospitalId = session.getHospitalId();
  const hospitalName = session.getHospitalName();
  const [doctors, setDoctors] = useState<HospitalDoctor[]>([]);
  const [departments, setDepartments] = useState<string[]>(() => [...CLINICAL_DEPARTMENTS]);
  const [selectedDepartments, setSelectedDepartments] = useState<string[]>([]);
  const [selectedDoctorId, setSelectedDoctorId] = useState("");
  const [rule, setRule] = useState<FeeRule | null>(null);
  const [minutes, setMinutes] = useState(40);
  const [preview, setPreview] = useState<Record<string, number | string> | null>(null);
  const [error, setError] = useState("");
  const [loadingDoctors, setLoadingDoctors] = useState(true);
  const [loadingRule, setLoadingRule] = useState(false);

  const selectedDoctor = useMemo(
    () => doctors.find((d) => d.doctorId === selectedDoctorId) ?? null,
    [doctors, selectedDoctorId],
  );

  const filteredDoctors = useMemo(() => {
    if (selectedDepartments.length === 0) return doctors;
    const wanted = new Set(selectedDepartments.map((n) => n.toLowerCase()));
    return doctors.filter((d) => wanted.has(doctorDept(d).toLowerCase()));
  }, [doctors, selectedDepartments]);

  async function loadHospital() {
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
      const fromDoctors = rows.map((d) => doctorDept(d)).filter(Boolean);
      setDepartments(mergeDepartmentOptions(CLINICAL_DEPARTMENTS, fromDoctors));
      setLoadingDoctors(false);
      const [hospital, deptData] = await Promise.all([
        api.hospital(hospitalId).catch(() => ({ hospital: { departments: [] as string[] } })),
        api.departmentsByHospital(hospitalId).catch(() => ({ departments: [] })),
      ]);
      const fromHospital = hospital.hospital?.departments ?? [];
      const fromTable = (deptData.departments ?? [])
        .map((d) => d.name || d.value || "")
        .filter(Boolean);
      setDepartments(
        mergeDepartmentOptions(CLINICAL_DEPARTMENTS, fromHospital, fromTable, fromDoctors),
      );
    } catch (e) {
      setDoctors([]);
      setDepartments([...CLINICAL_DEPARTMENTS]);
      setError(e instanceof Error ? e.message : "Failed to load doctors");
      setLoadingDoctors(false);
    }
  }

  async function loadRule(doctor: HospitalDoctor) {
    setLoadingRule(true);
    setError("");
    setPreview(null);
    try {
      const saved = await api.fees(doctor.doctorId);
      const base =
        saved.baseConsultFee && saved.baseConsultFee > 0
          ? saved.baseConsultFee
          : doctor.consultationFee && doctor.consultationFee > 0
            ? doctor.consultationFee
            : 500;
      setRule({
        ...saved,
        doctorId: doctor.doctorId,
        baseConsultFee: base,
      });
    } catch {
      setRule(defaultRule(doctor.doctorId, doctor.consultationFee));
    } finally {
      setLoadingRule(false);
    }
  }

  useEffect(() => {
    void loadHospital();
  }, [hospitalId]);

  useEffect(() => {
    if (selectedDoctorId && !filteredDoctors.some((d) => d.doctorId === selectedDoctorId)) {
      setSelectedDoctorId("");
      setRule(null);
      setPreview(null);
    }
  }, [filteredDoctors, selectedDoctorId]);

  function toggleDepartment(name: string) {
    setSelectedDepartments((prev) =>
      prev.includes(name) ? prev.filter((n) => n !== name) : [...prev, name],
    );
  }

  function onPickDoctor(doctorId: string) {
    const doctor = doctors.find((d) => d.doctorId === doctorId);
    setSelectedDoctorId(doctorId);
    if (doctor) void loadRule(doctor);
  }

  async function onSave(e: FormEvent) {
    e.preventDefault();
    if (!rule || !selectedDoctorId) return;
    setError("");
    try {
      const saved = await api.saveFees(selectedDoctorId, {
        ...rule,
        clinicId: hospitalId || undefined,
        hospitalId: hospitalId ? Number(hospitalId) : undefined,
      });
      setRule({ ...saved, doctorId: selectedDoctorId });
      toast.success(
        `Fee rules saved for ${selectedDoctor?.doctorName || selectedDoctorId}. Overtime applies after fixed consult minutes.`,
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    }
  }

  async function onPreview() {
    if (!rule) return;
    const over = Math.max(0, minutes - rule.fixedConsultMinutes);
    const block = Math.max(1, rule.overtimeFeePerBlockMinutes);
    const blocks = over === 0 ? 0 : Math.ceil(over / block);
    const overtimeFee = blocks * rule.overtimeFeeAmount;
    setPreview({
      currency: rule.currency,
      baseFee: rule.baseConsultFee,
      overtimeMinutes: over,
      overtimeFee,
      totalFee: rule.baseConsultFee + overtimeFee,
    });
  }

  return (
    <section>
      <h1>Extra charge after fixed time</h1>
      <p className="lead">
        Choose a department and a doctor from{" "}
        {hospitalName || "this hospital"}, then set consult and overtime fees for that doctor.
        Base consult fee covers a fixed duration. Extra blocks after that add overtime charges
        and trigger SMS/email when the visit completes.
      </p>
      {error && <div className="msg error">{error}</div>}

      <div className="panel stack fees-picker">
        <CheckboxMultiSelect
          label="Department"
          options={departments}
          selected={selectedDepartments}
          onToggle={toggleDepartment}
          placeholder="All departments"
          loading={loadingDoctors}
          emptyText="No departments found for this hospital."
        />
        <p className="muted" style={{ margin: 0 }}>
          {selectedDepartments.length === 0
            ? "Showing every doctor in this hospital. Select one or more departments to narrow the list."
            : `Showing doctors in ${selectedDepartments.join(", ")}.`}
        </p>
      </div>

      <div className="fees-layout">
        <div className="panel stack">
          <h2>Doctors</h2>
          {loadingDoctors ? (
            <p className="muted">Loading doctors…</p>
          ) : filteredDoctors.length === 0 ? (
            <p className="muted">
              {selectedDepartments.length
                ? "No doctors in the selected department(s) at this hospital."
                : "No doctors registered for this hospital."}
            </p>
          ) : (
            <div className="fees-doctor-list" role="listbox" aria-label="Doctors">
              {filteredDoctors.map((d) => {
                const active = d.doctorId === selectedDoctorId;
                const dept = doctorDept(d) || "—";
                return (
                  <button
                    key={d.doctorId}
                    type="button"
                    role="option"
                    aria-selected={active}
                    className={active ? "fees-doctor-item is-selected" : "fees-doctor-item"}
                    onClick={() => onPickDoctor(d.doctorId)}
                  >
                    <strong>{d.doctorName}</strong>
                    <span>{dept}</span>
                    {d.consultationFee != null ? (
                      <em>Profile fee INR {d.consultationFee}</em>
                    ) : null}
                  </button>
                );
              })}
            </div>
          )}
        </div>

        <div className="grid-2 fees-rule-grid">
          {loadingRule ? (
            <p>Loading fee rules…</p>
          ) : rule && selectedDoctor ? (
            <form className="panel stack" onSubmit={onSave}>
              <h2>Fee rule — {selectedDoctor.doctorName}</h2>
              <p className="muted" style={{ margin: 0 }}>
                {doctorDept(selectedDoctor) || "Department not set"}
                {hospitalName ? ` · ${hospitalName}` : ""}
              </p>
              <label>
                Base consult fee
                <input
                  type="number"
                  min={0}
                  value={rule.baseConsultFee}
                  onChange={(e) => setRule({ ...rule, baseConsultFee: Number(e.target.value) })}
                />
              </label>
              <label>
                Fixed consult minutes
                <input
                  type="number"
                  min={1}
                  value={rule.fixedConsultMinutes}
                  onChange={(e) =>
                    setRule({ ...rule, fixedConsultMinutes: Number(e.target.value) })
                  }
                />
              </label>
              <label>
                Overtime fee per block
                <input
                  type="number"
                  min={0}
                  value={rule.overtimeFeeAmount}
                  onChange={(e) => setRule({ ...rule, overtimeFeeAmount: Number(e.target.value) })}
                />
              </label>
              <label>
                Block size (minutes)
                <input
                  type="number"
                  min={1}
                  value={rule.overtimeFeePerBlockMinutes}
                  onChange={(e) =>
                    setRule({ ...rule, overtimeFeePerBlockMinutes: Number(e.target.value) })
                  }
                />
              </label>
              <label>
                Currency
                <input
                  value={rule.currency}
                  onChange={(e) => setRule({ ...rule, currency: e.target.value })}
                />
              </label>
              <button type="submit">Save fee rules</button>
            </form>
          ) : (
            <div className="panel">
              <h2>Fee rule</h2>
              <p className="muted">Select a doctor to set consult and overtime fees.</p>
            </div>
          )}

          <div className="panel stack">
            <h2>Preview overtime</h2>
            <label>
              Actual consult minutes
              <input
                type="number"
                min={1}
                value={minutes}
                onChange={(e) => setMinutes(Number(e.target.value))}
                disabled={!selectedDoctorId}
              />
            </label>
            <button type="button" onClick={onPreview} disabled={!selectedDoctorId || !rule}>
              Calculate
            </button>
            {preview && (
              <div className="stats">
                <div className="stat">
                  <strong>
                    {preview.currency} {preview.baseFee}
                  </strong>
                  <span>Base</span>
                </div>
                <div className="stat">
                  <strong>{preview.overtimeMinutes}m</strong>
                  <span>Overtime</span>
                </div>
                <div className="stat">
                  <strong>
                    {preview.currency} {preview.overtimeFee}
                  </strong>
                  <span>Extra charge</span>
                </div>
                <div className="stat">
                  <strong>
                    {preview.currency} {preview.totalFee}
                  </strong>
                  <span>Total</span>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
