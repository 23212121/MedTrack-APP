import { ChangeEvent, FormEvent, ReactNode, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, DoctorRegistration, DoctorRegistrationBody, HospitalRegistrationSummary } from "../api";
import { CLINICAL_DEPARTMENTS, mergeDepartmentOptions } from "../clinicalDepartments";
import { session } from "../dl/MedTrackSession";
import { toast } from "../toast";

const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"] as const;

type FormState = {
  firstName: string; middleName: string; lastName: string; gender: string; dateOfBirth: string;
  profilePhoto: string; bloodGroup: string; maritalStatus: string;
  mobileNumber: string; alternateMobileNumber: string; email: string; emergencyContactNumber: string;
  residentialAddress: string; city: string; state: string; country: string; postalCode: string;
  medicalRegistrationNumber: string; medicalCouncilName: string; registrationDate: string;
  registrationValidUntil: string; yearsOfExperience: string; currentDesignation: string;
  department: string; specialization: string; subSpecialization: string; qualification: string;
  medicalCollege: string; graduationYear: string;
  hospitalName: string; clinicName: string; hospitalId: string; branch: string;
  consultationType: string; consultationFee: string; followUpFee: string;
  availableDays: string[]; availableTimeSlots: string;
  aadhaarNumber: string; panNumber: string; passportNumber: string; governmentIdUpload: string;
  accountHolderName: string; bankName: string; accountNumber: string; ifscCode: string; upiId: string;
  medicalRegistrationCertificate: string; degreeCertificate: string; experienceCertificate: string;
  identityProof: string; addressProof: string; passportSizePhotograph: string; digitalSignature: string;
  emergencyContactName: string; emergencyRelationship: string; emergencyContactPhone: string;
  languagesKnown: string; biography: string; awardsAchievements: string; publications: string;
  researchExperience: string; memberships: string;
  loginPassword: string;
  confirmPassword: string;
  doctorUserId: string;
  status: string; createdBy: string; remarks: string;
  infoCorrectConfirmed: boolean; termsAccepted: boolean; privacyPolicyAccepted: boolean;
};

const emptyForm: FormState = {
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
  emergencyContactName: "", emergencyRelationship: "", emergencyContactPhone: "",
  languagesKnown: "", biography: "", awardsAchievements: "", publications: "",
  researchExperience: "", memberships: "",
  loginPassword: "",
  confirmPassword: "",
  doctorUserId: "",
  status: "ACTIVE", createdBy: "SELF_REGISTRATION", remarks: "",
  infoCorrectConfirmed: false, termsAccepted: false, privacyPolicyAccepted: false,
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

function Field({
  label,
  children,
  optional,
  required,
}: {
  label: string;
  children: ReactNode;
  optional?: boolean;
  required?: boolean;
}) {
  const clean = label.replace(/\s*\*$/, "").trim();
  const showRequired = !optional && (required || /\*$/.test(label.trim()));
  return (
    <div className="field-block">
      <span>
        {clean}
        {showRequired ? (
          <span className="required-mark" aria-hidden="true">
            *
          </span>
        ) : null}
        {optional ? " (optional)" : ""}
      </span>
      {children}
    </div>
  );
}

function FormBlock({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="panel stack form-block">
      <h2>{title}</h2>
      <div className="grid-2">{children}</div>
    </section>
  );
}

export default function DoctorRegistrationPage() {
  const sessionHospitalId = session.getHospitalId() || "";
  const [form, setForm] = useState<FormState>({
    ...emptyForm,
    hospitalId: sessionHospitalId,
  });
  const [doctors, setDoctors] = useState<DoctorRegistration[]>([]);
  const [hospitals, setHospitals] = useState<HospitalRegistrationSummary[]>([]);
  const [departmentOther, setDepartmentOther] = useState(false);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const departmentOptions = mergeDepartmentOptions(
    CLINICAL_DEPARTMENTS,
    hospitals.find((h) => String(h.id ?? h.hospitalId) === String(form.hospitalId))?.departments,
  );
  const departmentIsListed = departmentOptions.some(
    (name) => name.toLowerCase() === form.department.trim().toLowerCase(),
  );
  const departmentSelectValue = departmentOther
    ? "__other__"
    : !form.department.trim()
      ? ""
      : departmentIsListed
        ? departmentOptions.find((name) => name.toLowerCase() === form.department.trim().toLowerCase()) || form.department
        : "__other__";

  async function load() {
    try {
      const data = await api.doctors();
      setDoctors(data.doctors);
    } catch { /* ignore until service is up */ }
  }

  async function loadHospitals() {
    try {
      const data = await api.hospitals();
      setHospitals(data.hospitals ?? []);
    } catch {
      setHospitals([]);
    }
  }

  useEffect(() => { load(); void loadHospitals(); }, []);

  function setField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  async function onFile(key: keyof FormState, e: ChangeEvent<HTMLInputElement>) {
    const value = await fileToStoredValue(e.target.files?.[0]);
    setField(key, value as FormState[typeof key]);
  }

  function toggleDay(day: string) {
    setForm((prev) => {
      const has = prev.availableDays.includes(day);
      return {
        ...prev,
        availableDays: has ? prev.availableDays.filter((d) => d !== day) : [...prev.availableDays, day],
      };
    });
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError("");
    try {
      if (!form.hospitalId.trim()) {
        throw new Error("Hospital ID is required");
      }
      if (!form.doctorUserId.trim()) {
        throw new Error("Doctor user ID is required");
      }
      if (!form.loginPassword || form.loginPassword.length < 6) {
        throw new Error("Password must be at least 6 characters");
      }
      if (form.loginPassword !== form.confirmPassword) {
        throw new Error("Password and confirm password do not match");
      }
      if (!form.department.trim()) {
        throw new Error("Department is required");
      }
      const body: DoctorRegistrationBody = {
        firstName: form.firstName.trim(),
        middleName: form.middleName.trim() || undefined,
        lastName: form.lastName.trim(),
        doctorUserId: form.doctorUserId.trim(),
        doctorId: form.doctorUserId.trim(),
        gender: form.gender,
        dateOfBirth: form.dateOfBirth,
        profilePhoto: form.profilePhoto || undefined,
        bloodGroup: form.bloodGroup || undefined,
        maritalStatus: form.maritalStatus || undefined,
        mobileNumber: form.mobileNumber.trim(),
        alternateMobileNumber: form.alternateMobileNumber.trim() || undefined,
        email: form.email.trim(),
        emergencyContactNumber: form.emergencyContactNumber.trim() || undefined,
        residentialAddress: form.residentialAddress.trim(),
        city: form.city.trim(),
        state: form.state.trim(),
        country: form.country.trim(),
        postalCode: form.postalCode.trim(),
        medicalRegistrationNumber: form.medicalRegistrationNumber.trim(),
        medicalCouncilName: form.medicalCouncilName.trim(),
        registrationDate: form.registrationDate,
        registrationValidUntil: form.registrationValidUntil || undefined,
        yearsOfExperience: form.yearsOfExperience ? Number(form.yearsOfExperience) : undefined,
        currentDesignation: form.currentDesignation.trim() || undefined,
        department: form.department.trim(),
        specialization: form.specialization.trim(),
        subSpecialization: form.subSpecialization.trim() || undefined,
        qualification: form.qualification.trim() || undefined,
        medicalCollege: form.medicalCollege.trim() || undefined,
        graduationYear: form.graduationYear ? Number(form.graduationYear) : undefined,
        hospitalName: form.hospitalName.trim() || undefined,
        clinicName: form.clinicName.trim() || undefined,
        hospitalId: Number(form.hospitalId),
        branch: form.branch.trim() || undefined,
        consultationType: form.consultationType || undefined,
        consultationFee: form.consultationFee ? Number(form.consultationFee) : undefined,
        followUpFee: form.followUpFee ? Number(form.followUpFee) : undefined,
        availableDays: form.availableDays.length ? form.availableDays.join(",") : undefined,
        availableTimeSlots: form.availableTimeSlots.trim() || undefined,
        aadhaarNumber: form.aadhaarNumber.trim() || undefined,
        panNumber: form.panNumber.trim() || undefined,
        passportNumber: form.passportNumber.trim() || undefined,
        governmentIdUpload: form.governmentIdUpload || undefined,
        accountHolderName: form.accountHolderName.trim() || undefined,
        bankName: form.bankName.trim() || undefined,
        accountNumber: form.accountNumber.trim() || undefined,
        ifscCode: form.ifscCode.trim() || undefined,
        upiId: form.upiId.trim() || undefined,
        medicalRegistrationCertificate: form.medicalRegistrationCertificate || undefined,
        degreeCertificate: form.degreeCertificate || undefined,
        experienceCertificate: form.experienceCertificate || undefined,
        identityProof: form.identityProof || undefined,
        addressProof: form.addressProof || undefined,
        passportSizePhotograph: form.passportSizePhotograph || undefined,
        digitalSignature: form.digitalSignature || undefined,
        emergencyContactName: form.emergencyContactName.trim() || undefined,
        emergencyRelationship: form.emergencyRelationship.trim() || undefined,
        emergencyContactPhone: form.emergencyContactPhone.trim() || undefined,
        languagesKnown: form.languagesKnown.trim() || undefined,
        biography: form.biography.trim() || undefined,
        awardsAchievements: form.awardsAchievements.trim() || undefined,
        publications: form.publications.trim() || undefined,
        researchExperience: form.researchExperience.trim() || undefined,
        memberships: form.memberships.trim() || undefined,
        loginPassword: form.loginPassword,
        status: form.status || "ACTIVE",
        createdBy: form.createdBy || "SELF_REGISTRATION",
        remarks: form.remarks.trim() || undefined,
        infoCorrectConfirmed: form.infoCorrectConfirmed,
        termsAccepted: form.termsAccepted,
        privacyPolicyAccepted: form.privacyPolicyAccepted,
      };
      const res = await api.registerDoctor(body);
      toast.success(
        `${res.message}. Sign in with User ID ${res.doctor?.doctorId ?? form.doctorUserId} and your password (Hospital ID ${form.hospitalId}).`,
      );
      setForm({ ...emptyForm, hospitalId: sessionHospitalId });
      setDepartmentOther(false);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section>
      <p className="lead" style={{ marginTop: 0 }}>
        <Link to="/login">← Back to login</Link>
      </p>
      <h1>Doctor registration</h1>
      <p className="lead">
        Set Hospital ID, Doctor user ID, and password first — these are used for User login.
      </p>
      {error && <div className="msg error">{error}</div>}

      <form className="stack" onSubmit={onSubmit}>
        <FormBlock title="0. Login credentials">
          <Field label="Hospital ID" required>
            <input
              type="number"
              min={1}
              required
              value={form.hospitalId}
              onChange={(e) => setField("hospitalId", e.target.value)}
              placeholder="e.g. 10001"
            />
          </Field>
          <Field label="Doctor user ID" required>
            <input
              required
              value={form.doctorUserId}
              onChange={(e) => setField("doctorUserId", e.target.value.toUpperCase())}
              placeholder="e.g. DOC-MEHTA01"
              pattern="[A-Za-z0-9][A-Za-z0-9_-]{2,39}"
              title="3–40 characters: letters, numbers, _ or -"
            />
          </Field>
          <Field label="Password" required>
            <input
              type="password"
              required
              minLength={6}
              value={form.loginPassword}
              onChange={(e) => setField("loginPassword", e.target.value)}
              placeholder="Min 6 characters"
              autoComplete="new-password"
            />
          </Field>
          <Field label="Confirm password" required>
            <input
              type="password"
              required
              minLength={6}
              value={form.confirmPassword}
              onChange={(e) => setField("confirmPassword", e.target.value)}
              placeholder="Re-enter password"
              autoComplete="new-password"
            />
          </Field>
        </FormBlock>

        <FormBlock title="1. Personal Information">
          <Field label="First Name" required><input required value={form.firstName} onChange={(e) => setField("firstName", e.target.value)} /></Field>
          <Field label="Middle Name" optional><input value={form.middleName} onChange={(e) => setField("middleName", e.target.value)} /></Field>
          <Field label="Last Name" required><input required value={form.lastName} onChange={(e) => setField("lastName", e.target.value)} /></Field>
          <Field label="Gender">
            <select required value={form.gender} onChange={(e) => setField("gender", e.target.value)}>
              <option value="">Select</option>
              <option value="MALE">Male</option>
              <option value="FEMALE">Female</option>
              <option value="OTHER">Other</option>
            </select>
          </Field>
          <Field label="Date of Birth" required><input type="date" required value={form.dateOfBirth} onChange={(e) => setField("dateOfBirth", e.target.value)} /></Field>
          <Field label="Profile Photo" optional><input type="file" accept="image/*" onChange={(e) => onFile("profilePhoto", e)} /></Field>
          <Field label="Blood Group" optional>
            <select value={form.bloodGroup} onChange={(e) => setField("bloodGroup", e.target.value)}>
              <option value="">—</option>
              {["A+","A-","B+","B-","AB+","AB-","O+","O-"].map((g) => <option key={g} value={g}>{g}</option>)}
            </select>
          </Field>
          <Field label="Marital Status" optional>
            <select value={form.maritalStatus} onChange={(e) => setField("maritalStatus", e.target.value)}>
              <option value="">—</option>
              <option value="SINGLE">Single</option>
              <option value="MARRIED">Married</option>
              <option value="DIVORCED">Divorced</option>
              <option value="WIDOWED">Widowed</option>
            </select>
          </Field>
        </FormBlock>

        <FormBlock title="2. Contact Information">
          <Field label="Mobile Number" required><input required value={form.mobileNumber} onChange={(e) => setField("mobileNumber", e.target.value)} /></Field>
          <Field label="Alternate Mobile Number" optional><input value={form.alternateMobileNumber} onChange={(e) => setField("alternateMobileNumber", e.target.value)} /></Field>
          <Field label="Email Address" required><input type="email" required value={form.email} onChange={(e) => setField("email", e.target.value)} /></Field>
          <Field label="Emergency Contact Number" optional><input value={form.emergencyContactNumber} onChange={(e) => setField("emergencyContactNumber", e.target.value)} /></Field>
          <Field label="Residential Address" required><textarea required rows={2} value={form.residentialAddress} onChange={(e) => setField("residentialAddress", e.target.value)} /></Field>
          <Field label="City" required><input required value={form.city} onChange={(e) => setField("city", e.target.value)} /></Field>
          <Field label="State" required><input required value={form.state} onChange={(e) => setField("state", e.target.value)} /></Field>
          <Field label="Country" required><input required value={form.country} onChange={(e) => setField("country", e.target.value)} /></Field>
          <Field label="Postal Code" required><input required value={form.postalCode} onChange={(e) => setField("postalCode", e.target.value)} /></Field>
        </FormBlock>

        <FormBlock title="3. Professional Information">
          <Field label="Medical Registration Number" required><input required value={form.medicalRegistrationNumber} onChange={(e) => setField("medicalRegistrationNumber", e.target.value)} /></Field>
          <Field label="Medical Council Name" required><input required value={form.medicalCouncilName} onChange={(e) => setField("medicalCouncilName", e.target.value)} /></Field>
          <Field label="Registration Date" required><input type="date" required value={form.registrationDate} onChange={(e) => setField("registrationDate", e.target.value)} /></Field>
          <Field label="Registration Valid Until" optional><input type="date" value={form.registrationValidUntil} onChange={(e) => setField("registrationValidUntil", e.target.value)} /></Field>
          <Field label="Years of Experience" optional><input type="number" min={0} value={form.yearsOfExperience} onChange={(e) => setField("yearsOfExperience", e.target.value)} /></Field>
          <Field label="Current Designation" optional><input value={form.currentDesignation} onChange={(e) => setField("currentDesignation", e.target.value)} /></Field>
          <Field label="Department" required>
            <select
              required
              value={departmentSelectValue}
              onChange={(e) => {
                const value = e.target.value;
                if (value === "__other__") {
                  setDepartmentOther(true);
                  if (departmentIsListed) setField("department", "");
                  return;
                }
                setDepartmentOther(false);
                setField("department", value);
              }}
            >
              <option value="">Select department</option>
              {departmentOptions.map((name) => (
                <option key={name} value={name}>{name}</option>
              ))}
              <option value="__other__">Other</option>
            </select>
          </Field>
          {departmentSelectValue === "__other__" && (
            <Field label="Other department" required>
              <input
                required
                value={form.department}
                onChange={(e) => setField("department", e.target.value)}
                placeholder="Enter department name"
              />
            </Field>
          )}
          <Field label="Specialization" required><input required value={form.specialization} onChange={(e) => setField("specialization", e.target.value)} /></Field>
          <Field label="Sub-Specialization" optional><input value={form.subSpecialization} onChange={(e) => setField("subSpecialization", e.target.value)} /></Field>
          <Field label="Qualification" optional><input placeholder="MBBS, MD, MS, DM, MCh…" value={form.qualification} onChange={(e) => setField("qualification", e.target.value)} /></Field>
          <Field label="Medical College/University" optional><input value={form.medicalCollege} onChange={(e) => setField("medicalCollege", e.target.value)} /></Field>
          <Field label="Graduation Year" optional><input type="number" min={1950} max={2100} value={form.graduationYear} onChange={(e) => setField("graduationYear", e.target.value)} /></Field>
        </FormBlock>

        <FormBlock title="4. Clinic / Hospital Information">
          <Field label="Hospital Name" optional><input value={form.hospitalName} onChange={(e) => setField("hospitalName", e.target.value)} /></Field>
          <Field label="Clinic Name" optional><input value={form.clinicName} onChange={(e) => setField("clinicName", e.target.value)} /></Field>
          <Field label="Branch" optional><input value={form.branch} onChange={(e) => setField("branch", e.target.value)} /></Field>
          <Field label="Consultation Type">
            <select value={form.consultationType} onChange={(e) => setField("consultationType", e.target.value)}>
              <option value="IN_PERSON">In-person</option>
              <option value="ONLINE">Online</option>
              <option value="BOTH">Both</option>
            </select>
          </Field>
          <Field label="Consultation Fee" optional><input type="number" min={0} value={form.consultationFee} onChange={(e) => setField("consultationFee", e.target.value)} /></Field>
          <Field label="Follow-up Fee" optional><input type="number" min={0} value={form.followUpFee} onChange={(e) => setField("followUpFee", e.target.value)} /></Field>
          <div className="full-span">
            <span className="field-label">Available Days</span>
            <div className="chip-row">
              {DAYS.map((d) => (
                <label key={d} className="chip">
                  <input type="checkbox" checked={form.availableDays.includes(d)} onChange={() => toggleDay(d)} />
                  {d}
                </label>
              ))}
            </div>
          </div>
          <Field label="Available TimeSlots" optional><input placeholder="09:00-13:00, 14:00-18:00" value={form.availableTimeSlots} onChange={(e) => setField("availableTimeSlots", e.target.value)} /></Field>
        </FormBlock>

        <FormBlock title="5. Identity Documents">
          <Field label="Aadhaar Number" optional><input value={form.aadhaarNumber} onChange={(e) => setField("aadhaarNumber", e.target.value)} /></Field>
          <Field label="PAN Number" optional><input value={form.panNumber} onChange={(e) => setField("panNumber", e.target.value)} /></Field>
          <Field label="Passport Number" optional><input value={form.passportNumber} onChange={(e) => setField("passportNumber", e.target.value)} /></Field>
          <Field label="Government ID Upload" optional><input type="file" onChange={(e) => onFile("governmentIdUpload", e)} /></Field>
        </FormBlock>

        <FormBlock title="6. Bank Details">
          <Field label="Account Holder Name" optional><input value={form.accountHolderName} onChange={(e) => setField("accountHolderName", e.target.value)} /></Field>
          <Field label="Bank Name" optional><input value={form.bankName} onChange={(e) => setField("bankName", e.target.value)} /></Field>
          <Field label="Account Number" optional><input value={form.accountNumber} onChange={(e) => setField("accountNumber", e.target.value)} /></Field>
          <Field label="IFSC Code" optional><input value={form.ifscCode} onChange={(e) => setField("ifscCode", e.target.value)} /></Field>
          <Field label="UPI ID" optional><input value={form.upiId} onChange={(e) => setField("upiId", e.target.value)} /></Field>
        </FormBlock>

        <FormBlock title="7. Documents Upload">
          <Field label="Medical Registration Certificate" optional><input type="file" onChange={(e) => onFile("medicalRegistrationCertificate", e)} /></Field>
          <Field label="Degree Certificate" optional><input type="file" onChange={(e) => onFile("degreeCertificate", e)} /></Field>
          <Field label="Experience Certificate" optional><input type="file" onChange={(e) => onFile("experienceCertificate", e)} /></Field>
          <Field label="Identity Proof" optional><input type="file" onChange={(e) => onFile("identityProof", e)} /></Field>
          <Field label="Address Proof" optional><input type="file" onChange={(e) => onFile("addressProof", e)} /></Field>
          <Field label="Passport Size Photograph" optional><input type="file" accept="image/*" onChange={(e) => onFile("passportSizePhotograph", e)} /></Field>
          <Field label="Digital Signature" optional><input type="file" accept="image/*" onChange={(e) => onFile("digitalSignature", e)} /></Field>
        </FormBlock>

        <FormBlock title="8. Emergency Details">
          <Field label="Emergency Contact Name" optional><input value={form.emergencyContactName} onChange={(e) => setField("emergencyContactName", e.target.value)} /></Field>
          <Field label="Relationship" optional><input value={form.emergencyRelationship} onChange={(e) => setField("emergencyRelationship", e.target.value)} /></Field>
          <Field label="Emergency Contact Number" optional><input value={form.emergencyContactPhone} onChange={(e) => setField("emergencyContactPhone", e.target.value)} /></Field>
        </FormBlock>

        <FormBlock title="9. Professional Profile">
          <Field label="Languages Known" optional><input value={form.languagesKnown} onChange={(e) => setField("languagesKnown", e.target.value)} /></Field>
          <Field label="Biography / About Doctor" optional><textarea rows={3} value={form.biography} onChange={(e) => setField("biography", e.target.value)} /></Field>
          <Field label="Awards & Achievements" optional><textarea rows={2} value={form.awardsAchievements} onChange={(e) => setField("awardsAchievements", e.target.value)} /></Field>
          <Field label="Publications" optional><textarea rows={2} value={form.publications} onChange={(e) => setField("publications", e.target.value)} /></Field>
          <Field label="Research Experience" optional><textarea rows={2} value={form.researchExperience} onChange={(e) => setField("researchExperience", e.target.value)} /></Field>
          <Field label="Memberships" optional><input value={form.memberships} onChange={(e) => setField("memberships", e.target.value)} /></Field>
        </FormBlock>

        <FormBlock title="10. System Information">
          <Field label="Status">
            <select value={form.status} onChange={(e) => setField("status", e.target.value)}>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </Field>
          <Field label="Created By"><input value={form.createdBy} onChange={(e) => setField("createdBy", e.target.value)} /></Field>
          <Field label="Created Date"><input value="Set automatically" disabled /></Field>
          <Field label="Updated Date"><input value="Set automatically" disabled /></Field>
          <Field label="Last Login"><input value="Updated on login" disabled /></Field>
          <Field label="Remarks" optional><textarea rows={2} value={form.remarks} onChange={(e) => setField("remarks", e.target.value)} /></Field>
        </FormBlock>

        <FormBlock title="11. Consent">
          <label className="check-line full-span">
            <input type="checkbox" required checked={form.infoCorrectConfirmed} onChange={(e) => setField("infoCorrectConfirmed", e.target.checked)} />
            I confirm that the information provided is correct.
          </label>
          <label className="check-line full-span">
            <input type="checkbox" required checked={form.termsAccepted} onChange={(e) => setField("termsAccepted", e.target.checked)} />
            I agree to the Terms and Conditions.
          </label>
          <label className="check-line full-span">
            <input type="checkbox" required checked={form.privacyPolicyAccepted} onChange={(e) => setField("privacyPolicyAccepted", e.target.checked)} />
            I agree to the Privacy Policy.
          </label>
        </FormBlock>

        <div className="row">
          <button type="submit" disabled={saving}>{saving ? "Registering…" : "Register doctor"}</button>
          <button
            type="button"
            className="secondary"
            disabled={saving}
            onClick={() => {
              setForm({ ...emptyForm, hospitalId: sessionHospitalId });
              setDepartmentOther(false);
            }}
          >
            Reset form
          </button>
        </div>
      </form>

      <section className="panel" style={{ marginTop: "1.2rem" }}>
        <h2>Registered doctors</h2>
        {doctors.length === 0 ? (
          <p className="lead">No registrations yet.</p>
        ) : (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>User ID</th><th>Hospital</th><th>Name</th><th>Department</th><th>Specialization</th><th>Email</th><th>Status</th>
                </tr>
              </thead>
              <tbody>
                {doctors.map((d) => (
                  <tr key={d.id || d.doctorId}>
                    <td>{d.doctorId}</td>
                    <td>{d.hospitalId ?? "—"}</td>
                    <td>{d.firstName} {d.lastName}</td>
                    <td>{d.department || "—"}</td>
                    <td>{d.specialization}</td>
                    <td>{d.email}</td>
                    <td><span className={`badge ${d.status}`}>{d.status}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </section>
  );
}
