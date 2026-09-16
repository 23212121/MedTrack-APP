import { FormEvent, useEffect, useState } from "react";
import { api } from "../api";
import { toast } from "../toast";

type Props = {
  open: boolean;
  onClose: () => void;
  /** Called after a successful save into user_details (userId is backend-generated). */
  onRegistered?: (result: {
    userId: string;
    userName: string;
    phone: string;
    password: string;
  }) => void;
};

const emptyForm = { userName: "", phone: "", email: "", password: "" };

export default function PatientUserRegistrationModal({
  open,
  onClose,
  onRegistered,
}: Props) {
  const [form, setForm] = useState(emptyForm);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      setError("");
      setForm(emptyForm);
    }
  }, [open]);

  function onReset() {
    setForm(emptyForm);
    setError("");
  }

  function onCancel() {
    onReset();
    onClose();
  }

  async function onSave(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (!form.userName.trim()) {
      setError("User Name cannot be blank");
      return;
    }
    if (!form.phone.trim()) {
      setError("Phone number cannot be blank");
      return;
    }
    if (!form.password) {
      setError("Password cannot be blank");
      return;
    }
    if (form.password.length < 4) {
      setError("Password must contain a minimum of 4 characters");
      return;
    }
    setSaving(true);
    try {
      const phone = form.phone.trim();
      const password = form.password;
      const res = await api.registerUser({
        userName: form.userName.trim(),
        phone,
        email: form.email.trim() || undefined,
        password,
        createdBy: "SELF",
      });
      toast.success(res.message);
      onRegistered?.({
        userId: res.userId,
        userName: res.userName,
        phone,
        password,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
    } finally {
      setSaving(false);
    }
  }

  if (!open) return null;

  return (
    <div className="modal-backdrop" role="presentation" onClick={onCancel}>
      <div
        className="modal panel patient-reg-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="patient-reg-title"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="modal-header">
          <div>
            <p className="modal-eyebrow">Patient/User Registration</p>
            <h2 id="patient-reg-title">User Registration Form</h2>
          </div>
          <button
            type="button"
            className="modal-close"
            aria-label="Close"
            onClick={onCancel}
          >
            ×
          </button>
        </header>

        {error && <div className="msg error">{error}</div>}

        <form className="stack" onSubmit={onSave}>
          <label>
            User Name
            <input
              value={form.userName}
              onChange={(e) => setForm({ ...form, userName: e.target.value })}
              placeholder="e.g. azherkhan"
              autoComplete="username"
              required
            />
          </label>
          <label>
            Phone Number
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
            Email (for booking confirmation)
            <input
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              placeholder="e.g. azherkhan061@gmail.com"
              autoComplete="email"
            />
          </label>
          <label>
            Password
            <input
              type="password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              minLength={4}
              autoComplete="new-password"
              required
            />
          </label>

          <div className="modal-actions">
            <button type="submit" disabled={saving}>
              {saving ? "Saving…" : "Save"}
            </button>
            <button type="button" onClick={onReset} disabled={saving}>
              Reset
            </button>
            <button type="button" className="btn-secondary" onClick={onCancel}>
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
