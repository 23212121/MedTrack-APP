import { FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  api,
  AppointmentPatientOption,
  PatientPortalDocument,
  PatientReport,
} from "../../api";
import { getPatientPhone, session } from "../../auth";

export default function PatientReportsPage() {
  const phone = getPatientPhone();
  const sessionPatientName = session.getPatientName();
  const [reports, setReports] = useState<PatientReport[]>([]);
  const [documents, setDocuments] = useState<PatientPortalDocument[]>([]);
  const [patients, setPatients] = useState<AppointmentPatientOption[]>([]);
  const [selectedPatientName, setSelectedPatientName] = useState("");
  const [filters, setFilters] = useState({ from: "", to: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [busyKey, setBusyKey] = useState("");

  async function loadPatients() {
    if (!phone) return [];
    const data = await api.appointmentPatients(phone);
    setPatients(data.patients || []);
    return data.patients || [];
  }

  async function loadReports(patientName: string, next = filters) {
    if (!phone) {
      setReports([]);
      setDocuments([]);
      return;
    }
    setLoading(true);
    setError("");
    try {
      const [reportData, docData] = await Promise.all([
        api.patientReports(phone, {
          patientName: patientName || undefined,
          from: next.from || undefined,
          to: next.to || undefined,
        }),
        api.patientDocuments(phone, patientName || undefined),
      ]);
      setReports(reportData.reports);
      if (reportData.patients?.length) {
        setPatients(reportData.patients);
      }
      setDocuments(docData.documents || []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load reports");
      setReports([]);
      setDocuments([]);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (!phone) return;
    (async () => {
      try {
        setLoading(true);
        setError("");
        const list = await loadPatients();
        const sessionName = sessionPatientName;
        if (list.length === 1) {
          setSelectedPatientName(list[0].patientName);
          await loadReports(list[0].patientName);
        } else if (sessionName && list.some((p) => p.patientName === sessionName)) {
          setSelectedPatientName(sessionName);
          await loadReports(sessionName);
        } else {
          setSelectedPatientName("");
          await loadReports("");
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to load patients");
        setLoading(false);
      }
    })();
  }, [phone]);

  function onPatientChange(name: string) {
    setSelectedPatientName(name);
    loadReports(name, filters);
  }

  function onFilter(e: FormEvent) {
    e.preventDefault();
    if (selectedPatientName) {
      loadReports(selectedPatientName, filters);
    }
  }

  const selectedDetail =
    patients.find((p) => p.patientName === selectedPatientName) || null;

  function downloadUrl(url: string) {
    if (!url) return "#";
    if (url.startsWith("http")) {
      return `/download-file?url=${encodeURIComponent(url)}`;
    }
    return url;
  }

  async function downloadDocument(doc: PatientPortalDocument) {
    if (!phone) return;
    const key = `${doc.id}-${doc.slot}`;
    setBusyKey(key);
    setError("");
    try {
      await api.downloadPatientPortalDocument(
        doc.id,
        doc.slot,
        phone,
        doc.documentName,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Download failed");
    } finally {
      setBusyKey("");
    }
  }

  return (
    <div className="patient-portal-stack">
      <section className="panel">
        <h2>Medical reports</h2>
        <p className="lead">
          View and download laboratory reports, prescriptions, discharge summaries, and hospital
          test documents. Lab uploads for this phone appear here automatically.
        </p>

        <form className="row patient-report-filters" onSubmit={onFilter}>
          <label>
            Patient name
            <select
              value={selectedPatientName}
              onChange={(e) => onPatientChange(e.target.value)}
              disabled={loading}
            >
              <option value="">
                {patients.length > 1
                  ? "All reports for this phone"
                  : patients.length === 0
                    ? "All reports for this phone"
                    : patients[0].patientName}
              </option>
              {patients.map((p) => (
                <option key={`${p.patientId || ""}-${p.patientName}`} value={p.patientName}>
                  {p.patientName}
                  {p.age !== undefined && p.age !== "" ? ` · age ${p.age}` : ""}
                </option>
              ))}
            </select>
          </label>
          <label>
            From
            <input
              type="date"
              value={filters.from}
              onChange={(e) => setFilters({ ...filters, from: e.target.value })}
            />
          </label>
          <label>
            To
            <input
              type="date"
              value={filters.to}
              onChange={(e) => setFilters({ ...filters, to: e.target.value })}
            />
          </label>
          <button type="submit" disabled={loading}>
            Filter
          </button>
        </form>

        {selectedDetail && (
          <p className="lead patient-report-selected-detail">
            Showing reports for <strong>{selectedDetail.patientName}</strong>
            {selectedDetail.patientId ? ` · ID ${selectedDetail.patientId}` : ""}
            {selectedDetail.phone ? ` · ${selectedDetail.phone}` : ""}
          </p>
        )}
      </section>

      {error && <div className="msg error">{error}</div>}

      <section className="panel">
        <h3 className="patient-report-section-title">Reports</h3>
        {loading ? (
          <p className="lead">Loading reports…</p>
        ) : reports.length === 0 ? (
          <p className="lead">
            {selectedPatientName
              ? `No reports found for ${selectedPatientName}.`
              : "No lab or hospital reports found for this phone yet."}
          </p>
        ) : (
          <ul className="patient-report-list">
            {reports.map((r) => (
              <li key={r.id} className="patient-report-item">
                <div>
                  <strong>{r.title}</strong>
                  <p className="lead">
                    {r.reportType}
                    {r.reportDate ? ` · ${r.reportDate}` : ""}
                    {r.doctorName ? ` · ${r.doctorName}` : ""}
                    {r.patientName ? ` · ${r.patientName}` : ""}
                  </p>
                  {r.description && <p className="lead">{r.description}</p>}
                </div>
                {r.fileUrl ? (
                  r.fileUrl.startsWith("/api/") ? (
                    <a href={downloadUrl(r.fileUrl)} className="btn-link">
                      Download
                    </a>
                  ) : (
                    <Link to={downloadUrl(r.fileUrl)} className="btn-link">
                      Download
                    </Link>
                  )
                ) : (
                  <span className="lead">No file</span>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="panel">
        <h3 className="patient-report-section-title">Documents</h3>
        {loading ? (
          <p className="lead">Loading documents…</p>
        ) : documents.length === 0 ? (
          <p className="lead">
            {selectedPatientName
              ? `No documents found for ${selectedPatientName}.`
              : "No uploaded documents found for this phone yet."}
          </p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Patient name</th>
                <th>Document type</th>
                <th>Hospital name</th>
                <th>Document link</th>
              </tr>
            </thead>
            <tbody>
              {documents.map((d) => {
                const key = `${d.id}-${d.slot}`;
                return (
                  <tr key={key}>
                    <td>{d.patientName}</td>
                    <td>{d.documentType}</td>
                    <td>{d.hospitalName}</td>
                    <td>
                      <button
                        type="button"
                        className="link-button"
                        disabled={busyKey === key}
                        onClick={() => void downloadDocument(d)}
                      >
                        {busyKey === key ? "Downloading…" : d.documentName}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
