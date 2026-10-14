import { FormEvent, useEffect, useState } from "react";
import { api, type LabTechnician } from "../../api";
import { IndiaStateCityFields } from "../../components/IndiaStateCityFields";
import { session } from "../../dl/MedTrackSession";
import { toast } from "../../toast";

export default function LabsPage() {
  const hospitalId = session.getHospitalId();
  const [labs, setLabs] = useState<LabTechnician[]>([]);
  const [error, setError] = useState("");
  const [labName, setLabName] = useState("");
  const [phone, setPhone] = useState("");
  const [address, setAddress] = useState("");
  const [city, setCity] = useState("");
  const [state, setState] = useState("");
  const [password, setPassword] = useState("123456");
  const [saving, setSaving] = useState(false);
  const [lastCreated, setLastCreated] = useState<{ code: string; password: string } | null>(null);

  async function load() {
    try {
      const res = await api.labs(hospitalId);
      setLabs(res.labs || []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load labs");
    }
  }

  useEffect(() => {
    void load();
  }, [hospitalId]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setSaving(true);
    try {
      const created = await api.registerLab({
        labName,
        phone,
        address,
        city,
        state,
        password,
      });
      const code = created.labCode || created.loginId || "";
      setLastCreated({ code, password });
      toast.success(
        created.message ||
          `Lab registered. Login as Lab technician with code ${code}`,
      );
      setLabName("");
      setPhone("");
      setAddress("");
      setCity("");
      setState("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not register lab");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="stack">
      <h1>Diagnostic labs</h1>
      <p className="lead">
        Create a lab technician account for this hospital. Lab ID is generated automatically. They
        sign in on Login → Lab technician with that code and upload patient test reports.
      </p>
      {error && <div className="msg error">{error}</div>}
      {lastCreated?.code ? (
        <div className="msg ok">
          Lab technician ready. On Login choose <strong>Lab technician</strong>, then use code{" "}
          <strong>{lastCreated.code}</strong> and password <strong>{lastCreated.password}</strong>.
        </div>
      ) : null}

      <form className="panel stack" onSubmit={onSubmit}>
        <h2>Register lab technician</h2>
        <label>
          Lab tech ID (auto)
          <input
            value={hospitalId ? `LAB-${hospitalId}-${labs.length + 1}` : "Assigned after save"}
            readOnly
            aria-readonly="true"
          />
        </label>
        <p className="muted">
          Linked to this hospital. Final ID is assigned on save (e.g. LAB-{hospitalId || "…"}-1).
        </p>
        <label>
          Lab name
          <input value={labName} onChange={(e) => setLabName(e.target.value)} required />
        </label>
        <label>
          Phone
          <input value={phone} onChange={(e) => setPhone(e.target.value)} />
        </label>
        <label>
          Address
          <input value={address} onChange={(e) => setAddress(e.target.value)} />
        </label>
        <IndiaStateCityFields
          state={state}
          city={city}
          onStateChange={setState}
          onCityChange={setCity}
        />
        <label>
          Login password
          <input value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        <button type="submit" disabled={saving}>
          {saving ? "Saving…" : "Create lab technician account"}
        </button>
      </form>

      <div className="table-scroll panel">
        <table className="table">
          <thead>
            <tr>
              <th>Code</th>
              <th>Name</th>
              <th>City</th>
              <th>State</th>
              <th>Phone</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {labs.map((s) => (
              <tr key={s.id}>
                <td>{s.labCode}</td>
                <td>{s.labName}</td>
                <td>{s.city || "—"}</td>
                <td>{s.state || "—"}</td>
                <td>{s.phone || "—"}</td>
                <td>{s.status}</td>
                <td>
                  <button
                    type="button"
                    className="secondary"
                    onClick={() =>
                      void api
                        .setLabStatus(s.id, s.status === "ACTIVE" ? "INACTIVE" : "ACTIVE")
                        .then(() => load())
                    }
                  >
                    {s.status === "ACTIVE" ? "Deactivate" : "Activate"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
