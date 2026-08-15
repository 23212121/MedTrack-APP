"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("employee@clinic.local");
  const [password, setPassword] = useState("password123");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError("");
    const res = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    const data = await res.json();
    setLoading(false);
    if (!res.ok) {
      setError(data.error || "Login failed");
      return;
    }
    router.push("/");
    router.refresh();
  }

  return (
    <main>
      <div className="card" style={{ maxWidth: 460, margin: "3rem auto" }}>
        <p className="muted" style={{ marginTop: 0 }}>
          Visit journey tracker
        </p>
        <h1 className="hero-brand">MedTrack Clinic</h1>
        <p className="muted">
          Sign in as employee, doctor, patient, or admin.
        </p>
        <div className="stack" style={{ marginBottom: "1.25rem" }}>
          <Link href="/book" className="btn" style={{ textAlign: "center" }}>
            Book Appointment
          </Link>
          <p className="muted" style={{ margin: 0, fontSize: "0.85rem" }}>
            No login needed — book a visit as a guest.
          </p>
        </div>
        <hr style={{ border: 0, borderTop: "1px solid var(--line)", margin: "0 0 1.25rem" }} />
        <form className="stack" onSubmit={onSubmit}>
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </label>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>
          {error ? <p className="error">{error}</p> : null}
          <button type="submit" disabled={loading}>
            {loading ? "Signing in…" : "Sign in"}
          </button>
        </form>

        <div
          className="card"
          style={{
            marginTop: "1.25rem",
            padding: "1rem",
            background: "#f4fafb",
            boxShadow: "none",
          }}
        >
          <p style={{ margin: "0 0 0.5rem", fontWeight: 600 }}>
            Doctor not registered yet?
          </p>
          <p className="muted" style={{ margin: "0 0 0.75rem", fontSize: "0.9rem" }}>
            Register yourself in the system as a doctor.
          </p>
          <Link
            href="/doctor-register"
            className="btn"
            style={{ display: "block", textAlign: "center" }}
          >
            Doctor registration
          </Link>
        </div>

        <p className="muted" style={{ marginTop: "1rem" }}>
          Other account? <Link href="/register">Create patient/staff account</Link>
        </p>
        <p className="muted" style={{ fontSize: "0.85rem" }}>
          Desk: employee@clinic.local or employee.apollo@clinic.local /
          password123
          <br />
          Doctor: doctor@clinic.local or doctor.apollo@clinic.local /
          password123
        </p>
      </div>
    </main>
  );
}
