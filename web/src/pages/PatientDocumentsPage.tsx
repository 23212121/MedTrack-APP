import { FormEvent, useState } from "react";
import { api } from "../api";
import NativeFileInput from "../components/NativeFileInput";
import { session } from "../dl/MedTrackSession";
import { toast } from "../toast";

type PatientRow = {
  key: string;
  patientName: string;
  aadhaarNumber: string;
  phoneNumber: string;
  files: File[];
};

function newRow(): PatientRow {
  return {
    key: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    patientName: "",
    aadhaarNumber: "",
    phoneNumber: "",
    files: [],
  };
}

function FilePicker({ onPick }: { onPick: (list: File[]) => void }) {
  return (
    <NativeFileInput
      label="Upload documents (max 5)"
      multiple
      accept=".pdf,.png,.jpg,.jpeg,.doc,.docx,.tif,.tiff"
      onPick={(list) => onPick(list.slice(0, 5))}
    />
  );
}

export default function PatientDocumentsPage() {
  const hospitalId = session.getHospitalId();
  const [rows, setRows] = useState<PatientRow[]>([newRow()]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  function updateRow(key: string, patch: Partial<PatientRow>) {
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  }

  function onFilesChange(key: string, files: File[]) {
    updateRow(key, { files: files.slice(0, 5) });
  }

  function addRow() {
    setRows((prev) => [...prev, newRow()]);
  }

  function removeRow(key: string) {
    setRows((prev) => (prev.length <= 1 ? prev : prev.filter((r) => r.key !== key)));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (!hospitalId) {
      setError("Hospital session required. Log in as hospital admin.");
      return;
    }

    for (const row of rows) {
      if (!row.patientName.trim()) {
        setError("Patient name is required for every row.");
        return;
      }
      if (!/^\d{12}$/.test(row.aadhaarNumber.replace(/\s+/g, ""))) {
        setError("Aadhaar number must be 12 digits for every row.");
        return;
      }
      if (!row.phoneNumber.trim()) {
        setError("Phone number is required for every row.");
        return;
      }
      if (!row.files.length) {
        setError(`Upload at least one document for ${row.patientName.trim()}.`);
        return;
      }
      if (row.files.length > 5) {
        setError("Maximum 5 files per patient upload.");
        return;
      }
    }

    setSaving(true);
    try {
      let saved = 0;
      let s3Ok = 0;
      for (const row of rows) {
        const result = await api.uploadPatientDocuments({
          patientName: row.patientName.trim(),
          aadhaarNumber: row.aadhaarNumber.replace(/\s+/g, ""),
          phoneNumber: row.phoneNumber.trim(),
          files: row.files,
        });
        saved += 1;
        const dest = result.document?.destinationPath || result.s3Path || "";
        if (result.s3Uploaded || dest.startsWith("s3://") || dest.startsWith("patient-documents/")) {
          s3Ok += 1;
        }
      }
      toast.success(
        s3Ok === saved
          ? saved === 1
            ? "Document uploaded to Amazon S3."
            : `${saved} document sets uploaded to Amazon S3.`
          : saved === 1
            ? "Document saved."
            : `${saved} document sets saved.`,
      );
      setRows([newRow()]);
    } catch (err) {
      const raw = err instanceof Error ? err.message : "";
      setError(
        /s3|amazon|iam|permissions boundary|PutObject|accesspoint|medtrackdoc/i.test(raw)
          ? "Could not upload to Amazon S3. IAM user MedTrack-App needs s3:PutObject on bucket medtrackdoc (user policy and permissions boundary)."
          : raw || "Upload failed",
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="patient-docs-page">
      {error && <div className="msg error">{error}</div>}

      <form className="panel stack patient-docs-form" onSubmit={onSubmit}>
        {rows.map((row, index) => (
          <div key={row.key} className="patient-docs-row">
            <div className="patient-docs-row-head">
              <strong>Patient {index + 1}</strong>
              {rows.length > 1 && (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => removeRow(row.key)}
                >
                  Remove
                </button>
              )}
            </div>
            <div className="grid-2">
              <label>
                Patient name
                <input
                  value={row.patientName}
                  onChange={(e) => updateRow(row.key, { patientName: e.target.value })}
                  placeholder="Full name"
                  required
                />
              </label>
              <label>
                Aadhaar number
                <input
                  value={row.aadhaarNumber}
                  onChange={(e) => updateRow(row.key, { aadhaarNumber: e.target.value })}
                  placeholder="12-digit Aadhaar"
                  inputMode="numeric"
                  maxLength={14}
                  required
                />
              </label>
              <label>
                Phone number
                <input
                  value={row.phoneNumber}
                  onChange={(e) => updateRow(row.key, { phoneNumber: e.target.value })}
                  placeholder="Mobile number"
                  required
                />
              </label>
              <FilePicker onPick={(list) => onFilesChange(row.key, list)} />
            </div>
            {row.files.length > 0 && (
              <ul className="patient-docs-file-list">
                {row.files.map((f) => (
                  <li key={`${row.key}-${f.name}-${f.size}`}>{f.name}</li>
                ))}
              </ul>
            )}
          </div>
        ))}

        <div className="row patient-docs-actions">
          <button type="button" className="secondary" onClick={addRow}>
            Add another patient
          </button>
          <button type="submit" disabled={saving}>
            {saving ? "Uploading…" : "Upload documents"}
          </button>
        </div>
      </form>
    </section>
  );
}
