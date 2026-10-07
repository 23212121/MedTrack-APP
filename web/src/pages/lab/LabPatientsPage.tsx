import { FormEvent, useEffect, useRef, useState } from "react";
import NativeFileInput from "../../components/NativeFileInput";
import { api, type LabPatientRow, type LabTechnician } from "../../api";
import { toast } from "../../toast";

type Detail = {
  name: string;
  phone: string;
  aadhaar: string;
  age: string;
  gender: string;
};

function splitPhoneOrAadhaar(raw: string): { phone?: string; aadhaar?: string } {
  const digits = (raw || "").replace(/\D/g, "");
  if (!digits) return {};
  if (digits.length === 12) return { aadhaar: digits };
  return { phone: digits };
}

function normalizeGender(raw?: string): string {
  const g = (raw || "").trim().toUpperCase();
  if (g === "M" || g === "MALE") return "Male";
  if (g === "F" || g === "FEMALE") return "Female";
  if (g === "O" || g === "OTHER") return "Other";
  if (raw === "Male" || raw === "Female" || raw === "Other") return raw;
  return raw || "";
}

function fromRow(row: LabPatientRow, entered: string): Detail {
  const parsed = splitPhoneOrAadhaar(entered);
  return {
    name: row.userName || row.patientName || "",
    phone: row.phone || parsed.phone || "",
    aadhaar: row.aadhaarNumber || parsed.aadhaar || "",
    age: row.age == null || row.age === "" ? "" : String(row.age),
    gender: normalizeGender(row.gender),
  };
}

function emptyDetail(entered: string): Detail {
  const parsed = splitPhoneOrAadhaar(entered);
  return {
    name: "",
    phone: parsed.phone || "",
    aadhaar: parsed.aadhaar || "",
    age: "",
    gender: "",
  };
}

export default function LabPatientsPage() {
  const [lab, setLab] = useState<LabTechnician | null>(null);
  const [idQuery, setIdQuery] = useState("");
  const [details, setDetails] = useState<Detail[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [busyKey, setBusyKey] = useState("");
  const [title, setTitle] = useState("Lab test report");
  const [notes, setNotes] = useState("");
  const lastLookup = useRef("");

  useEffect(() => {
    api
      .labMe()
      .then((res) => setLab(res.lab))
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load lab ID"));
  }, []);

  async function fetchDetails(raw = idQuery, e?: FormEvent) {
    e?.preventDefault();
    setError("");
    const parsed = splitPhoneOrAadhaar(raw);
    if (!parsed.phone && !parsed.aadhaar) {
      setError("Enter a phone number or a 12-digit Aadhaar number.");
      setDetails([]);
      return;
    }
    const key = `${parsed.phone || ""}:${parsed.aadhaar || ""}`;
    lastLookup.current = key;
    setLoading(true);
    try {
      const res = await api.labPatientDetails(parsed);
      if (lastLookup.current !== key) return;
      const rows = res.patients || [];
      if (rows.length === 0) {
        setDetails([emptyDetail(raw)]);
        setError("No record yet. Fill name, age, and gender, then upload.");
      } else {
        setDetails(rows.map((row) => fromRow(row, raw)));
      }
    } catch (err) {
      if (lastLookup.current !== key) return;
      setDetails([emptyDetail(raw)]);
      setError(err instanceof Error ? err.message : "Could not fetch patient details");
    } finally {
      if (lastLookup.current === key) setLoading(false);
    }
  }

  useEffect(() => {
    const digits = idQuery.replace(/\D/g, "");
    if (digits.length !== 10 && digits.length !== 12) return;
    const timer = window.setTimeout(() => {
      void fetchDetails(idQuery);
    }, 350);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- lookup when ID length is complete
  }, [idQuery]);

  function patch(index: number, next: Partial<Detail>) {
    setDetails((rows) => rows.map((row, i) => (i === index ? { ...row, ...next } : row)));
  }

  async function uploadRow(index: number, files: File[]) {
    const row = details[index];
    if (!row) return;
    const phone = row.phone.replace(/\D/g, "");
    const aadhaar = row.aadhaar.replace(/\D/g, "");
    if (!phone && aadhaar.length !== 12) {
      setError("Enter a phone number or a 12-digit Aadhaar number before uploading.");
      return;
    }
    if (files.length === 0) return;
    setBusyKey(String(index));
    setError("");
    try {
      const res = await api.uploadLabReports({
        patientName: row.name,
        phone: phone || undefined,
        aadhaar: aadhaar || undefined,
        age: row.age,
        gender: row.gender,
        title,
        notes,
        files,
      });
      toast.success(res.message || `Uploaded ${files.length} file(s)`);
      await fetchDetails(phone || aadhaar || idQuery);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Upload failed");
    } finally {
      setBusyKey("");
    }
  }

  const labId = lab?.labId || lab?.labCode || "";
  const assistant = lab?.assistantName || lab?.labName || "";
  const hospitalLabel = lab?.hospitalName
    ? `${lab.hospitalName}${lab.hospitalId ? ` (#${lab.hospitalId})` : ""}`
    : lab?.hospitalId
      ? `Hospital #${lab.hospitalId}`
      : "";

  return (
    <section className="stack lab-patients">
      <h1>Patient reports</h1>
      <p className="lead">
        Lab ID is filled from this hospital lab. Enter the patient mobile or Aadhaar to load
        username, age, and gender. You can edit those fields before upload.
      </p>
      {error && <div className="msg error">{error}</div>}

      <div className="panel row patient-report-filters">
        <label>
          Lab ID
          <input value={labId} readOnly aria-readonly="true" />
        </label>
        <label>
          Lab assistant
          <input value={assistant} readOnly aria-readonly="true" />
        </label>
      </div>
      {hospitalLabel && (
        <p className="muted">Linked to {hospitalLabel}. Lab ID stays with this hospital.</p>
      )}

      <form className="panel row patient-report-filters" onSubmit={(e) => void fetchDetails(idQuery, e)}>
        <label>
          Phone number / Aadhaar number
          <input
            value={idQuery}
            onChange={(e) => setIdQuery(e.target.value)}
            inputMode="numeric"
            placeholder="10-digit phone or 12-digit Aadhaar"
            autoComplete="off"
          />
        </label>
        <button type="submit" disabled={loading}>
          {loading ? "Fetching…" : "Fetch details"}
        </button>
      </form>

      <div className="panel stack">
        <label>
          Report title
          <input value={title} onChange={(e) => setTitle(e.target.value)} />
        </label>
        <label>
          Notes
          <input value={notes} onChange={(e) => setNotes(e.target.value)} />
        </label>
      </div>

      <div className="table-scroll panel">
        <table className="table lab-patient-table">
          <thead>
            <tr>
              <th>Name / username</th>
              <th>Mobile</th>
              <th>Aadhaar</th>
              <th>Age</th>
              <th>Gender</th>
              <th>Upload files</th>
            </tr>
          </thead>
          <tbody>
            {details.length === 0 ? (
              <tr>
                <td colSpan={6}>
                  {loading
                    ? "Looking up patient…"
                    : "Enter a mobile or Aadhaar number. Name, age, and gender fill from the detail table."}
                </td>
              </tr>
            ) : (
              details.map((row, index) => (
                <tr key={`${row.phone}-${row.aadhaar}-${index}`}>
                  <td>
                    <input
                      className="lab-cell-input lab-id-input"
                      value={row.name}
                      onChange={(e) => patch(index, { name: e.target.value })}
                      placeholder="Patient name"
                      aria-label="Patient name"
                    />
                  </td>
                  <td>
                    <input
                      className="lab-cell-input"
                      value={row.phone}
                      onChange={(e) => patch(index, { phone: e.target.value })}
                      inputMode="numeric"
                      aria-label="Mobile number"
                    />
                  </td>
                  <td>
                    <input
                      className="lab-cell-input"
                      value={row.aadhaar}
                      onChange={(e) => patch(index, { aadhaar: e.target.value })}
                      inputMode="numeric"
                      maxLength={14}
                      aria-label="Aadhaar number"
                    />
                  </td>
                  <td>
                    <input
                      className="lab-cell-input"
                      value={row.age}
                      onChange={(e) => patch(index, { age: e.target.value.replace(/\D/g, "").slice(0, 3) })}
                      inputMode="numeric"
                      aria-label="Age"
                    />
                  </td>
                  <td>
                    <select
                      className="lab-cell-input"
                      value={["", "Male", "Female", "Other"].includes(row.gender) ? row.gender : ""}
                      onChange={(e) => patch(index, { gender: e.target.value })}
                      aria-label="Gender"
                    >
                      <option value="">Select</option>
                      <option value="Male">Male</option>
                      <option value="Female">Female</option>
                      <option value="Other">Other</option>
                    </select>
                  </td>
                  <td className="lab-upload-cell">
                    <NativeFileInput
                      multiple
                      label={busyKey === String(index) ? "Uploading…" : "Upload file"}
                      disabled={busyKey === String(index)}
                      onPick={(files) => void uploadRow(index, files)}
                    />
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
