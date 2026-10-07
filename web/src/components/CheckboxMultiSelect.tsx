import { useEffect, useMemo, useRef, useState } from "react";

type Props = {
  label: string;
  options: string[];
  selected: string[];
  onToggle: (value: string) => void;
  placeholder?: string;
  loading?: boolean;
  emptyText?: string;
};

export default function CheckboxMultiSelect({
  label,
  options,
  selected,
  onToggle,
  placeholder = "Select…",
  loading = false,
  emptyText = "No options",
}: Props) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement | null>(null);
  const selectedSet = useMemo(() => new Set(selected), [selected]);

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
    selected.length === 0
      ? placeholder
      : selected.length === 1
        ? selected[0]
        : `${selected.length} selected`;

  return (
    <div className="schedule-people" ref={menuRef}>
      <label className="schedule-people-label">{label}</label>
      <button
        type="button"
        className={menuOpen ? "schedule-people-trigger is-open" : "schedule-people-trigger"}
        aria-haspopup="listbox"
        aria-expanded={menuOpen}
        aria-label={label}
        onClick={() => setMenuOpen((o) => !o)}
        disabled={loading}
      >
        <span className="schedule-people-trigger-label">
          {loading ? "Loading…" : triggerLabel}
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
          {options.length === 0 ? (
            <p className="muted" style={{ margin: 0, padding: "0.6rem 0.75rem" }}>
              {emptyText}
            </p>
          ) : (
            options.map((name) => {
              const checked = selectedSet.has(name);
              return (
                <label
                  key={name}
                  className={
                    checked ? "schedule-people-option is-selected" : "schedule-people-option"
                  }
                >
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={() => onToggle(name)}
                  />
                  <span className="schedule-people-name">{name}</span>
                </label>
              );
            })
          )}
        </div>
      )}
      {selected.length > 0 && (
        <div className="chip-row schedule-people-chips">
          {selected.map((name) => (
            <button
              key={name}
              type="button"
              className="schedule-people-chip"
              onClick={() => onToggle(name)}
            >
              {name}
              <span aria-hidden>×</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
