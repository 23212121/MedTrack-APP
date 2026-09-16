import { FormEvent, useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  HrmHoliday,
  createHrmHoliday,
  deleteHrmHoliday,
  getHrmHolidays,
  getHrmRights,
} from "../../api";
import { toast } from "../../toast";
import HolidayModal from "./HolidayModal";

export default function HrmHolidayPage() {
  const [params, setParams] = useSearchParams();
  const [rows, setRows] = useState<HrmHoliday[]>([]);
  const [canManage, setCanManage] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [open, setOpen] = useState(false);
  const [date, setDate] = useState("");
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [busyId, setBusyId] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [list, rights] = await Promise.all([getHrmHolidays(), getHrmRights()]);
      setRows(Array.isArray(list) ? list : []);
      setCanManage(!!rights.canManageHolidays);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load holidays");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (params.get("add") === "1") {
      setOpen(true);
      const next = new URLSearchParams(params);
      next.delete("add");
      setParams(next, { replace: true });
    }
  }, [params, setParams]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!date || !reason.trim()) {
      setFormError("Date and reason are required");
      return;
    }
    setSaving(true);
    setFormError("");
    try {
      await createHrmHoliday({ date, reason: reason.trim() });
      toast.success("Holiday saved for the organization.");
      setOpen(false);
      setDate("");
      setReason("");
      await load();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Could not save holiday");
    } finally {
      setSaving(false);
    }
  }

  async function onDelete(id: string) {
    setBusyId(id);
    try {
      await deleteHrmHoliday(id);
      toast.success("Holiday removed.");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not remove holiday");
    } finally {
      setBusyId("");
    }
  }

  return (
    <div className="hrm-holiday-page">
      <header className="hrm-holiday-head">
        <div>
          <h2>Holidays</h2>
        </div>
        {canManage && (
          <button type="button" className="btn primary" onClick={() => setOpen(true)}>
            Add holiday
          </button>
        )}
      </header>

      {error && <p className="error">{error}</p>}
      {loading && !rows.length && <p className="lead">Loading holidays…</p>}

      <section className="panel">
        {rows.length === 0 && !loading ? (
          <p className="muted">No holidays yet.</p>
        ) : (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Day</th>
                  <th>Reason</th>
                  {canManage && <th></th>}
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id}>
                    <td>{row.date}</td>
                    <td>{row.day}</td>
                    <td>{row.reason || row.name}</td>
                    {canManage && (
                      <td>
                        <button
                          type="button"
                          className="btn"
                          disabled={busyId === row.id}
                          onClick={() => void onDelete(row.id)}
                        >
                          Remove
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {canManage && (
        <HolidayModal
          open={open}
          saving={saving}
          date={date}
          reason={reason}
          error={formError}
          onDate={setDate}
          onReason={setReason}
          onClose={() => !saving && setOpen(false)}
          onSubmit={onSubmit}
        />
      )}
    </div>
  );
}
