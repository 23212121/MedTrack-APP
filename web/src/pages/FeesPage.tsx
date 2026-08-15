import { FormEvent, useEffect, useState } from "react";
import { api, DOCTOR_ID, FeeRule } from "../api";

export default function FeesPage() {
  const [rule, setRule] = useState<FeeRule | null>(null);
  const [minutes, setMinutes] = useState(40);
  const [preview, setPreview] = useState<Record<string, number | string> | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function load() {
    setError("");
    try {
      setRule(await api.fees(DOCTOR_ID));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load fees");
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function onSave(e: FormEvent) {
    e.preventDefault();
    if (!rule) return;
    setMessage("");
    setError("");
    try {
      const saved = await api.saveFees(DOCTOR_ID, rule);
      setRule(saved);
      setMessage("Fee rules saved. Overtime applies after fixed consult minutes.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    }
  }

  async function onPreview() {
    setError("");
    try {
      setPreview(await api.calcFees(DOCTOR_ID, minutes));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Calculate failed");
    }
  }

  if (!rule && !error) return <p>Loading fee rules…</p>;

  return (
    <section>
      <h1>Extra charge after fixed time</h1>
      <p className="lead">
        Base consult fee covers a fixed duration. Extra blocks after that add overtime charges
        and trigger SMS/email when the visit completes.
      </p>
      {error && <div className="msg error">{error}</div>}
      {message && <div className="msg ok">{message}</div>}
      <div className="grid-2">
        {rule && (
          <form className="panel stack" onSubmit={onSave}>
            <h2>Fee rule — Dr. Mehta</h2>
            <label>
              Base consult fee
              <input
                type="number"
                value={rule.baseConsultFee}
                onChange={(e) => setRule({ ...rule, baseConsultFee: Number(e.target.value) })}
              />
            </label>
            <label>
              Fixed consult minutes
              <input
                type="number"
                value={rule.fixedConsultMinutes}
                onChange={(e) => setRule({ ...rule, fixedConsultMinutes: Number(e.target.value) })}
              />
            </label>
            <label>
              Overtime fee per block
              <input
                type="number"
                value={rule.overtimeFeeAmount}
                onChange={(e) => setRule({ ...rule, overtimeFeeAmount: Number(e.target.value) })}
              />
            </label>
            <label>
              Block size (minutes)
              <input
                type="number"
                value={rule.overtimeFeePerBlockMinutes}
                onChange={(e) =>
                  setRule({ ...rule, overtimeFeePerBlockMinutes: Number(e.target.value) })
                }
              />
            </label>
            <label>
              Currency
              <input
                value={rule.currency}
                onChange={(e) => setRule({ ...rule, currency: e.target.value })}
              />
            </label>
            <button type="submit">Save fee rules</button>
          </form>
        )}
        <div className="panel stack">
          <h2>Preview overtime</h2>
          <label>
            Actual consult minutes
            <input
              type="number"
              min={1}
              value={minutes}
              onChange={(e) => setMinutes(Number(e.target.value))}
            />
          </label>
          <button type="button" onClick={onPreview}>
            Calculate
          </button>
          {preview && (
            <div className="stats">
              <div className="stat">
                <strong>
                  {preview.currency} {preview.baseFee}
                </strong>
                <span>Base</span>
              </div>
              <div className="stat">
                <strong>{preview.overtimeMinutes}m</strong>
                <span>Overtime</span>
              </div>
              <div className="stat">
                <strong>
                  {preview.currency} {preview.overtimeFee}
                </strong>
                <span>Extra charge</span>
              </div>
              <div className="stat">
                <strong>
                  {preview.currency} {preview.totalFee}
                </strong>
                <span>Total</span>
              </div>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
