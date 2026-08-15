"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";

export default function RegisterPage() {
  const router = useRouter();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<"EMPLOYEE" | "DOCTOR" | "PATIENT">(
    "PATIENT"
  );
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError("");
    const res = await fetch("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        fullName,
        email: email || undefined,
        phone: phone || undefined,
        password,
        role,
      }),
    });
    const data = await res.json();
    setLoading(false);
    if (!res.ok) {
      setError(data.error || "Registration failed");
      return;
    }
    router.push("/");
    router.refresh();
  }

  return (
    <main>
      <div className="card" style={{ maxWidth: 520, margin: "2rem auto" }}>
        <h1 className="hero-brand">Create account</h1>
        <p className="muted">Register as patient, employee, or doctor.</p>
        <form className="stack" onSubmit={onSubmit}>
          <label>
            Full name
            <input
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              required
            />
          </label>
          <label>
            Role
            <select
              value={role}
              onChange={(e) =>
                setRole(e.target.value as "EMPLOYEE" | "DOCTOR" | "PATIENT")
              }
            >
              <option value="PATIENT">Patient</option>
              <option value="EMPLOYEE">Employee</option>
              <option value="DOCTOR">Doctor (basic account)</option>
            </select>
          </label>
          {role === "DOCTOR" ? (
            <p className="muted" style={{ margin: 0, fontSize: "0.9rem" }}>
              For full doctor profile (credentials + auto Doctor ID), use{" "}
              <Link href="/doctor-register">Doctor registration</Link> instead.
            </p>
          ) : null}
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>
          <label>
            Phone
            <input
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="Required for patients"
            />
          </label>
          <label>
            Password (min 8)
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={8}
              required
            />
          </label>
          {error ? <p className="error">{error}</p> : null}
          <button type="submit" disabled={loading}>
            {loading ? "Creating…" : "Register"}
          </button>
        </form>
        <p className="muted" style={{ marginTop: "1rem" }}>
          Already registered? <Link href="/login">Sign in</Link>
        </p>
      </div>
    </main>
  );
}
