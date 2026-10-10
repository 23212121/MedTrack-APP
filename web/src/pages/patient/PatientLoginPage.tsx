import { FormEvent, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { api } from "../../api";
import { login as saveSession } from "../../auth";
import { useT } from "../../i18n";

export default function PatientLoginPage() {
  const t = useT();
  const navigate = useNavigate();
  const location = useLocation();
  const prefillId =
    typeof (location.state as { loginId?: string } | null)?.loginId === "string"
      ? (location.state as { loginId: string }).loginId
      : "";
  const [loginId, setLoginId] = useState(prefillId);
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (!loginId.trim() || !password) {
      setError(t("Enter username or phone number and password"));
      return;
    }
    if (password.length < 4) {
      setError(t("Password must be at least 4 characters"));
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
      setError(err instanceof Error ? err.message : t("Sign-in failed"));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="login-stage">
      <div className="login-stage-brand">
        <span className="login-brand-mark" aria-hidden="true">
          M
        </span>
        <span className="login-brand-text">
          <span className="login-brand-name">MediMitra</span>
          <span className="login-brand-tag">Healthier together</span>
        </span>
      </div>
      <section className="panel login-popup patient-auth-panel">
        <h1 style={{ color: "var(--brand-dark)" }}>{t("Patient Portal")}</h1>
        <p className="lead">
          {t("Sign in with username or phone number to open the patient portal.")}
        </p>
        {error && <div className="msg error">{error}</div>}
        <form className="stack" onSubmit={onSubmit}>
          <label>
            {t("Username / Phone Number")}
            <input
              value={loginId}
              onChange={(e) => setLoginId(e.target.value)}
              placeholder="azherkhan or 984394375"
              autoComplete="username"
              required
            />
          </label>
          <label>
            {t("Password")}
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
            {saving ? t("Signing in…") : t("Sign in")}
          </button>
        </form>
        <p className="lead" style={{ marginTop: "1rem", marginBottom: "0.45rem" }}>
          {t("New patient?")}{" "}
          <Link to="/patient/register" style={{ textDecoration: "underline" }}>
            {t("Patient sign up")}
          </Link>
          {" · "}
          <Link to="/login" style={{ textDecoration: "underline" }}>
            {t("Back to login")}
          </Link>
        </p>
        <p className="lead" style={{ marginBottom: 0 }}>
          <Link to="/book">{t("Book without login")}</Link> ·{" "}
          <Link to="/track">{t("Track by mobile")}</Link> ·{" "}
          <Link to="/queue">{t("Public queue board")}</Link>
        </p>
      </section>
    </div>
  );
}
