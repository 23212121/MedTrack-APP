import { useEffect, useMemo, useRef, useState } from "react";
import { citiesForState, INDIA_STATES } from "../data/indiaLocations";

type SearchableSelectProps = {
  label?: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
  allowEmpty?: boolean;
  emptyLabel?: string;
  id?: string;
};

export function SearchableSelect({
  label,
  value,
  options,
  onChange,
  placeholder = "Type to search",
  disabled = false,
  required = false,
  allowEmpty = false,
  emptyLabel = "All",
  id,
}: SearchableSelectProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState(value);
  const wrapRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setQuery(value);
  }, [value]);

  useEffect(() => {
    function onDoc(e: MouseEvent) {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setOpen(false);
        setQuery(value);
      }
    }
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, [value]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const list = q ? options.filter((o) => o.toLowerCase().includes(q)) : options;
    return list.slice(0, 80);
  }, [options, query]);

  function pick(next: string) {
    onChange(next);
    setQuery(next);
    setOpen(false);
  }

  const control = (
    <div className={`india-combo${disabled ? " is-disabled" : ""}`} ref={wrapRef}>
      <input
        id={id}
        type="text"
        autoComplete="off"
        disabled={disabled}
        required={required && !allowEmpty}
        placeholder={placeholder}
        value={open ? query : value}
        onFocus={() => {
          if (disabled) return;
          setOpen(true);
          setQuery(value);
        }}
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
        }}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            const first = filtered[0];
            if (allowEmpty && !query.trim()) pick("");
            else if (first) pick(first);
          }
          if (e.key === "Escape") {
            setOpen(false);
            setQuery(value);
          }
        }}
      />
      {open && !disabled ? (
        <ul className="india-combo-menu" role="listbox">
          {allowEmpty ? (
            <li>
              <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={() => pick("")}>
                {emptyLabel}
              </button>
            </li>
          ) : null}
          {filtered.length === 0 ? (
            <li className="india-combo-empty">No match. Keep typing to search.</li>
          ) : (
            filtered.map((opt) => (
              <li key={opt}>
                <button
                  type="button"
                  className={opt === value ? "is-active" : undefined}
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => pick(opt)}
                >
                  {opt}
                </button>
              </li>
            ))
          )}
        </ul>
      ) : null}
    </div>
  );

  if (!label) return control;
  return (
    <label>
      {label}
      {control}
    </label>
  );
}

type LocationProps = {
  state: string;
  city: string;
  onStateChange: (state: string) => void;
  onCityChange: (city: string) => void;
  allowAll?: boolean;
  required?: boolean;
  disabled?: boolean;
};

export function IndiaStateCityFields({
  state,
  city,
  onStateChange,
  onCityChange,
  allowAll = false,
  required = false,
  disabled = false,
}: LocationProps) {
  const cities = useMemo(() => citiesForState(state), [state]);
  return (
    <div className="row">
      <SearchableSelect
        label="State"
        value={state}
        options={INDIA_STATES}
        onChange={(next) => {
          onStateChange(next);
          onCityChange("");
        }}
        placeholder={allowAll ? "All states — type to search" : "Type to search state"}
        allowEmpty={allowAll}
        emptyLabel="All states"
        required={required}
        disabled={disabled}
      />
      <SearchableSelect
        label="City"
        value={city}
        options={cities}
        onChange={onCityChange}
        placeholder={
          !state && !allowAll
            ? "Select a state first"
            : allowAll
              ? "All cities — type to search"
              : "Type to search city"
        }
        allowEmpty={allowAll}
        emptyLabel="All cities"
        required={required && !!state}
        disabled={disabled || (!state && !allowAll)}
      />
    </div>
  );
}
