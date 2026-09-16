import { FormEvent, useEffect, useState } from "react";
import { api, PatientProfile } from "../../api";
import { getPatientPhone } from "../../auth";
import { toast } from "../../toast";

export default function PatientProfilePage() {
  const phone = getPatientPhone();
  const [profile, setProfile] = useState<PatientProfile | null>(null);
  const [history, setHistory] = useState<
    { id: string; doctorName: string; appointmentDate: string; status: string; tokenNumber?: number }[]
  >([]);
  const [form, setForm] = useState({
    name: "",
    age: "",
    gender: "",
    email: "",
    address: "",
    bloodGroup: "",
    emergencyContact: "",
  });
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!phone) return;
    api
      .patientProfile(phone)
      .then((data) => {
        setProfile(data.profile);
        setHistory(data.appointmentHistory || []);
        setForm({
          name: String(data.profile.name || ""),
          age: data.profile.age ? String(data.profile.age) : "",
          gender: data.profile.gender || "",
          email: data.profile.email || "",
          address: data.profile.address || "",
          bloodGroup: data.profile.bloodGroup || "",
          emergencyContact: data.profile.emergencyContact || "",
        });
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load profile"));
  }, [phone]);

  async function onSave(e: FormEvent) {
    e.preventDefault();
    if (!phone) return;
    setSaving(true);
    setError("");
    try {
      const res = await api.updatePatientProfile(phone, {
        name: form.name,
        age: form.age ? Number(form.age) : null,
        gender: form.gender,
        email: form.email,
        address: form.address,
        bloodGroup: form.bloodGroup,
        emergencyContact: form.emergencyContact,
      });
      setProfile(res.profile);
      toast.success("Profile updated");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Update failed");
    } finally {
      setSaving(false);
    }
  }

  if (!profile && !error) return <p className="lead">Loading profile…</p>;

  return (
    <div className="patient-portal-stack">
      {error && <div className="msg error">{error}</div>}

      <section className="panel">
        <h2>Personal information</h2>
        <form className="stack" onSubmit={onSave}>
          <label>
            Full name
            <input
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
            />
          </label>
          <div className="grid-2">
            <label>
              Phone
              <input value={profile?.phone || phone} disabled />
            </label>
            <label>
              Age
              <input
                type="number"
                value={form.age}
                onChange={(e) => setForm({ ...form, age: e.target.value })}
              />
            </label>
          </div>
          <div className="grid-2">
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
            <label>
              Blood group
              <input
                value={form.bloodGroup}
                onChange={(e) => setForm({ ...form, bloodGroup: e.target.value })}
              />
            </label>
          </div>
          <label>
            Email
            <input
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
            />
          </label>
          <label>
            Address
            <input
              value={form.address}
              onChange={(e) => setForm({ ...form, address: e.target.value })}
            />
          </label>
          <label>
            Emergency contact
            <input
              value={form.emergencyContact}
              onChange={(e) => setForm({ ...form, emergencyContact: e.target.value })}
            />
          </label>
          <button type="submit" disabled={saving}>
            {saving ? "Saving…" : "Save profile"}
          </button>
        </form>
      </section>

      <section className="panel">
        <h2>Previous appointments</h2>
        {history.length === 0 ? (
          <p className="lead">No appointment history yet.</p>
        ) : (
          <ul className="patient-appointment-list">
            {history.map((h) => (
              <li key={h.id} className="patient-appointment-item">
                <strong>{h.doctorName}</strong>
                <p className="lead">
                  {h.appointmentDate} · Token # {h.tokenNumber ?? "—"} · {h.status}
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
