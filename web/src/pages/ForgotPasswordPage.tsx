import { FormEvent, useState } from "react";
import { Link } from "react-router-dom";
import { useT } from "../i18n";

export default function ForgotPasswordPage() {
  const t = useT();
  const [id, setId] = useState("");
  const [submitted, setSubmitted] = useState(false);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!id.trim()) return;
    setSubmitted(true);
  }

  return (
    <section className="panel" style={{ maxWidth: 460, margin: "2rem auto" }}>
      <h1 style={{ color: "var(--brand-dark)" }}>{t("Forgot password")}</h1>
      <p className="lead">
        {t(
          "Enter your hospital ID or user ID. A hospital administrator can reset your password for you.",
        )}
      </p>

      {submitted ? (
        <div className="msg ok">
          {t(
            "Request noted for {id}. Please contact your hospital administrator to reset the password.",
            { id: id.trim() },
          )}
        </div>
      ) : (
        <form className="stack" onSubmit={onSubmit}>
          <label>
            {t("Hospital ID or User ID")}
            <input
              value={id}
              onChange={(e) => setId(e.target.value)}
              placeholder="e.g. 10001 or doctor ID"
              required
            />
          </label>
          <button type="submit">{t("Continue")}</button>
        </form>
      )}

      <p className="lead" style={{ marginTop: "1rem", marginBottom: 0 }}>
        <Link to="/login" style={{ textDecoration: "underline" }}>
          {t("Back to sign in")}
        </Link>
      </p>
    </section>
  );
}
