import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api, PatientDocument } from "../api";
import { session } from "../dl/MedTrackSession";

type DocumentListRow = {
  key: string;
  documentId: string;
  slot: number;
  patientName: string;
  aadhaarNumber: string;
  phoneNumber: string;
  documentName: string;
  uploadDate: string;
};

function flattenDocuments(docs: PatientDocument[]): DocumentListRow[] {
  const rows: DocumentListRow[] = [];
  for (const doc of docs) {
    const files = [
      doc.fileUpload1,
      doc.fileUpload2,
      doc.fileUpload3,
      doc.fileUpload4,
      doc.fileUpload5,
    ]
      .map((name, idx) => ({ name, slot: idx + 1 }))
      .filter((x): x is { name: string; slot: number } => !!x.name);

    const uploadDate = doc.creationDate
      ? new Date(doc.creationDate).toLocaleString()
      : "—";

    if (files.length === 0) {
      rows.push({
        key: `${doc.id}-none`,
        documentId: doc.id,
        slot: 0,
        patientName: doc.patientName,
        aadhaarNumber: doc.aadhaarNumber || "—",
        phoneNumber: doc.phoneNumber,
        documentName: doc.sourcePath || "—",
        uploadDate,
      });
      continue;
    }

    for (const f of files) {
      const displayName = f.name.replace(/^\d+_/, "") || f.name;
      rows.push({
        key: `${doc.id}-${f.slot}`,
        documentId: doc.id,
        slot: f.slot,
        patientName: doc.patientName,
        aadhaarNumber: doc.aadhaarNumber || "—",
        phoneNumber: doc.phoneNumber,
        documentName: displayName,
        uploadDate,
      });
    }
  }
  return rows;
}

export default function CheckDocumentsPage() {
  const hospitalId = session.getHospitalId();
  const [documents, setDocuments] = useState<PatientDocument[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [busyKey, setBusyKey] = useState("");

  async function load() {
    if (!hospitalId) {
      setError("Hospital session required. Log in as hospital admin.");
      setDocuments([]);
      return;
    }
    setLoading(true);
    setError("");
    try {
      // Backend: WHERE hospital_id = session hospital only
      const data = await api.listPatientDocuments();
      setDocuments(data.documents ?? []);
    } catch (e) {
      setDocuments([]);
      setError(e instanceof Error ? e.message : "Failed to load documents");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [hospitalId]);

  const rows = useMemo(() => {
    const all = flattenDocuments(documents);
    const q = query.trim().toLowerCase();
    if (!q) return all;
    return all.filter(
      (r) =>
        r.patientName.toLowerCase().includes(q) ||
        r.aadhaarNumber.toLowerCase().includes(q) ||
        r.phoneNumber.toLowerCase().includes(q) ||
        r.documentName.toLowerCase().includes(q),
    );
  }, [documents, query]);

  async function downloadRow(row: DocumentListRow) {
    if (!row.slot) return;
    setBusyKey(row.key);
    setError("");
    try {
      await api.downloadPatientDocument(row.documentId, row.slot, row.documentName);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Download failed");
    } finally {
      setBusyKey("");
    }
  }

  return (
    <section className="check-docs-page">
      <h1>Check document</h1>
      <p className="lead">
        Documents uploaded by your hospital only. Other hospitals’ files are not shown.
      </p>

      {error && <div className="msg error">{error}</div>}

      <div className="panel">
        <div className="row check-docs-toolbar">
          <label className="check-docs-search">
            Search
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Patient, Aadhaar, mobile, or document"
            />
          </label>
          <button type="button" className="secondary" onClick={() => void load()}>
            Refresh
          </button>
          <Link to="/patient-documents" className="button-link">
            Upload documents
          </Link>
        </div>

        {loading ? (
          <p className="muted">Loading…</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Patient name</th>
                <th>Aadhaar card</th>
                <th>Mobile number</th>
                <th>Document name</th>
                <th>Upload date</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.key}>
                  <td>{r.patientName}</td>
                  <td>{r.aadhaarNumber}</td>
                  <td>{r.phoneNumber}</td>
                  <td>
                    {r.slot ? (
                      <button
                        type="button"
                        className="link-button"
                        disabled={busyKey === r.key}
                        onClick={() => void downloadRow(r)}
                      >
                        {busyKey === r.key ? "Downloading…" : r.documentName}
                      </button>
                    ) : (
                      r.documentName
                    )}
                  </td>
                  <td>{r.uploadDate}</td>
                </tr>
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={5}>
                    {documents.length === 0
                      ? "No documents uploaded by your hospital yet."
                      : "No documents match your search."}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}
