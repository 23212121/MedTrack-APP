import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { Link } from "react-router-dom";
import {
  AllCommunityModule,
  ColDef,
  ICellRendererParams,
  ModuleRegistry,
  themeQuartz,
} from "ag-grid-community";
import { AgGridReact } from "ag-grid-react";
import { api, Appointment } from "../api";
import { useCareChatUnread } from "../careChatUnread";
import CareChatWindow from "../components/CareChatWindow";
import ChatLink from "../components/ChatLink";
import { BodyFileButton } from "../components/NativeFileInput";
import { session } from "../dl/MedTrackSession";
import { toast } from "../toast";

ModuleRegistry.registerModules([AllCommunityModule]);

const queueTheme = themeQuartz.withParams({
  accentColor: "#0e7c86",
  backgroundColor: "#ffffff",
  borderColor: "#c5d4db",
  browserColorScheme: "light",
  chromeBackgroundColor: "#f4f8f9",
  foregroundColor: "#14212b",
  headerBackgroundColor: "#f4f8f9",
  headerFontSize: 13,
  headerFontWeight: 600,
  headerTextColor: "#5a6b76",
  fontFamily: "DM Sans, Segoe UI, sans-serif",
  fontSize: 14,
  oddRowBackgroundColor: "#f8fbfb",
  rowHoverColor: "#e8f1f2",
  spacing: 6,
  wrapperBorderRadius: 10,
});

type DoctorPatient = Appointment & {
  phoneNumber?: string;
  patientPhone?: string;
  appointmentTimeLabel?: string;
  workflowStatus?: string;
  currentComplaint?: string;
  aadhaarNumber?: string;
};

type DayBoard = {
  date: string;
  hospitalId: number;
  doctorId: string;
  doctorName?: string;
  hospitalName?: string;
  specialization?: string;
  patients: DoctorPatient[];
};

type DetailDoc = {
  id: string;
  slot: number;
  patientName?: string;
  aadhaarNumber?: string;
  documentType: string;
  documentName: string;
  uploadDate?: string;
  downloadUrl?: string;
};

type PreviousRecord = {
  id: string;
  appointmentDate?: string;
  status?: string;
  tokenNumber?: number;
  doctorName?: string;
  reason?: string;
};

type QueueRow = DoctorPatient & {
  statusDisplay: string;
  ageDisplay: string | number;
  unreadCount: number;
};

type QueueLayout = "tokens" | "table";

const QUEUE_LAYOUT_KEY = "medtrack.doctorQueueLayout";

function readQueueLayout(): QueueLayout {
  try {
    if (localStorage.getItem(QUEUE_LAYOUT_KEY) === "table") return "table";
  } catch {
    /* ignore */
  }
  return "tokens";
}

function writeQueueLayout(layout: QueueLayout) {
  try {
    localStorage.setItem(QUEUE_LAYOUT_KEY, layout);
  } catch {
    /* ignore */
  }
}

type QueueGridContext = {
  onChat: (patient: DoctorPatient) => void;
  onDetails: (patient: DoctorPatient) => void;
};

function ChatRenderer(params: ICellRendererParams<QueueRow>) {
  const patient = params.data;
  if (!patient) return null;
  const ctx = params.context as QueueGridContext;
  return <ChatLink unread={patient.unreadCount} onClick={() => ctx.onChat(patient)} />;
}

function DetailsRenderer(params: ICellRendererParams<QueueRow>) {
  const patient = params.data;
  if (!patient) return null;
  const ctx = params.context as QueueGridContext;
  return (
    <button type="button" className="link-button" onClick={() => ctx.onDetails(patient)}>
      View details
    </button>
  );
}

function todayIso() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function workflowOf(p: DoctorPatient) {
  const raw = (p.workflowStatus || p.status || "BOOKED").toUpperCase().replace(/_/g, "-");
  if (raw.includes("PROCESS") || raw.includes("CONSULT") || raw === "RUNNING") return "IN-PROCESS";
  if (raw === "COMPLETED" || raw === "COMPLETE") return "COMPLETED";
  return "BOOKED";
}

function statusLabel(status: string) {
  if (status === "IN-PROCESS") return "In-Process";
  if (status === "COMPLETED") return "Completed";
  return "Booked";
}

function tileClass(status: string) {
  if (status === "IN-PROCESS") return "doctor-token-tile doctor-token-tile--process";
  if (status === "COMPLETED") return "doctor-token-tile doctor-token-tile--done";
  return "doctor-token-tile";
}

function takeFiles(list: FileList | File[] | null | undefined): File[] {
  if (!list || list.length === 0) return [];
  return Array.from(list).slice(0, 5);
}

export default function DoctorPatientQueuePage() {
  const isDoctor = session.isDoctor();
  const [date, setDate] = useState(todayIso());
  const [board, setBoard] = useState<DayBoard | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [busyId, setBusyId] = useState("");
  const [selected, setSelected] = useState<DoctorPatient | null>(null);
  const [chatPatient, setChatPatient] = useState<DoctorPatient | null>(null);
  const [documents, setDocuments] = useState<DetailDoc[]>([]);
  const [previous, setPrevious] = useState<PreviousRecord[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [busyDoc, setBusyDoc] = useState("");
  const [aadhaarNumber, setAadhaarNumber] = useState("");
  const [rxFiles, setRxFiles] = useState<File[]>([]);
  const [rxDragOver, setRxDragOver] = useState(false);
  const [uploadingRx, setUploadingRx] = useState(false);
  const [layout, setLayout] = useState<QueueLayout>(readQueueLayout);

  async function loadBoard(day = date) {
    if (!isDoctor) {
      setError("Sign in with a doctor User ID to view this queue.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      const data = await api.doctorTodayPatients(day);
      setBoard(data as DayBoard);
    } catch (e) {
      setBoard(null);
      setError(e instanceof Error ? e.message : "Failed to load patients");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadBoard(date);
  }, [date, isDoctor]);

  useEffect(() => {
    if (!selected) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setSelected(null);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [selected]);

  async function startPatient(patient: DoctorPatient) {
    if (workflowOf(patient) !== "BOOKED") return;
    setBusyId(patient.id);
    setError("");
    try {
      await api.doctorPatientStatus(patient.id, "start");
      toast.success("Patient consult started.");
      await loadBoard();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not update status");
    } finally {
      setBusyId("");
    }
  }

  async function completePatient(patient: DoctorPatient) {
    if (workflowOf(patient) !== "IN-PROCESS") return;
    setBusyId(patient.id);
    setError("");
    try {
      await api.doctorPatientStatus(patient.id, "complete");
      toast.success("Patient marked complete.");
      await loadBoard();
      if (selected?.id === patient.id) {
        setSelected({ ...patient, status: "COMPLETED", workflowStatus: "COMPLETED" });
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not complete patient");
    } finally {
      setBusyId("");
    }
  }

  async function openDetails(patient: DoctorPatient) {
    setSelected(patient);
    setDocuments([]);
    setPrevious([]);
    setRxFiles([]);
    setAadhaarNumber(patient.aadhaarNumber || "");
    setDetailLoading(true);
    setError("");
    try {
      const data = await api.doctorPatientDetail(patient.id);
      const next = { ...(data.patient as DoctorPatient) };
      setSelected(next);
      const docs = (data.documents as DetailDoc[]) || [];
      setDocuments(docs);
      setPrevious(data.previousRecords || []);
      const fromDocs = docs.find((d) => d.aadhaarNumber)?.aadhaarNumber;
      setAadhaarNumber(next.aadhaarNumber || fromDocs || "");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load patient details");
    } finally {
      setDetailLoading(false);
    }
  }

  async function uploadRx() {
    if (!selected) return;
    const aadhaar = aadhaarNumber.replace(/\s+/g, "");
    if (!/^\d{12}$/.test(aadhaar)) {
      setError("Aadhaar number must be 12 digits");
      return;
    }
    if (rxFiles.length === 0) {
      setError("Choose at least one RX / patient file");
      return;
    }
    if (rxFiles.length > 5) {
      setError("Maximum 5 files per upload");
      return;
    }
    setUploadingRx(true);
    setError("");
    try {
      const data = await api.uploadDoctorPatientDocuments(selected.id, {
        aadhaarNumber: aadhaar,
        files: rxFiles,
      });
      const next = { ...(data.patient as DoctorPatient) };
      setSelected(next);
      setDocuments((data.documents as DetailDoc[]) || []);
      setAadhaarNumber(next.aadhaarNumber || aadhaar);
      setRxFiles([]);
      toast.success(
        data.documentCount === 1
          ? "RX uploaded. File link is listed below."
          : `RX uploaded. ${data.documentCount} file links are listed below.`,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "RX upload failed");
    } finally {
      setUploadingRx(false);
    }
  }

  function pickRxFiles(list: FileList | File[] | null | undefined) {
    const files = takeFiles(list);
    if (files.length === 0) return;
    setRxFiles(files);
    setError("");
  }

  async function downloadDoc(doc: DetailDoc) {
    const key = `${doc.id}-${doc.slot}`;
    setBusyDoc(key);
    try {
      await api.downloadPatientDocument(doc.id, doc.slot, doc.documentName);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Download failed");
    } finally {
      setBusyDoc("");
    }
  }

  const patients = board?.patients ?? [];
  const preloaded = useMemo(() => {
    const map: Record<string, string[] | undefined> = {};
    for (const p of patients) map[p.id] = p.otherMessageAts;
    return map;
  }, [patients]);
  const fallbackCounts = useMemo(() => {
    const map: Record<string, number | undefined> = {};
    for (const p of patients) map[p.id] = p.unreadCount ?? p.chatCount;
    return map;
  }, [patients]);
  const unread = useCareChatUnread(
    patients.map((p) => p.id),
    "DOCTOR",
    preloaded,
    fallbackCounts,
  );
  const rowData = useMemo<QueueRow[]>(
    () =>
      patients.map((p) => ({
        ...p,
        statusDisplay: busyId === p.id ? "Updating…" : statusLabel(workflowOf(p)),
        ageDisplay: p.patientAge ?? "—",
        unreadCount: unread[p.id] ?? 0,
      })),
    [patients, busyId, unread],
  );
  const columnDefs = useMemo<ColDef<QueueRow>[]>(
    () => [
      {
        headerName: "Token",
        field: "tokenNumber",
        width: 110,
        filter: "agNumberColumnFilter",
        filterParams: {
          filterOptions: ["equals", "lessThan", "greaterThan"],
          maxNumConditions: 1,
          debounceMs: 200,
        },
        valueGetter: (p) => p.data?.tokenNumber ?? null,
        valueFormatter: (p) => (p.value == null ? "—" : String(p.value)),
      },
      {
        headerName: "Patient name",
        field: "patientName",
        flex: 1,
        minWidth: 160,
        filter: "agTextColumnFilter",
      },
      {
        headerName: "Age",
        field: "ageDisplay",
        width: 100,
        filter: "agNumberColumnFilter",
        filterParams: {
          filterOptions: ["equals", "lessThan", "greaterThan"],
          maxNumConditions: 1,
          debounceMs: 200,
        },
        valueGetter: (p) => p.data?.patientAge ?? null,
        valueFormatter: (p) => (p.value == null ? "—" : String(p.value)),
      },
      {
        headerName: "Status",
        field: "statusDisplay",
        width: 140,
        filter: "agTextColumnFilter",
      },
      {
        headerName: "Chat",
        colId: "chat",
        width: 140,
        sortable: false,
        filter: false,
        cellRenderer: ChatRenderer,
      },
      {
        headerName: "Details",
        colId: "details",
        width: 140,
        sortable: false,
        filter: false,
        cellRenderer: DetailsRenderer,
      },
    ],
    [],
  );
  const defaultColDef = useMemo<ColDef<QueueRow>>(
    () => ({
      sortable: true,
      resizable: true,
      filter: "agTextColumnFilter",
      floatingFilter: false,
      suppressHeaderMenuButton: true,
      suppressHeaderFilterButton: false,
      filterParams: {
        filterOptions: ["contains"],
        debounceMs: 200,
        maxNumConditions: 1,
      },
    }),
    [],
  );
  const gridContext = useMemo<QueueGridContext>(
    () => ({
      onChat: (patient) => setChatPatient(patient),
      onDetails: (patient) => void openDetails(patient),
    }),
    [],
  );

  return (
    <section className="doctor-portal-page">
      <p className="muted" style={{ marginBottom: "0.4rem" }}>
        <Link to="/doctor-portal">← Dashboard</Link>
      </p>

      {error && !selected && <div className="msg error">{error}</div>}

      <div className="panel doctor-queue-toolbar">
        <div className="row doctor-portal-summary">
          <label className="doctor-queue-date">
            <input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              aria-label="Date"
            />
          </label>
          <span className="badge doctor-queue-patients">{patients.length} patients</span>
          <div className="doctor-queue-layout" role="tablist" aria-label="Queue format">
            <button
              type="button"
              role="tab"
              aria-selected={layout === "tokens"}
              className={layout === "tokens" ? "is-active" : undefined}
              onClick={() => {
                setLayout("tokens");
                writeQueueLayout("tokens");
              }}
            >
              Tokens
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={layout === "table"}
              className={layout === "table" ? "is-active" : undefined}
              onClick={() => {
                setLayout("table");
                writeQueueLayout("table");
              }}
            >
              Table
            </button>
          </div>
          <button
            type="button"
            className="secondary doctor-queue-refresh"
            onClick={() => void loadBoard()}
            disabled={loading}
          >
            {loading ? "Loading…" : "Refresh"}
          </button>
        </div>
      </div>

      <div className="panel doctor-queue-board">
        {loading && patients.length === 0 ? (
          <p className="muted">Loading patients…</p>
        ) : null}
        {layout === "table" ? (
        <div className="table-wrap doctor-queue-grid">
          <AgGridReact<QueueRow>
            theme={queueTheme}
            rowData={rowData}
            columnDefs={columnDefs}
            defaultColDef={defaultColDef}
            context={gridContext}
            getRowId={(p) => p.data.id}
            domLayout="autoHeight"
            rowHeight={46}
            headerHeight={44}
            columnMenu="new"
            loading={loading && patients.length > 0}
            overlayNoRowsTemplate="No appointments for you at this hospital on this date."
            pagination={false}
            suppressCellFocus
          />
        </div>
        ) : patients.length > 0 ? (
            <div className="doctor-token-grid">
              {patients.map((p) => {
                const status = workflowOf(p);
                const busy = busyId === p.id;
                return (
                  <article key={p.id} className={tileClass(status)}>
                    <button
                      type="button"
                      className="doctor-token-select"
                      onClick={() => void startPatient(p)}
                      disabled={busy || status !== "BOOKED"}
                      title={
                        status === "BOOKED"
                          ? "Select token to start (In-Process)"
                          : statusLabel(status)
                      }
                    >
                      <span className="doctor-token-num">{p.tokenNumber ?? "—"}</span>
                    </button>
                    <span className="doctor-token-name" title={p.patientName}>
                      {p.patientName}
                    </span>
                    <span className="doctor-token-meta">Age {p.patientAge ?? "—"}</span>
                    <span className={`doctor-token-status is-${status.toLowerCase()}`}>
                      {busy ? "Updating…" : statusLabel(status)}
                    </span>
                    <ChatLink unread={unread[p.id] ?? 0} onClick={() => setChatPatient(p)} />
                    <button
                      type="button"
                      className="link-button doctor-token-details"
                      onClick={() => void openDetails(p)}
                    >
                      View details
                    </button>
                    {status === "IN-PROCESS" && (
                      <button
                        type="button"
                        className="doctor-token-complete"
                        disabled={busy}
                        onClick={() => void completePatient(p)}
                      >
                        Complete
                      </button>
                    )}
                  </article>
                );
              })}
            </div>
        ) : !loading ? (
          <p className="muted">No appointments for you at this hospital on this date.</p>
        ) : null}
      </div>

      {selected &&
        createPortal(
          <div
            className="modal-backdrop"
            role="presentation"
            onMouseDown={(e) => {
              if (e.target === e.currentTarget) setSelected(null);
            }}
          >
            <div
              className="modal panel doctor-portal-modal"
              role="dialog"
              aria-modal="true"
              aria-labelledby="doctor-patient-detail-title"
              onMouseDown={(e) => e.stopPropagation()}
            >
              <header className="modal-header">
                <div>
                  <p className="modal-eyebrow">Token {selected.tokenNumber ?? "—"}</p>
                  <h2 id="doctor-patient-detail-title">{selected.patientName}</h2>
                </div>
                <button
                  type="button"
                  className="modal-close"
                  aria-label="Close"
                  onClick={() => setSelected(null)}
                >
                  ×
                </button>
              </header>

              {error ? <div className="msg error">{error}</div> : null}

              {detailLoading ? (
                <p className="muted">Loading details…</p>
              ) : (
                <>
                  <div className="doctor-portal-modal-upload">
                    <div className="doctor-rx-upload">
                      <label>
                        Aadhaar number
                        <input
                          value={aadhaarNumber}
                          onChange={(e) => setAadhaarNumber(e.target.value)}
                          placeholder="12-digit Aadhaar"
                          inputMode="numeric"
                          maxLength={14}
                        />
                      </label>
                      <div
                        className={`doctor-rx-drop${rxDragOver ? " is-over" : ""}`}
                        onDragOver={(e) => {
                          e.preventDefault();
                          setRxDragOver(true);
                        }}
                        onDragLeave={() => setRxDragOver(false)}
                        onDrop={(e) => {
                          e.preventDefault();
                          setRxDragOver(false);
                          pickRxFiles(e.dataTransfer.files);
                        }}
                      >
                        <span className="native-file-caption">Files (max 5)</span>
                        <BodyFileButton
                          label="Choose file"
                          multiple
                          disabled={uploadingRx}
                          onPick={(files) => pickRxFiles(files)}
                        />
                        {rxFiles.length > 0 ? (
                          <ul className="doctor-rx-pending">
                            {rxFiles.map((f) => (
                              <li key={`${f.name}-${f.size}`}>{f.name}</li>
                            ))}
                          </ul>
                        ) : null}
                      </div>
                      <button type="button" disabled={uploadingRx} onClick={() => void uploadRx()}>
                        {uploadingRx ? "Uploading…" : "Upload RX"}
                      </button>
                    </div>
                  </div>

                  <div className="doctor-portal-modal-body">
                    <div className="doctor-portal-detail-grid">
                      <div>
                        <span className="muted">Patient name</span>
                        <div>{selected.patientName}</div>
                      </div>
                      <div>
                        <span className="muted">Age / Gender</span>
                        <div>
                          {selected.patientAge ?? "—"}
                          {selected.gender ? ` · ${selected.gender}` : ""}
                        </div>
                      </div>
                      <div>
                        <span className="muted">Contact</span>
                        <div>{selected.phoneNumber || selected.patientPhone || "—"}</div>
                      </div>
                      <div>
                        <span className="muted">Aadhaar number</span>
                        <div>{aadhaarNumber || selected.aadhaarNumber || "—"}</div>
                      </div>
                      <div>
                        <span className="muted">Token</span>
                        <div>{selected.tokenNumber ?? "—"}</div>
                      </div>
                      <div>
                        <span className="muted">Appointment</span>
                        <div>
                          {selected.appointmentDate || date}
                          {selected.appointmentTimeLabel || selected.appointmentTime
                            ? ` · ${selected.appointmentTimeLabel || ""}`
                            : ""}
                        </div>
                      </div>
                      <div>
                        <span className="muted">Status</span>
                        <div>{statusLabel(workflowOf(selected))}</div>
                      </div>
                      <div className="doctor-portal-detail-span">
                        <span className="muted">Current complaint</span>
                        <div>{selected.currentComplaint || selected.reason || "—"}</div>
                      </div>
                      <div>
                        <span className="muted">Allergies</span>
                        <div>Not recorded</div>
                      </div>
                      <div>
                        <span className="muted">Medical history</span>
                        <div>
                          {previous.length > 0
                            ? `${previous.length} previous visit${previous.length === 1 ? "" : "s"} at this hospital`
                            : "No previous visits on file"}
                        </div>
                      </div>
                      <div className="doctor-portal-detail-span">
                        <span className="muted">Address</span>
                        <div>{selected.address || "—"}</div>
                      </div>
                    </div>

                    {previous.length > 0 && (
                      <>
                        <h3 className="doctor-portal-docs-title">Previous records</h3>
                        <ul className="doctor-previous-list">
                          {previous.map((r) => (
                            <li key={r.id}>
                              {r.appointmentDate || "—"} · Token {r.tokenNumber ?? "—"} ·{" "}
                              {r.doctorName || "Doctor"} · {statusLabel(r.status || "BOOKED")}
                              {r.reason ? ` — ${r.reason}` : ""}
                            </li>
                          ))}
                        </ul>
                      </>
                    )}

                    <h3 className="doctor-portal-docs-title">Document file links</h3>
                    {documents.length === 0 ? (
                      <p className="muted">No documents uploaded for this patient yet.</p>
                    ) : (
                      <ul className="doctor-rx-links">
                        {documents.map((d) => {
                          const key = `${d.id}-${d.slot}`;
                          return (
                            <li key={key}>
                              <span className="badge">{d.documentType}</span>
                              {d.aadhaarNumber ? (
                                <span className="muted">Aadhaar {d.aadhaarNumber}</span>
                              ) : null}
                              <button
                                type="button"
                                className="link-button"
                                disabled={busyDoc === key}
                                onClick={() => void downloadDoc(d)}
                              >
                                {busyDoc === key ? "Opening…" : d.documentName}
                              </button>
                            </li>
                          );
                        })}
                      </ul>
                    )}
                  </div>
                </>
              )}

              <div className="modal-actions">
                <button type="button" className="secondary" onClick={() => setChatPatient(selected)}>
                  Open chat
                </button>
                {workflowOf(selected) === "IN-PROCESS" && (
                  <button
                    type="button"
                    disabled={busyId === selected.id}
                    onClick={() => void completePatient(selected)}
                  >
                    Mark completed
                  </button>
                )}
                <button type="button" className="secondary" onClick={() => setSelected(null)}>
                  Close
                </button>
              </div>
            </div>
          </div>,
          document.body,
        )}
      {chatPatient && (
        <CareChatWindow
          appointmentId={chatPatient.id}
          title={`${chatPatient.patientName} · Token ${chatPatient.tokenNumber ?? "—"}`}
          senderType={isDoctor ? "DOCTOR" : "HOSPITAL"}
          senderName={session.getUsername() || (isDoctor ? "Doctor" : "Hospital")}
          onClose={() => setChatPatient(null)}
        />
      )}
    </section>
  );
}
