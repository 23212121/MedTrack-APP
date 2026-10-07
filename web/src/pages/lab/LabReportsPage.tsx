import { useEffect, useState } from "react";
import { api, type PatientDocument } from "../../api";

export default function LabReportsPage() {
  const [rows, setRows] = useState<PatientDocument[]>([]);
  const [labName, setLabName] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    api
      .labReports()
      .then((res) => {
        setRows(res.documents || []);
        setLabName(res.labName || "");
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load uploads"))
      .finally(() => setLoading(false));
  }, []);

  return (
    <section className="stack">
      <h1>Uploaded reports</h1>
      <p className="lead">
        Test files {labName ? `from ${labName}` : "this lab"} has stored for patients. Patients open
        the same files on Reports using their phone number.
      </p>
      {error && <div className="msg error">{error}</div>}
      {loading ? (
        <p className="lead">Loading…</p>
      ) : (
        <div className="table-scroll panel">
          <table className="table">
            <thead>
              <tr>
                <th>Patient</th>
                <th>Phone</th>
                <th>Aadhaar</th>
                <th>Type</th>
                <th>Storage path</th>
                <th>Uploaded</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <tr>
                  <td colSpan={6}>No reports uploaded yet.</td>
                </tr>
              ) : (
                rows.map((d) => (
                  <tr key={d.id}>
                    <td>{d.patientName}</td>
                    <td>{d.phoneNumber}</td>
                    <td>{d.aadhaarNumber || "—"}</td>
                    <td>{d.documentType || "LAB"}</td>
                    <td className="lab-path-cell">{d.filePath || d.destinationPath || "—"}</td>
                    <td>{d.creationDate ? d.creationDate.slice(0, 10) : "—"}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
