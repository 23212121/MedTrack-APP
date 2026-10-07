import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../../api";
import { IndiaStateCityFields } from "../../components/IndiaStateCityFields";
import { login as saveSession } from "../../auth";

export default function LabRegisterPage() {
  const navigate = useNavigate();
  const [labName, setLabName] = useState("");
  const [phone, setPhone] = useState("");
  const [address, setAddress] = useState("");
  const [city, setCity] = useState("");
  const [state, setState] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (!labName.trim() || !password) {
      setError("Lab name and password are required");
      return;
    }
    setSaving(true);
    try {
      const created = await api.selfRegisterLab({
        labName,
        phone,
        address,
        city,
        state,
        password,
      });
      const res = await api.login({
        loginType: "LAB",
        id: created.labCode || created.loginId || "",
        password,
      });
      saveSession(res.username || labName, password, res.hospitalId ?? "", res.hospitalName, res.hospitalCode, {
        loginType: "LAB",
        userId: res.userId || res.labTechnicianId,
        role: "LAB",
        accessToken: res.token,
        labTechnicianId: res.labTechnicianId || res.userId,
      });
      navigate("/lab");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not register lab");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="panel login-popup">
      <h1>Lab technician registration</h1>
      <p className="lead">Create a lab login to upload patient test reports.</p>
      {error && <div className="msg error">{error}</div>}
      <form className="stack" onSubmit={onSubmit}>
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
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={4}
            required
          />
        </label>
        <button type="submit" disabled={saving}>
          {saving ? "Saving…" : "Register lab"}
        </button>
      </form>
      <p className="lead" style={{ marginTop: "0.85rem" }}>
        Already have a lab code? <Link to="/login">Sign in</Link>
      </p>
    </section>
  );
}
