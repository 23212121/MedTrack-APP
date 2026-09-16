import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../../api";
import { toast } from "../../toast";

export default function PatientRegisterPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    name: "",
    phone: "",
    password: "",
    confirm: "",
    age: "",
    gender: "",
    email: "",
  });
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (form.password !== form.confirm) {
      setError("Passwords do not match");
      return;
    }
    setSaving(true);
    try {
      await api.registerPatient({
        name: form.name.trim(),
        phone: form.phone.trim(),
        password: form.password,
        age: form.age ? Number(form.age) : undefined,
        gender: form.gender || undefined,
        email: form.email || undefined,
      });
      toast.success("Account created. Sign in with your phone and password.");
      setTimeout(() => navigate("/patient/login"), 1200);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="panel patient-auth-panel">
      <h1 style={{ color: "var(--brand-dark)" }}>Patient Registration</h1>
      <p className="lead">Create your portal account to manage appointments.</p>
      {error && <div className="msg error">{error}</div>}
      <form className="stack" onSubmit={onSubmit}>
        <label>
          Full name
          <input
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            required
          />
        </label>
        <label>
          Mobile number
          <input
            value={form.phone}
            onChange={(e) => setForm({ ...form, phone: e.target.value })}
            inputMode="tel"
            required
          />
        </label>
        <div className="grid-2">
          <label>
            Age
            <input
              type="number"
              min={1}
              value={form.age}
              onChange={(e) => setForm({ ...form, age: e.target.value })}
            />
          </label>
          <label>
            Gender
            <select
              value={form.gender}
              onChange={(e) => setForm({ ...form, gender: e.target.value })}
            >
              <option value="">Select</option>
              <option value="Male">Male</option>
              <option value="Female">Female</option>
              <option value="Other">Other</option>
            </select>
          </label>
        </div>
        <label>
          Email (optional)
          <input
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            minLength={6}
            required
          />
        </label>
        <label>
          Confirm password
          <input
            type="password"
            value={form.confirm}
            onChange={(e) => setForm({ ...form, confirm: e.target.value })}
            required
          />
        </label>
        <button type="submit" disabled={saving}>
          {saving ? "Creating…" : "Create account"}
        </button>
      </form>
      <p className="lead" style={{ marginTop: "1rem" }}>
        Already registered? <Link to="/patient/login">Sign in</Link>
      </p>
    </section>
  );
}
