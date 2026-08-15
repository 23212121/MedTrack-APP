import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import PatientUserRegistrationModal from "../components/PatientUserRegistrationModal";
import { api } from "../api";
import { login as saveSession } from "../auth";

type LoginMode = "HOSPITAL" | "USER";

export default function LoginPage() {
  const navigate = useNavigate();
  const [mode, setMode] = useState<LoginMode>("HOSPITAL");
  const [id, setId] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [showPatientReg, setShowPatientReg] = useState(false);
  const [showPatientLogin, setShowPatientLogin] = useState(false);
  const [patientUserId, setPatientUserId] = useState("");
  const [patientPassword, setPatientPassword] = useState("");
  const [patientError, setPatientError] = useState("");
  const [patientSaving, setPatientSaving] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (!id.trim() || !password) {
      setError(
        mode === "HOSPITAL"
          ? "Enter hospital ID and password"
          : "Enter user ID and password",
      );
      return;
    }

    setSaving(true);
    try {
      const res = await api.login({
        loginType: mode,
        id: id.trim(),
        password,
      });
      saveSession(
        res.username || id.trim(),
        password,
        res.hospitalId ?? "",
        res.hospitalName,
        res.hospitalCode,
        {
          loginType: res.loginType,
          userId: res.userId || res.doctorId,
          role: res.role,
        },
      );
      // Doctors land on doctor portal; hospital admins on dashboard
      if (
        res.loginType === "USER" ||
        res.role === "DOCTOR" ||
        mode === "USER"
      ) {
        navigate("/doctor-portal");
      } else {
        navigate("/");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign-in failed");
    } finally {
      setSaving(false);
    }
  }

  async function onPatientLogin(e: FormEvent) {
    e.preventDefault();
    setPatientError("");
    if (!patientUserId.trim() || !patientPassword) {
      setPatientError("Enter username or phone number and password");
      return;
    }
    if (patientPassword.length < 4) {
      setPatientError("Password must be at least 4 characters");
      return;
    }
    setPatientSaving(true);
    try {
      const res = await api.login({
        loginType: "PATIENT",
        id: patientUserId.trim(),
        password: patientPassword,
      });
      saveSession(
        res.patientName || res.username || patientUserId.trim(),
        patientPassword,
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
      setPatientError(err instanceof Error ? err.message : "Sign-in failed");
    } finally {
      setPatientSaving(false);
    }
  }

  return (
    <>
      <section className="panel" style={{ maxWidth: 460, margin: "2rem auto" }}>
        <h1 style={{ color: "var(--brand-dark)" }}>MedTrack Clinic</h1>
        <p className="lead" style={{ marginBottom: "0.55rem" }}>
          Choose how to sign in — with a hospital ID or a user ID.
        </p>
        <p className="lead" style={{ marginBottom: "0.45rem" }}>
          <Link to="/book" style={{ textDecoration: "underline" }}>
            Book an appointment without login
          </Link>
        </p>
        <p className="lead" style={{ marginBottom: "0.45rem" }}>
          <Link to="/track" style={{ textDecoration: "underline" }}>
            Track appointment by mobile
          </Link>
        </p>
        <p className="lead" style={{ marginBottom: "1rem" }}>
          <Link to="/queue" style={{ textDecoration: "underline" }}>
            Check patient queue
          </Link>
        </p>
        <p className="lead" style={{ marginBottom: "1rem" }}>
          <button
            type="button"
            className="link-button"
            onClick={() => {
              setShowPatientLogin(true);
              setPatientError("");
            }}
          >
            Login as Patient
          </button>
          {" · "}
          <button
            type="button"
            className="link-button"
            onClick={() => {
              setMode("USER");
              setShowPatientReg(false);
              setShowPatientLogin(false);
              setError("");
              setId("");
              setPassword("");
            }}
          >
            Login as doctor
          </button>
        </p>
        {error && <div className="msg error">{error}</div>}

        <div className="login-mode" role="tablist" aria-label="Login type">
          <button
            type="button"
            role="tab"
            aria-selected={mode === "HOSPITAL"}
            className={mode === "HOSPITAL" ? "login-mode-btn is-active" : "login-mode-btn"}
            onClick={() => {
              setMode("HOSPITAL");
              setError("");
            }}
          >
            Hospital ID
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === "USER"}
            className={mode === "USER" ? "login-mode-btn is-active" : "login-mode-btn"}
            onClick={() => {
              setMode("USER");
              setError("");
            }}
          >
            User ID (Doctor)
          </button>
        </div>

        <form className="stack" onSubmit={onSubmit}>
          <label>
            {mode === "HOSPITAL" ? "Hospital ID" : "User ID (Doctor)"}
            <input
              value={id}
              onChange={(e) => setId(e.target.value)}
              placeholder={mode === "HOSPITAL" ? "e.g. 10001" : "e.g. DOC-SEED-0001"}
              inputMode={mode === "HOSPITAL" ? "numeric" : "text"}
              autoComplete="username"
              required
            />
          </label>
          <label>
            Password
            <div className="password-field">
              <input
                type={showPassword ? "text" : "password"}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                placeholder="123456"
                required
              />
              <button
                type="button"
                className="password-toggle"
                onClick={() => setShowPassword((v) => !v)}
                aria-pressed={showPassword}
                aria-label={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? "Hide" : "Show"}
              </button>
            </div>
          </label>
          <button type="submit" disabled={saving}>
            {saving ? "Signing in…" : "Sign in"}
          </button>
        </form>

        <div className="panel login-demo-creds" style={{ marginTop: "1rem", boxShadow: "none" }}>
          <h2 style={{ fontSize: "1.05rem", marginBottom: "0.4rem" }}>Demo login (password for all: 123456)</h2>
          {mode === "HOSPITAL" ? (
            <ul className="login-demo-list">
              <li>
                <strong>Hospital ID:</strong> 10001 — Test Hospital
              </li>
              <li>
                <strong>Hospital ID:</strong> 10002 — Sunrise Care Hospital
              </li>
              <li>
                <strong>Hospital ID:</strong> 10003 — City Heart Institute
              </li>
            </ul>
          ) : (
            <ul className="login-demo-list">
              <li>
                <strong>User ID:</strong> DOC-SEED-0001 — Dr. Aarav Sharma (Hospital 10001)
              </li>
              <li>
                <strong>User ID:</strong> DOC-SEED-0002 — Dr. Vivaan Verma (Hospital 10002)
              </li>
              <li>
                <strong>User ID:</strong> DOC-SEED-0005 — Dr. Arjun Nair (Hospital 10005)
              </li>
            </ul>
          )}
          <p className="muted" style={{ margin: "0.4rem 0 0" }}>
            Click a User ID / Hospital ID above to fill the form, then Sign in.
          </p>
          <div className="row" style={{ marginTop: "0.55rem" }}>
            {(mode === "HOSPITAL"
              ? [
                  { id: "10001", label: "10001" },
                  { id: "10002", label: "10002" },
                  { id: "10003", label: "10003" },
                ]
              : [
                  { id: "DOC-SEED-0001", label: "DOC-SEED-0001" },
                  { id: "DOC-SEED-0002", label: "DOC-SEED-0002" },
                  { id: "DOC-SEED-0005", label: "DOC-SEED-0005" },
                ]
            ).map((item) => (
              <button
                key={item.id}
                type="button"
                className="secondary"
                onClick={() => {
                  setId(item.id);
                  setPassword("123456");
                  setError("");
                }}
              >
                Use {item.label}
              </button>
            ))}
          </div>
        </div>

        <p className="lead" style={{ marginTop: "0.85rem", marginBottom: 0 }}>
          <Link to="/forgot-password" style={{ textDecoration: "underline" }}>
            Forgot password?
          </Link>
        </p>

        <div className="panel" style={{ marginTop: "1.2rem", boxShadow: "none" }}>
          <h2 style={{ fontSize: "1.1rem" }}>Hospital not registered yet?</h2>
          <p className="lead" style={{ marginBottom: "0.8rem" }}>
            Register your hospital in the system.
          </p>
          <Link to="/hospital-register">
            <button type="button">Hospital registration</button>
          </Link>
        </div>
      </section>

      <PatientUserRegistrationModal
        open={showPatientReg}
        onClose={() => setShowPatientReg(false)}
        onRegistered={async ({ userId, userName, phone, password }) => {
          // user_id is backend-generated; sign into patient portal with username/phone.
          setShowPatientReg(false);
          setPatientUserId(userName || phone || userId);
          setPatientPassword(password);
          try {
            const res = await api.login({
              loginType: "PATIENT",
              id: userName || phone || userId,
              password,
            });
            saveSession(
              res.patientName || res.username || userName,
              password,
              0,
              undefined,
              undefined,
              {
                loginType: "PATIENT",
                userId: res.userId || res.patientUserId || res.patientId || userId,
                patientId: res.userId || res.patientId || res.patientUserId || userId,
                role: "PATIENT",
                patientPhone: res.patientPhone || phone || "",
                patientName: res.patientName || res.username || userName,
              },
            );
            navigate("/patient");
          } catch (err) {
            setShowPatientLogin(true);
            setPatientError(
              err instanceof Error
                ? err.message
                : "Registered, but sign-in failed. Please sign in.",
            );
          }
        }}
      />

      {showPatientLogin && (
        <div
          className="modal-backdrop"
          role="presentation"
          onClick={() => setShowPatientLogin(false)}
        >
          <div
            className="modal panel patient-reg-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="patient-login-title"
            onClick={(e) => e.stopPropagation()}
          >
            <header className="modal-header">
              <div>
                <p className="modal-eyebrow">Patient Portal</p>
                <h2 id="patient-login-title">Login as Patient</h2>
              </div>
              <button
                type="button"
                className="modal-close"
                aria-label="Close"
                onClick={() => setShowPatientLogin(false)}
              >
                ×
              </button>
            </header>
            {patientError && <div className="msg error">{patientError}</div>}
            <form className="stack" onSubmit={onPatientLogin}>
              <label>
                Username / Phone Number
                <input
                  value={patientUserId}
                  onChange={(e) => setPatientUserId(e.target.value)}
                  placeholder="azherkhan or 984394375"
                  autoComplete="username"
                  required
                />
              </label>
              <label>
                Password
                <input
                  type="password"
                  value={patientPassword}
                  onChange={(e) => setPatientPassword(e.target.value)}
                  minLength={4}
                  required
                />
              </label>
              <div className="modal-actions">
                <button type="submit" disabled={patientSaving}>
                  {patientSaving ? "Signing in…" : "Sign in"}
                </button>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setShowPatientLogin(false)}
                >
                  Cancel
                </button>
              </div>
            </form>
            <p className="lead" style={{ marginBottom: 0 }}>
              Doctor?{" "}
              <button
                type="button"
                className="link-button"
                onClick={() => {
                  setShowPatientLogin(false);
                  setMode("USER");
                  setError("");
                }}
              >
                Login as doctor
              </button>
            </p>
          </div>
        </div>
      )}
    </>
  );
}
