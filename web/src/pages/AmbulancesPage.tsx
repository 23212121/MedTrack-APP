import { FormEvent, useEffect, useState } from "react";
import { api, type Ambulance } from "../api";
import { session } from "../dl/MedTrackSession";
import { toast } from "../toast";

export default function AmbulancesPage() {
  const hospitalId = session.getHospitalId();
  const [rows, setRows] = useState<Ambulance[]>([]);
  const [driverName, setDriverName] = useState("");
  const [mobile, setMobile] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [ratePerKm, setRatePerKm] = useState("");
  const [savingRate, setSavingRate] = useState(false);

  async function load() {
    try {
      const res = await api.ambulances(hospitalId || undefined);
      setRows(res.ambulances || []);
      const pricing = await api.ambulancePricing(hospitalId || undefined);
      if (pricing.ratePerKm != null) setRatePerKm(String(pricing.ratePerKm));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load ambulances");
    }
  }

  useEffect(() => {
    void load();
  }, [hospitalId]);

  async function saveRate(e: FormEvent) {
    e.preventDefault();
    const rate = Number(ratePerKm);
    if (!Number.isFinite(rate) || rate < 1) {
      setError("Enter a valid rate per km (minimum ₹1).");
      return;
    }
    setSavingRate(true);
    setError("");
    try {
      const res = await api.saveAmbulancePricing({
        ratePerKm: rate,
        hospitalId: hospitalId || undefined,
      });
      toast.success(res.message || "Ambulance rate saved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save rate");
    } finally {
      setSavingRate(false);
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError("");
    try {
      const res = await api.registerAmbulance({ driverName, mobileNumber: mobile });
      toast.success(res.message || "Ambulance saved");
      setDriverName("");
      setMobile("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save ambulance");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="stack">
      <h1>Ambulances</h1>
      <p className="lead">
        Register the driver mobile number. The Flutter driver app sends live GPS and an FCM token
        for that number. Patients are matched to the nearest available ambulance.
      </p>
      {error ? <div className="msg error">{error}</div> : null}
      <form className="panel stack" onSubmit={(e) => void saveRate(e)}>
        <h2>Ambulance fare</h2>
        <p className="muted">
          Patient portal fare = distance (km) × this rate. Distance is from the ambulance GPS to the
          patient location when the request is sent.
        </p>
        <label>
          Rate per kilometer (INR)
          <input
            value={ratePerKm}
            onChange={(e) => setRatePerKm(e.target.value)}
            inputMode="decimal"
            required
          />
        </label>
        <button type="submit" disabled={savingRate}>
          {savingRate ? "Saving…" : "Save rate"}
        </button>
      </form>
      <form className="panel stack" onSubmit={(e) => void onSubmit(e)}>
        <h2>Register ambulance</h2>
        <label>
          Driver name
          <input value={driverName} onChange={(e) => setDriverName(e.target.value)} required />
        </label>
        <label>
          Mobile number
          <input value={mobile} onChange={(e) => setMobile(e.target.value)} required inputMode="numeric" />
        </label>
        <button type="submit" disabled={saving}>
          {saving ? "Saving…" : "Save ambulance"}
        </button>
      </form>
      <div className="table-scroll panel">
        <table className="table">
          <thead>
            <tr>
              <th>Driver</th>
              <th>Mobile</th>
              <th>Status</th>
              <th>Last GPS</th>
              <th>Push token</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={5}>No ambulances registered.</td>
              </tr>
            ) : (
              rows.map((a) => (
                <tr key={a.id}>
                  <td>{a.driverName}</td>
                  <td>{a.mobileNumber}</td>
                  <td>{a.availabilityStatus}</td>
                  <td>
                    {a.latitude != null && a.longitude != null
                      ? `${a.latitude.toFixed(5)}, ${a.longitude.toFixed(5)}`
                      : "Waiting for driver GPS"}
                  </td>
                  <td>{a.fcmRegistered ? "Registered" : "Not yet"}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
