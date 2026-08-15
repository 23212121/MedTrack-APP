import { FormEvent, useState } from "react";
import { Link } from "react-router-dom";

export default function ForgotPasswordPage() {
  const [id, setId] = useState("");
  const [submitted, setSubmitted] = useState(false);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!id.trim()) return;
    setSubmitted(true);
  }

  return (
    <section className="panel" style={{ maxWidth: 460, margin: "2rem auto" }}>
      <h1 style={{ color: "var(--brand-dark)" }}>Forgot password</h1>
      <p className="lead">
        Enter your hospital ID or user ID. A hospital administrator can reset
        your password for you.
      </p>

      {submitted ? (
        <div className="msg ok">
          Request noted for <strong>{id.trim()}</strong>. Please contact your
          hospital administrator to reset the password.
        </div>
      ) : (
        <form className="stack" onSubmit={onSubmit}>
          <label>
            Hospital ID or User ID
            <input
              value={id}
              onChange={(e) => setId(e.target.value)}
              placeholder="e.g. 10001 or doctor ID"
              required
            />
          </label>
          <button type="submit">Continue</button>
        </form>
      )}

      <p className="lead" style={{ marginTop: "1rem", marginBottom: 0 }}>
        <Link to="/login" style={{ textDecoration: "underline" }}>
          Back to sign in
        </Link>
      </p>
    </section>
  );
}
