import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../../api";
import { login as saveSession } from "../../auth";
import { useT } from "../../i18n";
import { toast } from "../../toast";

export default function PatientRegisterPage() {
  const t = useT();
  const navigate = useNavigate();
  const [form, setForm] = useState({
    userName: "",
    phone: "",
    email: "",
    password: "",
    confirm: "",
  });
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (!form.userName.trim()) {
      setError(t("User Name cannot be blank"));
      return;
    }
    if (!form.phone.trim()) {
      setError(t("Phone number cannot be blank"));
      return;
    }
    if (!form.password) {
      setError(t("Password cannot be blank"));
      return;
    }
    if (form.password.length < 4) {
      setError(t("Password must be at least 4 characters"));
      return;
    }
    if (form.password !== form.confirm) {
      setError(t("Passwords do not match"));
      return;
    }

    setSaving(true);
    try {
      const phone = form.phone.trim();
      const password = form.password;
      const userName = form.userName.trim();
      const res = await api.registerUser({
        userName,
        phone,
        email: form.email.trim() || undefined,
        password,
        createdBy: "SELF",
      });
      toast.success(res.message || t("Account created. Sign in with your username or phone."));
      try {
        const loginRes = await api.login({
          loginType: "PATIENT",
          id: userName || phone,
          password,
        });
        saveSession(
          loginRes.patientName || loginRes.username || userName,
          password,
          0,
          undefined,
          undefined,
          {
            loginType: "PATIENT",
            userId: loginRes.userId || loginRes.patientUserId || loginRes.patientId || res.userId,
            patientId: loginRes.userId || loginRes.patientId || loginRes.patientUserId || res.userId,
            role: "PATIENT",
            patientPhone: loginRes.patientPhone || phone,
            patientName: loginRes.patientName || loginRes.username || userName,
          },
        );
        navigate("/patient");
      } catch {
        navigate("/patient/login", { state: { loginId: userName || phone } });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : t("Registration failed"));
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
        <h1 style={{ color: "var(--brand-dark)" }}>{t("Patient sign up")}</h1>
        <p className="lead" style={{ marginBottom: "1rem" }}>
          {t("Create your patient account to book and manage appointments.")}
        </p>
        {error && <div className="msg error">{error}</div>}
        <form className="stack" onSubmit={onSubmit}>
          <label>
            {t("User Name")}
            <input
              value={form.userName}
              onChange={(e) => setForm({ ...form, userName: e.target.value })}
              placeholder="e.g. azherkhan"
              autoComplete="username"
              required
            />
          </label>
          <label>
            {t("Phone Number")}
            <input
              value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })}
              placeholder="e.g. 984394375"
              inputMode="tel"
              autoComplete="tel"
              required
            />
          </label>
          <label>
            {t("Email (for booking confirmation)")}
            <input
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              placeholder="e.g. you@example.com"
              autoComplete="email"
            />
          </label>
          <label>
            {t("Password")}
            <div className="password-field">
              <input
                type={showPassword ? "text" : "password"}
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                minLength={4}
                autoComplete="new-password"
                required
              />
              <button
                type="button"
                className="password-toggle"
                onClick={() => setShowPassword((v) => !v)}
                aria-pressed={showPassword}
                aria-label={showPassword ? t("Hide password") : t("Show password")}
              >
                {showPassword ? t("Hide") : t("Show")}
              </button>
            </div>
          </label>
          <label>
            {t("Confirm password")}
            <input
              type={showPassword ? "text" : "password"}
              value={form.confirm}
              onChange={(e) => setForm({ ...form, confirm: e.target.value })}
              minLength={4}
              autoComplete="new-password"
              required
            />
          </label>
          <button type="submit" disabled={saving}>
            {saving ? t("Creating…") : t("Create account")}
          </button>
        </form>
        <p className="lead" style={{ marginTop: "1rem", marginBottom: 0 }}>
          {t("Already registered?")}{" "}
          <Link to="/patient/login" style={{ textDecoration: "underline" }}>
            {t("Sign in")}
          </Link>
          {" · "}
          <Link to="/login" style={{ textDecoration: "underline" }}>
            {t("Back to login")}
          </Link>
        </p>
      </section>
    </div>
  );
}
