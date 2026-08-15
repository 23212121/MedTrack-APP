import { ChangeEvent, FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";

type HospitalForm = {
  // 1. Basic
  hospitalName: string;
  hospitalId: string;
  hospitalCode: string;
  hospitalType: string;
  registrationNumber: string;
  establishmentYear: string;
  numberOfBeds: string;
  hospitalLogo: string;
  hospitalDescription: string;
  // 2. Contact
  email: string;
  primaryContact: string;
  emergencyContact: string;
  websiteUrl: string;
  faxNumber: string;
  // 3. Address
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  country: string;
  pinCode: string;
  landmark: string;
  latitude: string;
  longitude: string;
  // 4. License
  hospitalRegCertNumber: string;
  medicalLicenseNumber: string;
  gstNumber: string;
  panNumber: string;
  nabhNumber: string;
  licenseExpiryDate: string;
  // 5. Documents
  docRegistrationCertificate: string;
  docMedicalLicense: string;
  docGstCertificate: string;
  docPanCard: string;
  docAccreditation: string;
  // 6. Departments & services
  departments: string[];
  services: string[];
  // 7. Staff
  totalDoctors: string;
  totalNurses: string;
  totalTechnicians: string;
  totalAdminStaff: string;
  // 8. Working hours
  openingTime: string;
  closingTime: string;
  emergencyServiceAvailable: string;
  facility24x7: string;
  // 9. Subscription
  subscriptionPlan: string;
  numberOfUsers: string;
  billingAddress: string;
  paymentMethod: string;
  // 10. System
  timeZone: string;
  currency: string;
  language: string;
  dateFormat: string;
  // Admin (main tab)
  adminName: string;
  adminEmail: string;
  adminMobile: string;
  adminPassword: string;
  // 11. Declaration
  infoAccurate: boolean;
  termsAccepted: boolean;
  privacyAccepted: boolean;
};

const DEPARTMENTS = [
  "Cardiology",
  "Neurology",
  "Orthopedics",
  "Pediatrics",
  "Gynecology",
  "Dermatology",
  "Oncology",
  "Emergency",
  "ICU",
  "Radiology",
  "Laboratory",
] as const;

const SERVICES = [
  "OPD",
  "IPD",
  "Emergency",
  "Ambulance",
  "Pharmacy",
  "Laboratory",
  "Blood Bank",
  "Telemedicine",
] as const;

const HOSPITAL_TYPES = [
  "Multi-Specialty Hospital",
  "General Hospital",
  "Clinic",
  "Diagnostic Center",
  "Nursing Home",
  "Specialty Hospital",
] as const;

const emptyForm = (nextId = "10001"): HospitalForm => ({
  hospitalName: "",
  hospitalId: nextId,
  hospitalCode: nextId,
  hospitalType: "",
  registrationNumber: "",
  establishmentYear: "",
  numberOfBeds: "",
  hospitalLogo: "",
  hospitalDescription: "",
  email: "",
  primaryContact: "",
  emergencyContact: "",
  websiteUrl: "",
  faxNumber: "",
  addressLine1: "",
  addressLine2: "",
  city: "",
  state: "",
  country: "India",
  pinCode: "",
  landmark: "",
  latitude: "",
  longitude: "",
  hospitalRegCertNumber: "",
  medicalLicenseNumber: "",
  gstNumber: "",
  panNumber: "",
  nabhNumber: "",
  licenseExpiryDate: "",
  docRegistrationCertificate: "",
  docMedicalLicense: "",
  docGstCertificate: "",
  docPanCard: "",
  docAccreditation: "",
  departments: [],
  services: [],
  totalDoctors: "",
  totalNurses: "",
  totalTechnicians: "",
  totalAdminStaff: "",
  openingTime: "09:00",
  closingTime: "18:00",
  emergencyServiceAvailable: "Yes",
  facility24x7: "No",
  subscriptionPlan: "Basic",
  numberOfUsers: "5",
  billingAddress: "",
  paymentMethod: "",
  timeZone: "Asia/Kolkata",
  currency: "INR",
  language: "English",
  dateFormat: "DD/MM/YYYY",
  adminName: "",
  adminEmail: "",
  adminMobile: "",
  adminPassword: "",
  infoAccurate: false,
  termsAccepted: false,
  privacyAccepted: false,
});

type SectionId =
  | "basic"
  | "contact"
  | "address"
  | "admin"
  | "license"
  | "documents"
  | "departments"
  | "staff"
  | "hours"
  | "subscription"
  | "system"
  | "declaration";

type SectionDef = {
  id: SectionId;
  title: string;
  tab: string;
  mandatory?: boolean;
  description: string;
};

const SECTIONS: SectionDef[] = [
  {
    id: "basic",
    title: "Basic Hospital Information",
    tab: "Basic Information",
    mandatory: true,
    description: "Name, type, beds, logo, and description",
  },
  {
    id: "contact",
    title: "Contact Information",
    tab: "Basic Information",
    description: "Email, phone, website, and fax",
  },
  {
    id: "address",
    title: "Address Information",
    tab: "Address Details",
    description: "Address, city, PIN, and map location",
  },
  {
    id: "admin",
    title: "Administrator Details",
    tab: "Administrator Details",
    description: "Hospital admin account for dashboard access",
  },
  {
    id: "license",
    title: "Hospital License & Legal Information",
    tab: "Licenses & Documents",
    description: "Registration, license, GST, PAN, NABH",
  },
  {
    id: "documents",
    title: "Upload Documents",
    tab: "Licenses & Documents",
    description: "Certificates and identity documents",
  },
  {
    id: "departments",
    title: "Specializations & Services",
    tab: "Departments & Services",
    description: "Departments and clinical services offered",
  },
  {
    id: "staff",
    title: "Staff Information",
    tab: "Departments & Services",
    description: "Doctors, nurses, technicians, admin staff",
  },
  {
    id: "hours",
    title: "Working Hours",
    tab: "Departments & Services",
    description: "Opening hours and emergency availability",
  },
  {
    id: "subscription",
    title: "Subscription & Billing",
    tab: "Subscription Plan",
    description: "Plan, users, billing, and payment",
  },
  {
    id: "system",
    title: "System Configuration",
    tab: "Subscription Plan",
    description: "Time zone, currency, language, date format",
  },
  {
    id: "declaration",
    title: "Declaration",
    tab: "Review & Submit",
    description: "Confirm accuracy and accept policies",
  },
];

const TABS = [
  "Basic Information",
  "Address Details",
  "Administrator Details",
  "Licenses & Documents",
  "Departments & Services",
  "Subscription Plan",
  "Review & Submit",
] as const;

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
    <label>
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
    </label>
  );
}

function ChipToggle({
  options,
  selected,
  onToggle,
}: {
  options: readonly string[];
  selected: string[];
  onToggle: (value: string) => void;
}) {
  return (
    <div className="chip-row">
      {options.map((opt) => {
        const on = selected.includes(opt);
        return (
          <button
            key={opt}
            type="button"
            className={`chip${on ? " chip--on" : ""}`}
            onClick={() => onToggle(opt)}
          >
            {opt}
          </button>
        );
      })}
    </div>
  );
}

export default function HospitalRegistrationPage() {
  const [form, setForm] = useState<HospitalForm>(emptyForm());
  const [activeTab, setActiveTab] = useState<string>(TABS[0]);
  /** Open Basic Information popup immediately — no section cards. */
  const [openSection, setOpenSection] = useState<SectionId | null>("basic");
  const [completed, setCompleted] = useState<Set<SectionId>>(new Set());
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [saving, setSaving] = useState(false);
  const [popupError, setPopupError] = useState("");

  async function loadNextHospitalId() {
    try {
      const data = await api.nextHospitalId();
      const next = String(data.nextHospitalId || 10001);
      setForm((prev) => ({
        ...prev,
        hospitalId: next,
        hospitalCode: next,
      }));
    } catch {
      setForm((prev) => ({
        ...prev,
        hospitalId: prev.hospitalId || "10001",
        hospitalCode: prev.hospitalCode || "10001",
      }));
    }
  }

  useEffect(() => {
    void loadNextHospitalId();
  }, []);

  const sectionIndex = useMemo(
    () => Object.fromEntries(SECTIONS.map((s, i) => [s.id, i])) as Record<SectionId, number>,
    []
  );

  function isUnlocked(id: SectionId) {
    const idx = sectionIndex[id];
    if (idx === 0) return true;
    // Only first form is mandatory; once basic is done, all later sections unlock
    return completed.has("basic");
  }

  function setField<K extends keyof HospitalForm>(key: K, value: HospitalForm[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  function toggleList(key: "departments" | "services", value: string) {
    setForm((prev) => {
      const has = prev[key].includes(value);
      return {
        ...prev,
        [key]: has ? prev[key].filter((v) => v !== value) : [...prev[key], value],
      };
    });
  }

  async function onFile(key: keyof HospitalForm, e: ChangeEvent<HTMLInputElement>) {
    const value = await fileToStoredValue(e.target.files?.[0]);
    setField(key, value as HospitalForm[typeof key]);
  }

  function openPopup(id: SectionId) {
    setError("");
    setMessage("");
    setPopupError("");
    if (!isUnlocked(id)) {
      setError("Complete Basic Hospital Information first to open the next forms.");
      setOpenSection("basic");
      setActiveTab("Basic Information");
      return;
    }
    setOpenSection(id);
  }

  function openTab(tab: string) {
    setError("");
    setMessage("");
    if (
      tab !== "Basic Information" &&
      tab !== "Review & Submit" &&
      !completed.has("basic")
    ) {
      setError("Complete Basic Hospital Information first.");
      setActiveTab("Basic Information");
      setOpenSection("basic");
      return;
    }
    setActiveTab(tab);
    if (tab === "Review & Submit") {
      setOpenSection(null);
      return;
    }
    const first = SECTIONS.find((s) => s.tab === tab);
    if (first) openPopup(first.id);
  }

  function validateSection(id: SectionId): string | null {
    if (id === "basic") {
      if (!form.hospitalName.trim()) return "Hospital Name is required";
      if (!form.hospitalType) return "Hospital Type is required";
      if (!form.registrationNumber.trim()) return "Registration Number is required";
    }
    if (id === "declaration") {
      if (!form.infoAccurate || !form.termsAccepted || !form.privacyAccepted) {
        return "Please accept all declarations before continuing";
      }
    }
    return null;
  }

  function saveSection(e: FormEvent) {
    e.preventDefault();
    if (!openSection) return;
    const err = validateSection(openSection);
    if (err) {
      setPopupError(err);
      return;
    }
    setCompleted((prev) => new Set(prev).add(openSection));
    setPopupError("");
    setOpenSection(null);
    setMessage(`${SECTIONS.find((s) => s.id === openSection)?.title} saved.`);
  }

  function goNextFromPopup() {
    if (!openSection) return;
    const err = validateSection(openSection);
    if (err) {
      setPopupError(err);
      return;
    }
    setCompleted((prev) => new Set(prev).add(openSection));
    const idx = sectionIndex[openSection];
    const next = SECTIONS[idx + 1];
    setPopupError("");
    if (next) {
      setActiveTab(next.tab);
      setOpenSection(next.id);
    } else {
      setOpenSection(null);
      setActiveTab("Review & Submit");
    }
  }

  async function onSubmitRegistration() {
    if (!completed.has("basic")) {
      setError("Basic Hospital Information is mandatory before submit.");
      return;
    }
    if (!form.infoAccurate || !form.termsAccepted || !form.privacyAccepted) {
      setError("Accept the declaration checkboxes before submit.");
      setActiveTab("Review & Submit");
      setOpenSection("declaration");
      return;
    }
    if (!form.adminPassword.trim() || form.adminPassword.trim().length < 6) {
      setError("Set an administrator login password (min 6 characters) for Hospital ID sign-in.");
      setActiveTab("Administrator Details");
      setOpenSection("admin");
      return;
    }

    setSaving(true);
    setError("");
    setMessage("");
    try {
      const res = await api.registerHospital(form);
      const assignedId = res.hospital?.id ?? res.hospital?.hospitalId;
      setMessage(
        res.message ||
          `Hospital registered with ID ${assignedId ?? form.hospitalId}. Pending Super Admin verification.`
      );
      setCompleted(new Set());
      setActiveTab(TABS[0]);
      setOpenSection("basic");
      const next = assignedId != null ? String(Number(assignedId) + 1) : "10001";
      setForm(emptyForm(next));
      await loadNextHospitalId();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Registration failed");
    } finally {
      setSaving(false);
    }
  }

  const openDef = SECTIONS.find((s) => s.id === openSection) ?? null;

  return (
    <section className="hospital-reg">
      <div className="panel stack hospital-reg-tabs-bar">
        <div className="hospital-reg-tabs" role="tablist" aria-label="Hospital registration tabs">
          {TABS.map((tab) => {
            const tabDone = SECTIONS.filter((s) => s.tab === tab).every((s) =>
              completed.has(s.id)
            );
            const locked =
              tab !== "Basic Information" &&
              !completed.has("basic") &&
              tab !== "Review & Submit";
            return (
              <button
                key={tab}
                type="button"
                role="tab"
                aria-selected={activeTab === tab}
                className={`hospital-reg-tab${activeTab === tab ? " is-active" : ""}${
                  tabDone ? " is-done" : ""
                }`}
                disabled={locked && tab !== "Review & Submit"}
                onClick={() => openTab(tab)}
              >
                {tab}
              </button>
            );
          })}
        </div>
      </div>

      <p className="lead" style={{ marginBottom: "0.8rem" }}>
        <Link to="/login">← Back to sign in</Link>
      </p>
      {error && <div className="msg error">{error}</div>}
      {message && <div className="msg ok">{message}</div>}

      {activeTab === "Review & Submit" && !openSection && (
        <div className="panel stack hospital-reg-review">
          <h2>Review & Submit</h2>
          <dl className="hospital-reg-summary">
            <div>
              <dt>Hospital</dt>
              <dd>{form.hospitalName || "—"}</dd>
            </div>
            <div>
              <dt>Hospital ID</dt>
              <dd>
                <code>{form.hospitalId || form.hospitalCode}</code>
              </dd>
            </div>
            <div>
              <dt>Type</dt>
              <dd>{form.hospitalType || "—"}</dd>
            </div>
            <div>
              <dt>City</dt>
              <dd>{form.city || "—"}</dd>
            </div>
            <div>
              <dt>Plan</dt>
              <dd>{form.subscriptionPlan || "—"}</dd>
            </div>
            <div>
              <dt>Sections done</dt>
              <dd>
                {completed.size} / {SECTIONS.length}
              </dd>
            </div>
          </dl>
          <div className="row" style={{ gap: "0.5rem" }}>
            <button
              type="button"
              className="secondary"
              onClick={() => openPopup("declaration")}
            >
              Open declaration
            </button>
            <button
              type="button"
              disabled={saving || !completed.has("basic")}
              onClick={() => void onSubmitRegistration()}
            >
              {saving ? "Submitting…" : "Submit hospital registration"}
            </button>
          </div>
        </div>
      )}

      {openDef && (
        <div
          className="hospital-modal-backdrop"
          role="presentation"
          onClick={(e) => {
            if (e.target === e.currentTarget) setOpenSection(null);
          }}
        >
          <div
            className="hospital-modal panel stack"
            role="dialog"
            aria-modal="true"
            aria-labelledby="hospital-modal-title"
          >
            <header className="hospital-modal-head">
              <div>
                <h2 id="hospital-modal-title">{openDef.title}</h2>
                <p className="block-sub">{openDef.description}</p>
              </div>
              <button
                type="button"
                className="secondary"
                onClick={() => setOpenSection(null)}
              >
                Close
              </button>
            </header>

            {popupError && <div className="msg error">{popupError}</div>}

            <form className="stack" onSubmit={saveSection}>
              {openSection === "basic" && (
                <div className="grid-2">
                  <Field label="Hospital Name" required>
                    <input
                      value={form.hospitalName}
                      onChange={(e) => setField("hospitalName", e.target.value)}
                      required
                    />
                  </Field>
                  <Field label="Hospital ID (auto generated from 10001)">
                    <input value={form.hospitalId || form.hospitalCode} readOnly />
                  </Field>
                  <Field label="Hospital Type" required>
                    <select
                      value={form.hospitalType}
                      onChange={(e) => setField("hospitalType", e.target.value)}
                      required
                    >
                      <option value="">Select type</option>
                      {HOSPITAL_TYPES.map((t) => (
                        <option key={t} value={t}>
                          {t}
                        </option>
                      ))}
                    </select>
                  </Field>
                  <Field label="Registration Number" required>
                    <input
                      value={form.registrationNumber}
                      onChange={(e) => setField("registrationNumber", e.target.value)}
                      required
                    />
                  </Field>
                  <Field label="Establishment Year" optional>
                    <input
                      type="number"
                      min={1800}
                      max={2100}
                      value={form.establishmentYear}
                      onChange={(e) => setField("establishmentYear", e.target.value)}
                    />
                  </Field>
                  <Field label="Number of Beds" optional>
                    <input
                      type="number"
                      min={0}
                      value={form.numberOfBeds}
                      onChange={(e) => setField("numberOfBeds", e.target.value)}
                    />
                  </Field>
                  <Field label="Hospital Logo" optional>
                    <input
                      type="file"
                      accept="image/*"
                      onChange={(e) => void onFile("hospitalLogo", e)}
                    />
                  </Field>
                  <label className="full-span">
                    Hospital Description (optional)
                    <textarea
                      rows={3}
                      value={form.hospitalDescription}
                      onChange={(e) => setField("hospitalDescription", e.target.value)}
                    />
                  </label>
                </div>
              )}

              {openSection === "contact" && (
                <div className="grid-2">
                  <Field label="Hospital Email Address">
                    <input
                      type="email"
                      value={form.email}
                      onChange={(e) => setField("email", e.target.value)}
                    />
                  </Field>
                  <Field label="Primary Contact Number">
                    <input
                      value={form.primaryContact}
                      onChange={(e) => setField("primaryContact", e.target.value)}
                    />
                  </Field>
                  <Field label="Emergency Contact Number">
                    <input
                      value={form.emergencyContact}
                      onChange={(e) => setField("emergencyContact", e.target.value)}
                    />
                  </Field>
                  <Field label="Website URL" optional>
                    <input
                      value={form.websiteUrl}
                      onChange={(e) => setField("websiteUrl", e.target.value)}
                    />
                  </Field>
                  <Field label="Fax Number" optional>
                    <input
                      value={form.faxNumber}
                      onChange={(e) => setField("faxNumber", e.target.value)}
                    />
                  </Field>
                </div>
              )}

              {openSection === "address" && (
                <div className="grid-2">
                  <Field label="Address Line 1">
                    <input
                      value={form.addressLine1}
                      onChange={(e) => setField("addressLine1", e.target.value)}
                    />
                  </Field>
                  <Field label="Address Line 2" optional>
                    <input
                      value={form.addressLine2}
                      onChange={(e) => setField("addressLine2", e.target.value)}
                    />
                  </Field>
                  <Field label="City">
                    <input
                      value={form.city}
                      onChange={(e) => setField("city", e.target.value)}
                    />
                  </Field>
                  <Field label="State">
                    <input
                      value={form.state}
                      onChange={(e) => setField("state", e.target.value)}
                    />
                  </Field>
                  <Field label="Country">
                    <input
                      value={form.country}
                      onChange={(e) => setField("country", e.target.value)}
                    />
                  </Field>
                  <Field label="PIN/ZIP Code">
                    <input
                      value={form.pinCode}
                      onChange={(e) => setField("pinCode", e.target.value)}
                    />
                  </Field>
                  <Field label="Landmark" optional>
                    <input
                      value={form.landmark}
                      onChange={(e) => setField("landmark", e.target.value)}
                    />
                  </Field>
                  <Field label="Latitude" optional>
                    <input
                      value={form.latitude}
                      onChange={(e) => setField("latitude", e.target.value)}
                    />
                  </Field>
                  <Field label="Longitude" optional>
                    <input
                      value={form.longitude}
                      onChange={(e) => setField("longitude", e.target.value)}
                    />
                  </Field>
                </div>
              )}

              {openSection === "admin" && (
                <div className="grid-2">
                  <Field label="Administrator Name">
                    <input
                      value={form.adminName}
                      onChange={(e) => setField("adminName", e.target.value)}
                    />
                  </Field>
                  <Field label="Administrator Email">
                    <input
                      type="email"
                      value={form.adminEmail}
                      onChange={(e) => setField("adminEmail", e.target.value)}
                    />
                  </Field>
                  <Field label="Administrator Mobile">
                    <input
                      value={form.adminMobile}
                      onChange={(e) => setField("adminMobile", e.target.value)}
                    />
                  </Field>
                  <Field label="Login password (Hospital ID sign-in)">
                    <input
                      type="password"
                      required
                      minLength={6}
                      value={form.adminPassword}
                      onChange={(e) => setField("adminPassword", e.target.value)}
                      placeholder="Used on Login → Hospital ID"
                    />
                  </Field>
                </div>
              )}

              {openSection === "license" && (
                <div className="grid-2">
                  <Field label="Hospital Registration Certificate Number">
                    <input
                      value={form.hospitalRegCertNumber}
                      onChange={(e) => setField("hospitalRegCertNumber", e.target.value)}
                    />
                  </Field>
                  <Field label="Medical License Number">
                    <input
                      value={form.medicalLicenseNumber}
                      onChange={(e) => setField("medicalLicenseNumber", e.target.value)}
                    />
                  </Field>
                  <Field label="GST Number" optional>
                    <input
                      value={form.gstNumber}
                      onChange={(e) => setField("gstNumber", e.target.value)}
                    />
                  </Field>
                  <Field label="PAN Number">
                    <input
                      value={form.panNumber}
                      onChange={(e) => setField("panNumber", e.target.value)}
                    />
                  </Field>
                  <Field label="NABH Accreditation Number" optional>
                    <input
                      value={form.nabhNumber}
                      onChange={(e) => setField("nabhNumber", e.target.value)}
                    />
                  </Field>
                  <Field label="License Expiry Date">
                    <input
                      type="date"
                      value={form.licenseExpiryDate}
                      onChange={(e) => setField("licenseExpiryDate", e.target.value)}
                    />
                  </Field>
                </div>
              )}

              {openSection === "documents" && (
                <div className="grid-2">
                  <Field label="Hospital Registration Certificate">
                    <input
                      type="file"
                      onChange={(e) => void onFile("docRegistrationCertificate", e)}
                    />
                  </Field>
                  <Field label="Medical License">
                    <input
                      type="file"
                      onChange={(e) => void onFile("docMedicalLicense", e)}
                    />
                  </Field>
                  <Field label="GST Certificate" optional>
                    <input
                      type="file"
                      onChange={(e) => void onFile("docGstCertificate", e)}
                    />
                  </Field>
                  <Field label="PAN Card">
                    <input type="file" onChange={(e) => void onFile("docPanCard", e)} />
                  </Field>
                  <Field label="Accreditation Certificates" optional>
                    <input
                      type="file"
                      onChange={(e) => void onFile("docAccreditation", e)}
                    />
                  </Field>
                </div>
              )}

              {openSection === "departments" && (
                <div className="stack">
                  <div>
                    <p className="field-label">Available Departments</p>
                    <ChipToggle
                      options={DEPARTMENTS}
                      selected={form.departments}
                      onToggle={(v) => toggleList("departments", v)}
                    />
                  </div>
                  <div>
                    <p className="field-label">Services</p>
                    <ChipToggle
                      options={SERVICES}
                      selected={form.services}
                      onToggle={(v) => toggleList("services", v)}
                    />
                  </div>
                </div>
              )}

              {openSection === "staff" && (
                <div className="stack">
                  <div className="grid-2">
                    <Field label="Total Doctors">
                      <input
                        type="number"
                        min={0}
                        value={form.totalDoctors}
                        onChange={(e) => setField("totalDoctors", e.target.value)}
                      />
                    </Field>
                    <Field label="Total Nurses">
                      <input
                        type="number"
                        min={0}
                        value={form.totalNurses}
                        onChange={(e) => setField("totalNurses", e.target.value)}
                      />
                    </Field>
                    <Field label="Total Technicians">
                      <input
                        type="number"
                        min={0}
                        value={form.totalTechnicians}
                        onChange={(e) => setField("totalTechnicians", e.target.value)}
                      />
                    </Field>
                    <Field label="Total Administrative Staff">
                      <input
                        type="number"
                        min={0}
                        value={form.totalAdminStaff}
                        onChange={(e) => setField("totalAdminStaff", e.target.value)}
                      />
                    </Field>
                  </div>
                  <p className="field-hint">
                    Optional detailed staff registration can be done later.
                  </p>
                </div>
              )}

              {openSection === "hours" && (
                <div className="grid-2">
                  <Field label="Opening Time">
                    <input
                      type="time"
                      value={form.openingTime}
                      onChange={(e) => setField("openingTime", e.target.value)}
                    />
                  </Field>
                  <Field label="Closing Time">
                    <input
                      type="time"
                      value={form.closingTime}
                      onChange={(e) => setField("closingTime", e.target.value)}
                    />
                  </Field>
                  <Field label="Emergency Service Available">
                    <select
                      value={form.emergencyServiceAvailable}
                      onChange={(e) =>
                        setField("emergencyServiceAvailable", e.target.value)
                      }
                    >
                      <option value="Yes">Yes</option>
                      <option value="No">No</option>
                    </select>
                  </Field>
                  <Field label="24×7 Facility">
                    <select
                      value={form.facility24x7}
                      onChange={(e) => setField("facility24x7", e.target.value)}
                    >
                      <option value="Yes">Yes</option>
                      <option value="No">No</option>
                    </select>
                  </Field>
                </div>
              )}

              {openSection === "subscription" && (
                <div className="grid-2">
                  <Field label="Subscription Plan">
                    <select
                      value={form.subscriptionPlan}
                      onChange={(e) => setField("subscriptionPlan", e.target.value)}
                    >
                      <option value="Basic">Basic</option>
                      <option value="Standard">Standard</option>
                      <option value="Premium">Premium</option>
                    </select>
                  </Field>
                  <Field label="Number of Users Required">
                    <input
                      type="number"
                      min={1}
                      value={form.numberOfUsers}
                      onChange={(e) => setField("numberOfUsers", e.target.value)}
                    />
                  </Field>
                  <label className="full-span">
                    Billing Address
                    <textarea
                      rows={2}
                      value={form.billingAddress}
                      onChange={(e) => setField("billingAddress", e.target.value)}
                    />
                  </label>
                  <Field label="Payment Method">
                    <select
                      value={form.paymentMethod}
                      onChange={(e) => setField("paymentMethod", e.target.value)}
                    >
                      <option value="">Select</option>
                      <option value="UPI">UPI</option>
                      <option value="Card">Card</option>
                      <option value="Net Banking">Net Banking</option>
                      <option value="Invoice">Invoice</option>
                    </select>
                  </Field>
                </div>
              )}

              {openSection === "system" && (
                <div className="grid-2">
                  <Field label="Time Zone">
                    <select
                      value={form.timeZone}
                      onChange={(e) => setField("timeZone", e.target.value)}
                    >
                      <option value="Asia/Kolkata">Asia/Kolkata</option>
                      <option value="UTC">UTC</option>
                      <option value="America/New_York">America/New_York</option>
                      <option value="Europe/London">Europe/London</option>
                    </select>
                  </Field>
                  <Field label="Currency">
                    <select
                      value={form.currency}
                      onChange={(e) => setField("currency", e.target.value)}
                    >
                      <option value="INR">INR</option>
                      <option value="USD">USD</option>
                      <option value="EUR">EUR</option>
                      <option value="AED">AED</option>
                    </select>
                  </Field>
                  <Field label="Language">
                    <select
                      value={form.language}
                      onChange={(e) => setField("language", e.target.value)}
                    >
                      <option value="English">English</option>
                      <option value="Hindi">Hindi</option>
                    </select>
                  </Field>
                  <Field label="Date Format">
                    <select
                      value={form.dateFormat}
                      onChange={(e) => setField("dateFormat", e.target.value)}
                    >
                      <option value="DD/MM/YYYY">DD/MM/YYYY</option>
                      <option value="MM/DD/YYYY">MM/DD/YYYY</option>
                      <option value="YYYY-MM-DD">YYYY-MM-DD</option>
                    </select>
                  </Field>
                </div>
              )}

              {openSection === "declaration" && (
                <div className="stack">
                  <label className="check-line">
                    <input
                      type="checkbox"
                      checked={form.infoAccurate}
                      onChange={(e) => setField("infoAccurate", e.target.checked)}
                    />
                    I confirm that all information provided is accurate.
                  </label>
                  <label className="check-line">
                    <input
                      type="checkbox"
                      checked={form.termsAccepted}
                      onChange={(e) => setField("termsAccepted", e.target.checked)}
                    />
                    I agree to the Terms & Conditions.
                  </label>
                  <label className="check-line">
                    <input
                      type="checkbox"
                      checked={form.privacyAccepted}
                      onChange={(e) => setField("privacyAccepted", e.target.checked)}
                    />
                    I agree to the Privacy Policy.
                  </label>
                </div>
              )}

              <div className="row" style={{ justifyContent: "flex-end", gap: "0.5rem" }}>
                <button type="button" className="secondary" onClick={() => setOpenSection(null)}>
                  Cancel
                </button>
                <button type="submit" className="secondary">
                  Save
                </button>
                <button type="button" onClick={goNextFromPopup}>
                  Save & Next
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </section>
  );
}
