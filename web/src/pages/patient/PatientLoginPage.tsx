import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../../api";
import { login as saveSession } from "../../auth";

export default function PatientLoginPage() {
  const navigate = useNavigate();
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (!loginId.trim() || !password) {
      setError("Enter username or phone number and password");
      return;
    }
    if (password.length < 4) {
      setError("Password must be at least 4 characters");
      return;
    }
    setSaving(true);
    try {
      const res = await api.login({
        loginType: "PATIENT",
        id: loginId.trim(),
        password,
      });
      saveSession(
        res.patientName || res.username || loginId.trim(),
        password,
        0,
        undefined,
        undefined,
        {
          loginType: "PATIENT",
          userId: res.userId || res.patientUserId || res.patientId,
          patientId: res.userId || res.patientId || res.patientUserId,
          role: "PATIENT",
          patientPhone: res.patientPhone || "",
          patientName: res.patientName || res.username,
        },
      );
      navigate("/patient");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign-in failed");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="panel patient-auth-panel">
      <h1 style={{ color: "var(--brand-dark)" }}>Patient Portal</h1>
      <p className="lead">
        Sign in with username or phone number to open the patient portal.
      </p>
      {error && <div className="msg error">{error}</div>}
      <form className="stack" onSubmit={onSubmit}>
        <label>
          Username / Phone Number
          <input
            value={loginId}
            onChange={(e) => setLoginId(e.target.value)}
            placeholder="azherkhan or 984394375"
            autoComplete="username"
            required
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            minLength={4}
            required
          />
        </label>
        <button type="submit" disabled={saving}>
          {saving ? "Signing in…" : "Sign in"}
        </button>
      </form>
      <p className="lead" style={{ marginTop: "1rem" }}>
        <Link to="/login">Register or login from main page</Link>
      </p>
      <p className="lead">
        <Link to="/book">Book without login</Link> ·{" "}
        <Link to="/track">Track by mobile</Link> ·{" "}
        <Link to="/queue">Public queue board</Link>
      </p>
    </section>
  );
}
