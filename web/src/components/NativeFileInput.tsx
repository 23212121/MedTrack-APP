import { ChangeEvent, DragEvent, useId, useRef } from "react";

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

function listFrom(files: FileList | null): File[] {
  return files ? Array.from(files) : [];
}

function onFilesPicked(e: ChangeEvent<HTMLInputElement>, onPick: (files: File[]) => void) {
  const files = listFrom(e.target.files);
  if (files.length > 0) onPick(files);
  e.target.value = "";
}

/** Must stay synchronous so Chrome treats it as a user gesture. */
function openFilePicker(input: HTMLInputElement | null) {
  if (!input || input.disabled) return;
  try {
    if (typeof input.showPicker === "function") {
      input.showPicker();
      return;
    }
  } catch {
    /* use click() below */
  }
  input.click();
}

function onDropFiles(e: DragEvent, disabled: boolean | undefined, onPick: (files: File[]) => void) {
  e.preventDefault();
  e.stopPropagation();
  if (disabled) return;
  const files = listFrom(e.dataTransfer.files);
  if (files.length > 0) onPick(files);
}

export default function NativeFileInput({
  label = "Choose file",
  chosen,
  multiple,
  accept,
  disabled,
  className,
  ariaLabel,
  onPick,
}: NativeFileInputProps) {
  const id = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  return (
    <div
      className={`native-file-drop-zone${disabled ? " is-disabled" : ""}${className ? ` ${className}` : ""}`}
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-label={ariaLabel || label}
      onClick={() => openFilePicker(inputRef.current)}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          openFilePicker(inputRef.current);
        }
      }}
      onDragOver={(e) => {
        e.preventDefault();
        e.dataTransfer.dropEffect = "copy";
      }}
      onDrop={(e) => onDropFiles(e, disabled, onPick)}
    >
      <span className="native-file-btn">{label}</span>
      <span className="native-file-drop-hint">or drop a file here</span>
      <input
        ref={inputRef}
        id={id}
        type="file"
        className="native-file-sr"
        multiple={multiple}
        accept={accept}
        disabled={disabled}
        tabIndex={-1}
        aria-hidden="true"
        onChange={(e) => onFilesPicked(e, onPick)}
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

export function BodyFileButton({
  label = "Choose file",
  multiple,
  accept,
  disabled,
  onPick,
}: BodyFileButtonProps) {
  const id = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  return (
    <div
      className="doctor-rx-file-row"
      onDragOver={(e) => {
        e.preventDefault();
        e.dataTransfer.dropEffect = "copy";
      }}
      onDrop={(e) => onDropFiles(e, disabled, onPick)}
    >
      <button
        type="button"
        className="native-file-btn"
        disabled={disabled}
        onClick={() => openFilePicker(inputRef.current)}
      >
        {label}
      </button>
      <input
        ref={inputRef}
        id={id}
        type="file"
        className="native-file-sr"
        multiple={multiple}
        accept={accept}
        disabled={disabled}
        tabIndex={-1}
        aria-hidden="true"
        onChange={(e) => onFilesPicked(e, onPick)}
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

export function FileIconInput({
  icon,
  ariaLabel,
  accept,
  capture,
  disabled,
  onPick,
}: FileIconInputProps) {
  const id = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  return (
    <span className={`native-file-icon${disabled ? " is-disabled" : ""}`}>
      <button
        type="button"
        className="native-file-icon-hit"
        disabled={disabled}
        aria-label={ariaLabel}
        onClick={() => openFilePicker(inputRef.current)}
      >
        <span aria-hidden="true">{icon}</span>
      </button>
      <input
        ref={inputRef}
        id={id}
        type="file"
        className="native-file-sr"
        accept={accept}
        capture={capture}
        disabled={disabled}
        tabIndex={-1}
        aria-hidden="true"
        onChange={(e) => onFilesPicked(e, onPick)}
      />
    </span>
  );
}
