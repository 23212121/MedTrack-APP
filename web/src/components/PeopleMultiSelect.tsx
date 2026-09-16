import { useEffect, useMemo, useRef, useState } from "react";
import { SchedulePerson } from "../api";

type Props = {
  people: SchedulePerson[];
  selectedIds: string[];
  onToggle: (id: string) => void;
  loading?: boolean;
  label?: string;
};

export default function PeopleMultiSelect({
  people,
  selectedIds,
  onToggle,
  loading = false,
  label = "Doctors and staff",
}: Props) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement | null>(null);
  const selectedPeople = useMemo(
    () => people.filter((p) => selectedIds.includes(p.id)),
    [people, selectedIds],
  );

  useEffect(() => {
    function onDocClick(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    }
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, []);

  const triggerLabel =
    selectedPeople.length === 0
      ? "Select doctors and staff"
      : selectedPeople.length === 1
        ? `${selectedPeople[0].name} (${selectedPeople[0].role})`
        : `${selectedPeople.length} selected`;

  return (
    <div className="schedule-people" ref={menuRef}>
      <label className="schedule-people-label">{label}</label>
      <button
        type="button"
        className={menuOpen ? "schedule-people-trigger is-open" : "schedule-people-trigger"}
        aria-haspopup="listbox"
        aria-expanded={menuOpen}
        onClick={() => setMenuOpen((o) => !o)}
        disabled={loading}
      >
        <span className="schedule-people-trigger-label">
          {loading ? "Loading people…" : triggerLabel}
        </span>
        <svg
          className="schedule-people-caret"
          viewBox="0 0 16 16"
          width="14"
          height="14"
          aria-hidden="true"
        >
          <path
            d="M3.2 5.6 8 10.4l4.8-4.8"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </button>
      {menuOpen && (
        <div className="schedule-people-menu" role="listbox" aria-multiselectable="true">
          {people.length === 0 ? (
            <p className="muted" style={{ margin: 0, padding: "0.6rem 0.75rem" }}>
              No doctors or staff found for this hospital.
            </p>
          ) : (
            people.map((p) => {
              const checked = selectedIds.includes(p.id);
              return (
                <label
                  key={p.id}
                  className={
                    checked ? "schedule-people-option is-selected" : "schedule-people-option"
                  }
                >
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={() => onToggle(p.id)}
                  />
                  <span className="schedule-people-name">{p.name}</span>
                  <span className="schedule-people-meta">
                    {p.role}
                    {p.typeLabel ? ` · ${p.typeLabel}` : ""}
                  </span>
                </label>
              );
            })
          )}
        </div>
      )}
      {selectedPeople.length > 0 && (
        <div className="chip-row schedule-people-chips">
          {selectedPeople.map((p) => (
            <button
              key={p.id}
              type="button"
              className="schedule-people-chip"
              onClick={() => onToggle(p.id)}
            >
              {p.name}
              <span aria-hidden>×</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
