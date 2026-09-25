import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import PatientUserRegistrationModal from "../components/PatientUserRegistrationModal";
import { api } from "../api";
import { login as saveSession } from "../auth";
import { useT } from "../i18n";

type LoginMode = "HOSPITAL" | "USER" | "MEDICAL" | "PATIENT";

export default function LoginPage() {
  const t = useT();
  const navigate = useNavigate();
  const [mode, setMode] = useState<LoginMode>("HOSPITAL");
  const [id, setId] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [showPatientReg, setShowPatientReg] = useState(false);
  const [patientUserId, setPatientUserId] = useState("");
  const [patientPassword, setPatientPassword] = useState("");
  const [patientError, setPatientError] = useState("");
  const [patientSaving, setPatientSaving] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (!id.trim() || !password) {
      setError(
        t(
          mode === "HOSPITAL"
            ? "Enter hospital ID and password"
            : mode === "MEDICAL"
              ? "Enter medical store ID and password"
              : "Enter user ID and password",
        ),
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
          userId: res.userId || res.doctorId || res.medicalStoreId,
          role: res.role,
          accessToken: res.token,
          medicalStoreId: res.medicalStoreId,
        },
      );
      if (res.loginType === "MEDICAL" || mode === "MEDICAL") {
        navigate("/medical");
      } else if (
        res.loginType === "USER" ||
        res.role === "DOCTOR" ||
        mode === "USER"
      ) {
        navigate("/doctor-portal");
      } else {
        navigate("/");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : t("Sign-in failed"));
    } finally {
      setSaving(false);
    }
  }

  async function onPatientLogin(e: FormEvent) {
    e.preventDefault();
    setPatientError("");
    if (!patientUserId.trim() || !patientPassword) {
      setPatientError(t("Enter username or phone number and password"));
      return;
    }
    if (patientPassword.length < 4) {
      setPatientError(t("Password must be at least 4 characters"));
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
      setPatientError(err instanceof Error ? err.message : t("Sign-in failed"));
    } finally {
      setPatientSaving(false);
    }
  }

  return (
    <>
      <section className="panel login-popup">
        <h1 style={{ color: "var(--brand-dark)" }}>{t("MedTrack Clinic")}</h1>
        <p className="lead" style={{ marginBottom: "0.45rem" }}>
          <Link to="/book" style={{ textDecoration: "underline" }}>
            {t("Book an appointment without login")}
          </Link>
        </p>
        <p className="lead" style={{ marginBottom: "0.45rem" }}>
          <Link to="/track" style={{ textDecoration: "underline" }}>
            {t("Track appointment by mobile")}
          </Link>
        </p>
        <p className="lead" style={{ marginBottom: "1rem" }}>
          <Link to="/queue" style={{ textDecoration: "underline" }}>
            {t("Check patient queue")}
          </Link>
        </p>
        {(error || (mode === "PATIENT" && patientError)) && (
          <div className="msg error">{mode === "PATIENT" ? patientError || error : error}</div>
        )}

        <div className="login-mode" role="tablist" aria-label={t("Login type")}>
          <button
            type="button"
            role="tab"
            aria-selected={mode === "HOSPITAL"}
            className={mode === "HOSPITAL" ? "login-mode-btn is-active" : "login-mode-btn"}
            onClick={() => {
              setMode("HOSPITAL");
              setError("");
              setPatientError("");
            }}
          >
            {t("Hospital ID")}
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === "USER"}
            className={mode === "USER" ? "login-mode-btn is-active" : "login-mode-btn"}
            onClick={() => {
              setMode("USER");
              setError("");
              setPatientError("");
            }}
          >
            {t("User ID (Doctor)")}
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === "MEDICAL"}
            className={mode === "MEDICAL" ? "login-mode-btn is-active" : "login-mode-btn"}
            onClick={() => {
              setMode("MEDICAL");
              setError("");
              setPatientError("");
            }}
          >
            {t("Medical store")}
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === "PATIENT"}
            className={mode === "PATIENT" ? "login-mode-btn is-active" : "login-mode-btn"}
            onClick={() => {
              setMode("PATIENT");
              setError("");
              setPatientError("");
            }}
          >
            {t("Patient")}
          </button>
        </div>

        {mode === "PATIENT" ? (
          <form className="stack" onSubmit={onPatientLogin}>
            <label>
              {t("Username / Phone Number")}
              <input
                value={patientUserId}
                onChange={(e) => setPatientUserId(e.target.value)}
                placeholder="azherkhan or 984394375"
                autoComplete="username"
                required
              />
            </label>
            <label>
              {t("Password")}
              <div className="password-field">
                <input
                  type={showPassword ? "text" : "password"}
                  value={patientPassword}
                  onChange={(e) => setPatientPassword(e.target.value)}
                  autoComplete="current-password"
                  minLength={4}
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
            <button type="submit" disabled={patientSaving}>
              {patientSaving ? t("Signing in…") : t("Sign in")}
            </button>
          </form>
        ) : (
        <form className="stack" onSubmit={onSubmit}>
          <label>
            {t(
              mode === "HOSPITAL"
                ? "Hospital ID"
                : mode === "MEDICAL"
                  ? "Medical store ID"
                  : "User ID (Doctor)",
            )}
            <input
              value={id}
              onChange={(e) => setId(e.target.value)}
              placeholder={
                mode === "HOSPITAL"
                  ? "e.g. 10001"
                  : mode === "MEDICAL"
                    ? "e.g. MED-10001-1"
                    : "e.g. DOC-SEED-0001"
              }
              inputMode={mode === "HOSPITAL" ? "numeric" : "text"}
              autoComplete="username"
              required
            />
          </label>
          <label>
            {t("Password")}
            <div className="password-field">
              <input
                type={showPassword ? "text" : "password"}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
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
            {saving ? t("Signing in…") : t("Sign in")}
          </button>
        </form>
        )}

        <p className="lead" style={{ marginTop: "0.85rem", marginBottom: 0 }}>
          <Link to="/forgot-password" style={{ textDecoration: "underline" }}>
            {t("Forgot password?")}
          </Link>
        </p>

        <div className="panel" style={{ marginTop: "1.2rem", boxShadow: "none" }}>
          <h2 style={{ fontSize: "1.1rem" }}>{t("Hospital not registered yet?")}</h2>
          <p className="lead" style={{ marginBottom: "0.8rem" }}>
            {t("Register your hospital in the system.")}
          </p>
          <Link to="/hospital-register">
            <button type="button">{t("Hospital registration")}</button>
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
            setMode("PATIENT");
            setPatientError(
              err instanceof Error
                ? err.message
                : "Registered, but sign-in failed. Please sign in.",
            );
          }
        }}
      />
    </>
  );
}
