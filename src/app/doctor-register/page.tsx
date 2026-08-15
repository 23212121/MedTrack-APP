"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { ChangeEvent, FormEvent, ReactNode, useEffect, useState } from "react";

const STORAGE_KEY = "medtrack_doctor_reg_id";
const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"] as const;

const STEPS = [
  { key: "personal", title: "1. Personal Information", table: "doctor_personal" },
  { key: "contact", title: "2. Contact Information", table: "doctor_contact" },
  { key: "professional", title: "3. Professional Information", table: "doctor_professional" },
  { key: "clinic", title: "4. Clinic / Hospital Information", table: "doctor_clinic" },
  { key: "identity", title: "5. Identity Documents", table: "doctor_identity" },
  { key: "bank", title: "6. Bank Details", table: "doctor_bank" },
  { key: "documents", title: "7. Documents Upload", table: "doctor_documents" },
] as const;

type StepKey = (typeof STEPS)[number]["key"];

type FormState = Record<string, string | string[] | boolean>;

const empty: FormState = {
  firstName: "", middleName: "", lastName: "", gender: "", dateOfBirth: "",
  profilePhoto: "", bloodGroup: "", maritalStatus: "",
  mobileNumber: "", alternateMobileNumber: "", email: "", emergencyContactNumber: "",
  residentialAddress: "", city: "", state: "", country: "India", postalCode: "",
  medicalRegistrationNumber: "", medicalCouncilName: "", registrationDate: "",
  registrationValidUntil: "", yearsOfExperience: "", currentDesignation: "",
  department: "", specialization: "", subSpecialization: "", qualification: "",
  medicalCollege: "", graduationYear: "",
  hospitalName: "", clinicName: "", hospitalId: "", branch: "",
  consultationType: "BOTH", consultationFee: "", followUpFee: "",
  availableDays: [], availableTimeSlots: "",
  aadhaarNumber: "", panNumber: "", passportNumber: "", governmentIdUpload: "",
  accountHolderName: "", bankName: "", accountNumber: "", ifscCode: "", upiId: "",
  medicalRegistrationCertificate: "", degreeCertificate: "", experienceCertificate: "",
  identityProof: "", addressProof: "", passportSizePhotograph: "", digitalSignature: "",
};

async function fileToStoredValue(file: File | undefined): Promise<string> {
  if (!file) return "";
  if (file.size <= 200_000 && file.type.startsWith("image/")) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result || ""));
      reader.onerror = () => reject(reader.error);
      reader.readAsDataURL(file);
    });
  }
  return file.name;
}

function Field({ label, children, optional }: { label: string; children: ReactNode; optional?: boolean }) {
  return (
    <label>
      {label}{optional ? " (optional)" : ""}
      {children}
    </label>
  );
}

export default function DoctorRegisterWizardPage() {
  const router = useRouter();
  const [step, setStep] = useState(0);
  const [doctorId, setDoctorId] = useState("");
  const [form, setForm] = useState<FormState>(empty);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [saving, setSaving] = useState(false);
  const [booting, setBooting] = useState(true);

  function setField(key: string, value: string | string[] | boolean) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  async function onFile(key: string, e: ChangeEvent<HTMLInputElement>) {
    setField(key, await fileToStoredValue(e.target.files?.[0]));
  }

  function toggleDay(day: string) {
    const cur = Array.isArray(form.availableDays) ? [...form.availableDays] : [];
    setField(
      "availableDays",
      cur.includes(day) ? cur.filter((d) => d !== day) : [...cur, day]
    );
  }

  async function ensureDoctorId(): Promise<string> {
    if (doctorId) return doctorId;
    const existing = localStorage.getItem(STORAGE_KEY);
    if (existing) {
      setDoctorId(existing);
      return existing;
    }
    const res = await fetch("/api/doctors/register", { method: "POST" });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || "Could not start registration");
    const id = String(data.doctorId);
    localStorage.setItem(STORAGE_KEY, id);
    setDoctorId(id);
    return id;
  }

  async function loadSection(id: string, section: StepKey) {
    const res = await fetch(`/api/doctors/${id}/sections/${section}`);
    const data = await res.json();
    if (!res.ok) return;
    const row = (data.data || {}) as Record<string, unknown>;
    setForm((prev) => {
      const next = { ...prev };
      for (const [k, v] of Object.entries(row)) {
        if (k === "doctorId" || k === "createdDate" || k === "updatedDate") continue;
        if (k === "availableDays" && typeof v === "string") {
          next.availableDays = v ? v.split(",") : [];
        } else if (v != null) {
          next[k] = String(v);
        }
      }
      return next;
    });
  }

  useEffect(() => {
    (async () => {
      try {
        const id = await ensureDoctorId();
        await loadSection(id, STEPS[0].key);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to start");
      } finally {
        setBooting(false);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!doctorId || booting) return;
    loadSection(doctorId, STEPS[step].key).catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [step]);

  function sectionPayload(section: StepKey): Record<string, unknown> {
    const days = Array.isArray(form.availableDays) ? form.availableDays.join(",") : "";
    const num = (k: string) => {
      const v = String(form[k] ?? "").trim();
      return v ? Number(v) : undefined;
    };
    const str = (k: string) => {
      const v = String(form[k] ?? "").trim();
      return v || undefined;
    };

    switch (section) {
      case "personal":
        return {
          firstName: str("firstName"), middleName: str("middleName"), lastName: str("lastName"),
          gender: str("gender"), dateOfBirth: str("dateOfBirth"), profilePhoto: str("profilePhoto"),
          bloodGroup: str("bloodGroup"), maritalStatus: str("maritalStatus"),
        };
      case "contact":
        return {
          mobileNumber: str("mobileNumber"), alternateMobileNumber: str("alternateMobileNumber"),
          email: str("email"), emergencyContactNumber: str("emergencyContactNumber"),
          residentialAddress: str("residentialAddress"), city: str("city"), state: str("state"),
          country: str("country"), postalCode: str("postalCode"),
        };
      case "professional":
        return {
          medicalRegistrationNumber: str("medicalRegistrationNumber"),
          medicalCouncilName: str("medicalCouncilName"), registrationDate: str("registrationDate"),
          registrationValidUntil: str("registrationValidUntil"),
          yearsOfExperience: num("yearsOfExperience"), currentDesignation: str("currentDesignation"),
          department: str("department"), specialization: str("specialization"),
          subSpecialization: str("subSpecialization"), qualification: str("qualification"),
          medicalCollege: str("medicalCollege"), graduationYear: num("graduationYear"),
        };
      case "clinic":
        return {
          hospitalName: str("hospitalName"), clinicName: str("clinicName"),
          hospitalId: num("hospitalId"), branch: str("branch"),
          consultationType: str("consultationType"), consultationFee: num("consultationFee"),
          followUpFee: num("followUpFee"), availableDays: days || undefined,
          availableTimeSlots: str("availableTimeSlots"),
        };
      case "identity":
        return {
          aadhaarNumber: str("aadhaarNumber"), panNumber: str("panNumber"),
          passportNumber: str("passportNumber"), governmentIdUpload: str("governmentIdUpload"),
        };
      case "bank":
        return {
          accountHolderName: str("accountHolderName"), bankName: str("bankName"),
          accountNumber: str("accountNumber"), ifscCode: str("ifscCode"), upiId: str("upiId"),
        };
      case "documents":
        return {
          medicalRegistrationCertificate: str("medicalRegistrationCertificate"),
          degreeCertificate: str("degreeCertificate"),
          experienceCertificate: str("experienceCertificate"),
          identityProof: str("identityProof"), addressProof: str("addressProof"),
          passportSizePhotograph: str("passportSizePhotograph"),
          digitalSignature: str("digitalSignature"),
        };
    }
  }

  async function saveCurrent() {
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const id = await ensureDoctorId();
      const section = STEPS[step].key;
      const res = await fetch(`/api/doctors/${id}/sections/${section}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(sectionPayload(section)),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Save failed");
      setMessage(data.message || "Saved. You can continue later.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Save failed");
      throw e;
    } finally {
      setSaving(false);
    }
  }

  async function onSave(e: FormEvent) {
    e.preventDefault();
    try {
      await saveCurrent();
    } catch {
      /* shown in error */
    }
  }

  async function goNext() {
    setError("");
    setMessage("");
    // Next is not mandatory to save — just move forward
    if (step < STEPS.length - 1) setStep((s) => s + 1);
    else {
      setMessage("Registration steps complete. You can return anytime to fill remaining sections.");
      setTimeout(() => router.push("/login"), 1800);
    }
  }

  function goPrev() {
    setError("");
    setMessage("");
    if (step > 0) setStep((s) => s - 1);
  }

  const current = STEPS[step];
  const days = Array.isArray(form.availableDays) ? form.availableDays : [];

  if (booting) {
    return (
      <main>
        <p className="muted">Preparing registration…</p>
      </main>
    );
  }

  return (
    <main style={{ maxWidth: 860 }}>
      <p className="muted" style={{ marginTop: 0 }}>
        <Link href="/login">← Back to login</Link>
      </p>
      <h1 className="hero-brand">Doctor registration</h1>

      <div className="wizard-steps">
        {STEPS.map((s, i) => (
          <button
            key={s.key}
            type="button"
            className={i === step ? "active" : undefined}
            onClick={() => setStep(i)}
          >
            {i + 1}
          </button>
        ))}
      </div>

      <form className="card stack form-block" onSubmit={onSave}>
        <h2 style={{ margin: 0 }}>{current.title}</h2>
        <p className="muted" style={{ margin: 0, fontSize: "0.85rem" }}>
          Saves to table <code>{current.table}</code>
        </p>

        {error ? <p className="error">{error}</p> : null}
        {message ? <p className="success">{message}</p> : null}

        <div className="grid-2">
          {current.key === "personal" && (
            <>
              <Field label="First Name"><input value={String(form.firstName)} onChange={(e) => setField("firstName", e.target.value)} /></Field>
              <Field label="Middle Name" optional><input value={String(form.middleName)} onChange={(e) => setField("middleName", e.target.value)} /></Field>
              <Field label="Last Name"><input value={String(form.lastName)} onChange={(e) => setField("lastName", e.target.value)} /></Field>
              <Field label="Gender">
                <select value={String(form.gender)} onChange={(e) => setField("gender", e.target.value)}>
                  <option value="">Select</option>
                  <option value="MALE">Male</option>
                  <option value="FEMALE">Female</option>
                  <option value="OTHER">Other</option>
                </select>
              </Field>
              <Field label="Date of Birth"><input type="date" value={String(form.dateOfBirth)} onChange={(e) => setField("dateOfBirth", e.target.value)} /></Field>
              <Field label="Profile Photo" optional><input type="file" accept="image/*" onChange={(e) => onFile("profilePhoto", e)} /></Field>
              <Field label="Blood Group" optional>
                <select value={String(form.bloodGroup)} onChange={(e) => setField("bloodGroup", e.target.value)}>
                  <option value="">—</option>
                  {["A+","A-","B+","B-","AB+","AB-","O+","O-"].map((g) => <option key={g} value={g}>{g}</option>)}
                </select>
              </Field>
              <Field label="Marital Status" optional>
                <select value={String(form.maritalStatus)} onChange={(e) => setField("maritalStatus", e.target.value)}>
                  <option value="">—</option>
                  <option value="SINGLE">Single</option>
                  <option value="MARRIED">Married</option>
                  <option value="DIVORCED">Divorced</option>
                  <option value="WIDOWED">Widowed</option>
                </select>
              </Field>
            </>
          )}

          {current.key === "contact" && (
            <>
              <Field label="Mobile Number"><input value={String(form.mobileNumber)} onChange={(e) => setField("mobileNumber", e.target.value)} /></Field>
              <Field label="Alternate Mobile" optional><input value={String(form.alternateMobileNumber)} onChange={(e) => setField("alternateMobileNumber", e.target.value)} /></Field>
              <Field label="Email"><input type="email" value={String(form.email)} onChange={(e) => setField("email", e.target.value)} /></Field>
              <Field label="Emergency Contact" optional><input value={String(form.emergencyContactNumber)} onChange={(e) => setField("emergencyContactNumber", e.target.value)} /></Field>
              <Field label="Residential Address"><textarea rows={2} value={String(form.residentialAddress)} onChange={(e) => setField("residentialAddress", e.target.value)} /></Field>
              <Field label="City"><input value={String(form.city)} onChange={(e) => setField("city", e.target.value)} /></Field>
              <Field label="State"><input value={String(form.state)} onChange={(e) => setField("state", e.target.value)} /></Field>
              <Field label="Country"><input value={String(form.country)} onChange={(e) => setField("country", e.target.value)} /></Field>
              <Field label="Postal Code"><input value={String(form.postalCode)} onChange={(e) => setField("postalCode", e.target.value)} /></Field>
            </>
          )}

          {current.key === "professional" && (
            <>
              <Field label="Medical Registration Number"><input value={String(form.medicalRegistrationNumber)} onChange={(e) => setField("medicalRegistrationNumber", e.target.value)} /></Field>
              <Field label="Medical Council Name"><input value={String(form.medicalCouncilName)} onChange={(e) => setField("medicalCouncilName", e.target.value)} /></Field>
              <Field label="Registration Date"><input type="date" value={String(form.registrationDate)} onChange={(e) => setField("registrationDate", e.target.value)} /></Field>
              <Field label="Valid Until" optional><input type="date" value={String(form.registrationValidUntil)} onChange={(e) => setField("registrationValidUntil", e.target.value)} /></Field>
              <Field label="Years of Experience" optional><input type="number" value={String(form.yearsOfExperience)} onChange={(e) => setField("yearsOfExperience", e.target.value)} /></Field>
              <Field label="Current Designation" optional><input value={String(form.currentDesignation)} onChange={(e) => setField("currentDesignation", e.target.value)} /></Field>
              <Field label="Department" optional><input value={String(form.department)} onChange={(e) => setField("department", e.target.value)} /></Field>
              <Field label="Specialization"><input value={String(form.specialization)} onChange={(e) => setField("specialization", e.target.value)} /></Field>
              <Field label="Sub-Specialization" optional><input value={String(form.subSpecialization)} onChange={(e) => setField("subSpecialization", e.target.value)} /></Field>
              <Field label="Qualification" optional><input value={String(form.qualification)} onChange={(e) => setField("qualification", e.target.value)} /></Field>
              <Field label="Medical College" optional><input value={String(form.medicalCollege)} onChange={(e) => setField("medicalCollege", e.target.value)} /></Field>
              <Field label="Graduation Year" optional><input type="number" value={String(form.graduationYear)} onChange={(e) => setField("graduationYear", e.target.value)} /></Field>
            </>
          )}

          {current.key === "clinic" && (
            <>
              <Field label="Hospital Name" optional><input value={String(form.hospitalName)} onChange={(e) => setField("hospitalName", e.target.value)} /></Field>
              <Field label="Clinic Name" optional><input value={String(form.clinicName)} onChange={(e) => setField("clinicName", e.target.value)} /></Field>
              <Field label="Hospital ID" optional><input type="number" value={String(form.hospitalId)} onChange={(e) => setField("hospitalId", e.target.value)} /></Field>
              <Field label="Branch" optional><input value={String(form.branch)} onChange={(e) => setField("branch", e.target.value)} /></Field>
              <Field label="Consultation Type">
                <select value={String(form.consultationType)} onChange={(e) => setField("consultationType", e.target.value)}>
                  <option value="IN_PERSON">In-person</option>
                  <option value="ONLINE">Online</option>
                  <option value="BOTH">Both</option>
                </select>
              </Field>
              <Field label="Consultation Fee" optional><input type="number" value={String(form.consultationFee)} onChange={(e) => setField("consultationFee", e.target.value)} /></Field>
              <Field label="Follow-up Fee" optional><input type="number" value={String(form.followUpFee)} onChange={(e) => setField("followUpFee", e.target.value)} /></Field>
              <div style={{ gridColumn: "1 / -1" }}>
                <span className="muted" style={{ fontSize: "0.85rem" }}>Available Days</span>
                <div className="row" style={{ marginTop: "0.35rem" }}>
                  {DAYS.map((d) => (
                    <label key={d} className="chip">
                      <input type="checkbox" checked={days.includes(d)} onChange={() => toggleDay(d)} /> {d}
                    </label>
                  ))}
                </div>
              </div>
              <Field label="Available Time Slots" optional><input value={String(form.availableTimeSlots)} onChange={(e) => setField("availableTimeSlots", e.target.value)} /></Field>
            </>
          )}

          {current.key === "identity" && (
            <>
              <Field label="Aadhaar Number" optional><input value={String(form.aadhaarNumber)} onChange={(e) => setField("aadhaarNumber", e.target.value)} /></Field>
              <Field label="PAN Number" optional><input value={String(form.panNumber)} onChange={(e) => setField("panNumber", e.target.value)} /></Field>
              <Field label="Passport Number" optional><input value={String(form.passportNumber)} onChange={(e) => setField("passportNumber", e.target.value)} /></Field>
              <Field label="Government ID Upload" optional><input type="file" onChange={(e) => onFile("governmentIdUpload", e)} /></Field>
            </>
          )}

          {current.key === "bank" && (
            <>
              <Field label="Account Holder Name" optional><input value={String(form.accountHolderName)} onChange={(e) => setField("accountHolderName", e.target.value)} /></Field>
              <Field label="Bank Name" optional><input value={String(form.bankName)} onChange={(e) => setField("bankName", e.target.value)} /></Field>
              <Field label="Account Number" optional><input value={String(form.accountNumber)} onChange={(e) => setField("accountNumber", e.target.value)} /></Field>
              <Field label="IFSC Code" optional><input value={String(form.ifscCode)} onChange={(e) => setField("ifscCode", e.target.value)} /></Field>
              <Field label="UPI ID" optional><input value={String(form.upiId)} onChange={(e) => setField("upiId", e.target.value)} /></Field>
            </>
          )}

          {current.key === "documents" && (
            <>
              <Field label="Medical Registration Certificate" optional><input type="file" onChange={(e) => onFile("medicalRegistrationCertificate", e)} /></Field>
              <Field label="Degree Certificate" optional><input type="file" onChange={(e) => onFile("degreeCertificate", e)} /></Field>
              <Field label="Experience Certificate" optional><input type="file" onChange={(e) => onFile("experienceCertificate", e)} /></Field>
              <Field label="Identity Proof" optional><input type="file" onChange={(e) => onFile("identityProof", e)} /></Field>
              <Field label="Address Proof" optional><input type="file" onChange={(e) => onFile("addressProof", e)} /></Field>
              <Field label="Passport Size Photograph" optional><input type="file" accept="image/*" onChange={(e) => onFile("passportSizePhotograph", e)} /></Field>
              <Field label="Digital Signature" optional><input type="file" accept="image/*" onChange={(e) => onFile("digitalSignature", e)} /></Field>
            </>
          )}
        </div>

        <div className="wizard-nav">
          <button type="button" className="secondary" onClick={goPrev} disabled={step === 0 || saving}>
            ← Previous
          </button>
          <button type="submit" disabled={saving}>
            {saving ? "Saving…" : "Save"}
          </button>
          <button type="button" className="wizard-next" onClick={goNext} disabled={saving} aria-label="Next section">
            {step < STEPS.length - 1 ? "Next →" : "Finish"}
          </button>
        </div>
      </form>
    </main>
  );
}
