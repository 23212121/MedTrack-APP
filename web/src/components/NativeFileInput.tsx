import { ChangeEvent, RefObject, useRef } from "react";
import { createPortal } from "react-dom";

type NativeFileInputProps = {
  label?: string;
  chosen?: string;
  multiple?: boolean;
  accept?: string;
  disabled?: boolean;
  className?: string;
  ariaLabel?: string;
  onPick: (files: File[]) => void;
};

function filesFrom(e: ChangeEvent<HTMLInputElement>): File[] {
  return e.target.files ? Array.from(e.target.files) : [];
}

function openNativePicker(el: HTMLInputElement | null) {
  if (!el || el.disabled) return;
  try {
    if (typeof el.showPicker === "function") {
      el.showPicker();
      return;
    }
  } catch {
    /* fall through to click() */
  }
  el.click();
}

function PortalFileInput({
  inputRef,
  multiple,
  accept,
  capture,
  disabled,
  ariaLabel,
  onPick,
}: {
  inputRef: RefObject<HTMLInputElement | null>;
  multiple?: boolean;
  accept?: string;
  capture?: "user" | "environment";
  disabled?: boolean;
  ariaLabel: string;
  onPick: (files: File[]) => void;
}) {
  if (typeof document === "undefined") return null;
  return createPortal(
    <input
      ref={inputRef}
      type="file"
      multiple={multiple}
      accept={accept}
      capture={capture}
      disabled={disabled}
      aria-label={ariaLabel}
      className="body-file-input"
      onChange={(e) => {
        const files = filesFrom(e);
        if (files.length > 0) onPick(files);
        e.target.value = "";
      }}
    />,
    document.body,
  );
}

/** Visible native file control. Do not wrap this in a <label>. */
export default function NativeFileInput({
  label,
  chosen,
  multiple,
  accept,
  disabled,
  className,
  ariaLabel,
  onPick,
}: NativeFileInputProps) {
  return (
    <div className={`native-file${className ? ` ${className}` : ""}`}>
      {label ? <span className="native-file-caption">{label}</span> : null}
      <input
        type="file"
        multiple={multiple}
        accept={accept}
        disabled={disabled}
        aria-label={ariaLabel || label || "Choose file"}
        onChange={(e) => {
          const files = filesFrom(e);
          if (files.length > 0) onPick(files);
        }}
      />
      {chosen ? <span className="patient-docs-file-chosen">{chosen}</span> : null}
    </div>
  );
}

type BodyFileButtonProps = {
  label?: string;
  multiple?: boolean;
  accept?: string;
  disabled?: boolean;
  onPick: (files: File[]) => void;
};

/**
 * Branded Choose file button plus the browser's own file control.
 * Both drive the same input so a click cannot open two dialogs.
 */
export function BodyFileButton({
  label = "Choose file",
  multiple,
  accept,
  disabled,
  onPick,
}: BodyFileButtonProps) {
  const inputRef = useRef<HTMLInputElement>(null);

  return (
    <div className="doctor-rx-file-row">
      <button
        type="button"
        className={`doctor-rx-choose${disabled ? " is-disabled" : ""}`}
        disabled={disabled}
        onClick={() => openNativePicker(inputRef.current)}
      >
        {label}
      </button>
      <input
        ref={inputRef}
        type="file"
        multiple={multiple}
        accept={accept}
        disabled={disabled}
        className="doctor-rx-file-input"
        aria-label={label}
        onChange={(e) => {
          const files = filesFrom(e);
          if (files.length > 0) onPick(files);
        }}
      />
    </div>
  );
}

type FileIconInputProps = {
  icon: string;
  ariaLabel: string;
  accept?: string;
  capture?: "user" | "environment";
  disabled?: boolean;
  onPick: (files: File[]) => void;
};

/** Icon button that opens the OS file picker (required by Chrome). */
export function FileIconInput({
  icon,
  ariaLabel,
  accept,
  capture,
  disabled,
  onPick,
}: FileIconInputProps) {
  const inputRef = useRef<HTMLInputElement>(null);

  return (
    <>
      <button
        type="button"
        className={`native-file-icon${disabled ? " is-disabled" : ""}`}
        aria-label={ariaLabel}
        disabled={disabled}
        onClick={() => openNativePicker(inputRef.current)}
      >
        <span aria-hidden="true">{icon}</span>
      </button>
      <PortalFileInput
        inputRef={inputRef}
        accept={accept}
        capture={capture}
        disabled={disabled}
        ariaLabel={ariaLabel}
        onPick={onPick}
      />
    </>
  );
}
