import { FormEvent } from "react";

export function weekdayFromIso(iso: string) {
  if (!iso) return "";
  const d = new Date(iso + "T00:00:00");
  if (Number.isNaN(d.getTime())) return "";
  return d.toLocaleDateString(undefined, { weekday: "long" });
}

type Props = {
  open: boolean;
  saving: boolean;
  date: string;
  reason: string;
  error?: string;
  onDate: (value: string) => void;
  onReason: (value: string) => void;
  onClose: () => void;
  onSubmit: (e: FormEvent) => void;
};

export default function HolidayModal({
  open,
  saving,
  date,
  reason,
  error,
  onDate,
  onReason,
  onClose,
  onSubmit,
}: Props) {
  if (!open) return null;
  const day = weekdayFromIso(date);
  return (
    <div
      className="hrm-wfh-backdrop"
      role="presentation"
      onClick={() => !saving && onClose()}
    >
      <div
        className="hrm-wfh-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="hrm-holiday-title"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="hrm-wfh-head">
          <h2 id="hrm-holiday-title">Add holiday</h2>
          <button
            type="button"
            className="hrm-wfh-close"
            aria-label="Close"
            disabled={saving}
            onClick={onClose}
          >
            ×
          </button>
        </header>
        {error && <p className="error">{error}</p>}
        <form className="hrm-holiday-form" onSubmit={onSubmit}>
          <label>
            Holiday date
            <input
              type="date"
              value={date}
              onChange={(e) => onDate(e.target.value)}
              required
            />
          </label>
          <label>
            Day
            <input value={day || "Select a date"} readOnly />
          </label>
          <label className="hrm-holiday-form-reason">
            Reason
            <input
              value={reason}
              onChange={(e) => onReason(e.target.value)}
              placeholder="e.g. Diwali, Founders Day"
              maxLength={400}
              required
            />
          </label>
          <div className="hrm-holiday-form-actions">
            <button type="button" className="btn" disabled={saving} onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={saving || !date || !reason.trim()}>
              {saving ? "Saving…" : "Save holiday"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
