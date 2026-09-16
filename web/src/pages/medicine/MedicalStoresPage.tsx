import { FormEvent, useEffect, useState } from "react";
import { api, type MedicalStore } from "../../api";
import { session } from "../../dl/MedTrackSession";
import { toast } from "../../toast";

export default function MedicalStoresPage() {
  const hospitalId = session.getHospitalId();
  const [stores, setStores] = useState<MedicalStore[]>([]);
  const [error, setError] = useState("");
  const [storeName, setStoreName] = useState("");
  const [phone, setPhone] = useState("");
  const [address, setAddress] = useState("");
  const [password, setPassword] = useState("123456");
  const [saving, setSaving] = useState(false);

  async function load() {
    try {
      const res = await api.medicalStores(hospitalId);
      setStores(res.stores || []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load stores");
    }
  }

  useEffect(() => {
    void load();
  }, [hospitalId]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setSaving(true);
    try {
      const created = await api.registerMedicalStore({
        storeName,
        phone,
        address,
        password,
      });
      toast.success(created.message || `Registered ${created.storeCode}`);
      setStoreName("");
      setPhone("");
      setAddress("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not register store");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="stack">
      <h1>Medical stores</h1>
      <p className="lead">Register hospital pharmacies. They sign in with the store code and process medicine orders.</p>
      {error && <div className="msg error">{error}</div>}

      <form className="panel stack" onSubmit={onSubmit}>
        <h2>Register store</h2>
        <label>
          Store name
          <input value={storeName} onChange={(e) => setStoreName(e.target.value)} required />
        </label>
        <label>
          Phone
          <input value={phone} onChange={(e) => setPhone(e.target.value)} />
        </label>
        <label>
          Address
          <input value={address} onChange={(e) => setAddress(e.target.value)} />
        </label>
        <label>
          Login password
          <input value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        <button type="submit" disabled={saving}>
          {saving ? "Saving…" : "Register medical store"}
        </button>
      </form>

      <div className="table-scroll panel">
        <table className="table">
          <thead>
            <tr>
              <th>Code</th>
              <th>Name</th>
              <th>Phone</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {stores.map((s) => (
              <tr key={s.id}>
                <td>{s.storeCode}</td>
                <td>{s.storeName}</td>
                <td>{s.phone || "—"}</td>
                <td>{s.status}</td>
                <td>
                  <button
                    type="button"
                    className="secondary"
                    onClick={() =>
                      void api
                        .setMedicalStoreStatus(s.id, s.status === "ACTIVE" ? "INACTIVE" : "ACTIVE")
                        .then(() => load())
                    }
                  >
                    {s.status === "ACTIVE" ? "Deactivate" : "Activate"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
