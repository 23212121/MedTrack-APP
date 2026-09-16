import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  api,
  fetchMedicineDocumentBlob,
  type MedicineOrder,
  type MedicineOrderItemInput,
  type HospitalRegistrationSummary,
} from "../../api";
import NativeFileInput from "../../components/NativeFileInput";
import { session } from "../../dl/MedTrackSession";
import { toast } from "../../toast";

const STATUS_LABEL: Record<string, string> = {
  ORDERED: "Ordered",
  PENDING: "Pending",
  IN_PROCESS: "In process",
  WAITING_FOR_PATIENT_APPROVAL: "Waiting approval",
  AMOUNT_ACCEPTED: "Amount accepted",
  MEDICINE_READY: "Medicine ready",
  COMPLETED: "Completed",
  CANCELED: "Canceled",
};

const OPEN = new Set(["ORDERED", "PENDING"]);

function hideAwsDump(err: unknown, fallback: string) {
  const raw = err instanceof Error ? err.message : "";
  if (/s3|amazon|iam|permissions boundary|PutObject|accesspoint|medtrackdoc/i.test(raw)) {
    return fallback;
  }
  return raw.trim() || fallback;
}

function money(n?: number | null) {
  if (n == null || Number.isNaN(n)) return "Not calculated";
  return `₹${n.toFixed(2)}`;
}

function FilePicker({
  files,
  onPick,
}: {
  files: File[];
  onPick: (picked: File[]) => void;
}) {
  return (
    <NativeFileInput
      label="Choose file"
      multiple
      accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
      chosen={files.length > 0 ? files.map((f) => f.name).join(", ") : undefined}
      onPick={(picked) => {
        if (picked.length === 0) return;
        onPick(picked.slice(0, 8));
      }}
    />
  );
}

type QuoteItem = MedicineOrderItemInput;

function emptyItem(): QuoteItem {
  return {
    prescribedName: "",
    medicineName: "",
    quantity: 1,
    unitPrice: 0,
    availability: "AVAILABLE",
    substituteName: "",
    substituteReason: "",
  };
}

export default function MedicineOrdersPage({
  defaultStatus,
  title,
}: {
  defaultStatus?: string;
  title?: string;
}) {
  const role = session.getLoginType();
  const isMedical = session.isMedical();
  const isPatient = session.isPatient();
  const isHospital = role === "HOSPITAL";
  const canCreate = isPatient || session.isDoctor() || isHospital;

  const [orders, setOrders] = useState<MedicineOrder[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [q, setQ] = useState("");
  const [status, setStatus] = useState(defaultStatus || "");
  const [fulfillmentFilter, setFulfillmentFilter] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");

  const [hospitals, setHospitals] = useState<HospitalRegistrationSummary[]>([]);
  const [patientName, setPatientName] = useState(session.getPatientName() || "");
  const [patientPhone, setPatientPhone] = useState(session.getPatientPhone() || "");
  const [hospitalId, setHospitalId] = useState(session.getHospitalId() || "");
  const [fulfillment, setFulfillment] = useState("PICKUP");
  const [deliveryAddress, setDeliveryAddress] = useState("");
  const [notes, setNotes] = useState("");
  const [files, setFiles] = useState<File[]>([]);
  const [saving, setSaving] = useState(false);

  const [detail, setDetail] = useState<MedicineOrder | null>(null);
  const [amountOpen, setAmountOpen] = useState(false);
  const [filesOpen, setFilesOpen] = useState(false);
  const [previewUrl, setPreviewUrl] = useState("");
  const [previewName, setPreviewName] = useState("");
  const [quoteItems, setQuoteItems] = useState<QuoteItem[]>([emptyItem()]);
  const [charges, setCharges] = useState({
    deliveryCharge: 0,
    packagingCharge: 0,
    tax: 0,
    otherCharges: 0,
    discount: 0,
  });
  const [reviseReason, setReviseReason] = useState("");
  const [extraFiles, setExtraFiles] = useState<File[]>([]);

  useEffect(() => {
    setStatus(defaultStatus || "");
  }, [defaultStatus]);

  async function load() {
    setLoading(true);
    setError("");
    try {
      const res = await api.medicineOrders({
        status: status || undefined,
        q: q || undefined,
        fulfillment: fulfillmentFilter || undefined,
        from: from || undefined,
        to: to || undefined,
      });
      setOrders(res.orders || []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load orders");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [status, defaultStatus]);

  useEffect(() => {
    if (isPatient) {
      void api.hospitals().then((r) => setHospitals(r.hospitals || [])).catch(() => undefined);
    }
  }, [isPatient]);

  const subtotal = useMemo(
    () => quoteItems.reduce((s, it) => s + Number(it.quantity || 0) * Number(it.unitPrice || 0), 0),
    [quoteItems],
  );
  const grand = useMemo(() => {
    const g =
      subtotal +
      Number(charges.deliveryCharge || 0) +
      Number(charges.packagingCharge || 0) +
      Number(charges.tax || 0) +
      Number(charges.otherCharges || 0) -
      Number(charges.discount || 0);
    return Math.max(0, g);
  }, [subtotal, charges]);

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (!patientName.trim() || !patientPhone.trim()) {
      setError("Patient name and phone are required");
      return;
    }
    const resolvedHospitalId = hospitalId || session.getHospitalId();
    if (!resolvedHospitalId) {
      setError(isPatient ? "Select a hospital" : "Hospital is not set for this session");
      return;
    }
    if (files.length === 0) {
      setError("Upload at least one prescription (PDF, JPG, PNG)");
      return;
    }
    setSaving(true);
    try {
      const form = new FormData();
      form.set("hospitalId", resolvedHospitalId);
      form.set("patientName", patientName.trim());
      form.set("patientPhone", patientPhone.trim());
      form.set("fulfillment", fulfillment);
      if (deliveryAddress) form.set("deliveryAddress", deliveryAddress);
      if (notes) form.set("notes", notes);
      if (session.getPatientId()) form.set("patientId", session.getPatientId());
      if (session.isDoctor()) {
        form.set("doctorId", session.getUserId());
        form.set("doctorName", session.getUsername());
      }
      files.forEach((f) => form.append("files", f));
      const created = await api.createMedicineOrder(form);
      toast.success(`Order ${created.orderNumber} created`);
      setFiles([]);
      setNotes("");
      await load();
    } catch (err) {
      setError(hideAwsDump(err, "Could not create order"));
    } finally {
      setSaving(false);
    }
  }

  async function openAmount(order: MedicineOrder) {
    setError("");
    try {
      const full = await api.medicineOrder(order.id);
      setDetail(full);
      setQuoteItems(
        full.items && full.items.length
          ? full.items.map((it) => ({
              prescribedName: it.prescribedName || it.medicineName,
              medicineName: it.medicineName,
              quantity: it.quantity,
              unitPrice: it.unitPrice ?? 0,
              availability: it.availability || "AVAILABLE",
              substituteName: it.substituteName || "",
              substituteReason: it.substituteReason || "",
            }))
          : [emptyItem()],
      );
      setCharges({
        deliveryCharge: full.charges?.deliveryCharge ?? 0,
        packagingCharge: full.charges?.packagingCharge ?? 0,
        tax: full.charges?.tax ?? 0,
        otherCharges: full.charges?.otherCharges ?? 0,
        discount: full.charges?.discount ?? 0,
      });
      setAmountOpen(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load amount");
    }
  }

  async function openFiles(order: MedicineOrder) {
    try {
      const full = await api.medicineOrder(order.id);
      setDetail(full);
      setFilesOpen(true);
      setPreviewUrl("");
      setPreviewName("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load files");
    }
  }

  async function previewDoc(orderId: string, docId: string, name: string, type?: string) {
    try {
      const blob = await fetchMedicineDocumentBlob(orderId, docId);
      const typed = type ? new Blob([blob], { type }) : blob;
      if (previewUrl) URL.revokeObjectURL(previewUrl);
      setPreviewUrl(URL.createObjectURL(typed));
      setPreviewName(name);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not preview file");
    }
  }

  async function run(action: () => Promise<unknown>, ok: string) {
    setError("");
    try {
      await action();
      toast.success(ok);
      setAmountOpen(false);
      await load();
    } catch (err) {
      setError(hideAwsDump(err, "Action failed"));
    }
  }

  const pageTitle = title || "Medicine orders";

  return (
    <section className="stack medicine-orders-page">
      <h1>{pageTitle}</h1>
      <p className="lead">
        {isMedical
          ? "Accept new prescriptions, send a quote, and complete pickup or delivery."
          : "Upload a doctor prescription and track the medical store quote."}
      </p>
      {error && <div className="msg error">{error}</div>}

      {canCreate && (
        <form className="panel stack" onSubmit={onCreate}>
          <h2>Create medicine order</h2>
          <div className="row">
            <label>
              Patient name
              <input value={patientName} onChange={(e) => setPatientName(e.target.value)} required />
            </label>
            <label>
              Phone
              <input value={patientPhone} onChange={(e) => setPatientPhone(e.target.value)} required />
            </label>
          </div>
          {isPatient && (
            <label>
              Hospital
              <select value={hospitalId} onChange={(e) => setHospitalId(e.target.value)} required>
                <option value="">Select hospital</option>
                {hospitals.map((h) => (
                  <option key={h.id} value={String(h.id)}>
                    {h.hospitalName} ({h.id})
                  </option>
                ))}
              </select>
            </label>
          )}
          <div className="row">
            <label>
              Fulfillment
              <select value={fulfillment} onChange={(e) => setFulfillment(e.target.value)}>
                <option value="PICKUP">Pickup</option>
                <option value="DELIVERY">Delivery</option>
              </select>
            </label>
            {fulfillment === "DELIVERY" && (
              <label>
                Delivery address
                <input value={deliveryAddress} onChange={(e) => setDeliveryAddress(e.target.value)} required />
              </label>
            )}
          </div>
          <label>
            Notes
            <input value={notes} onChange={(e) => setNotes(e.target.value)} />
          </label>
          <div>
            <span className="schedule-people-label">Prescription (PDF, JPG, PNG)</span>
            <FilePicker files={files} onPick={setFiles} />
          </div>
          <button type="submit" disabled={saving}>
            {saving ? "Submitting…" : "Create order"}
          </button>
        </form>
      )}

      <form
        className="panel row"
        onSubmit={(e) => {
          e.preventDefault();
          void load();
        }}
      >
        <label>
          Search
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Order no, patient, phone" />
        </label>
        {!defaultStatus && (
          <label>
            Status
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All</option>
              {Object.entries(STATUS_LABEL).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
        )}
        <label>
          Type
          <select value={fulfillmentFilter} onChange={(e) => setFulfillmentFilter(e.target.value)}>
            <option value="">All</option>
            <option value="PICKUP">Pickup</option>
            <option value="DELIVERY">Delivery</option>
          </select>
        </label>
        <label>
          From
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label>
          To
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
        <button type="submit" className="secondary">
          Filter
        </button>
      </form>

      <div className="table-scroll panel">
        {loading ? (
          <p className="muted">Loading…</p>
        ) : orders.length === 0 ? (
          <p className="muted">No medicine orders.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Order</th>
                <th>Patient</th>
                <th>Prescription</th>
                <th>Date</th>
                <th>Store</th>
                <th>Amount</th>
                <th>Amount status</th>
                <th>Order status</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.id}>
                  <td>
                    <strong>{o.orderNumber}</strong>
                  </td>
                  <td>
                    {o.patientName}
                    <div className="muted">{o.patientPhone}</div>
                  </td>
                  <td>
                    <button type="button" className="link-button" onClick={() => void openFiles(o)}>
                      View files ({o.prescriptionCount ?? o.documents?.length ?? 0})
                    </button>
                  </td>
                  <td>{o.createdAt ? new Date(o.createdAt).toLocaleString() : "—"}</td>
                  <td>{o.assignedStoreName || "—"}</td>
                  <td>
                    {o.amountVisible === false && !isMedical ? (
                      "Not calculated"
                    ) : (
                      <button type="button" className="link-button" onClick={() => void openAmount(o)}>
                        {isPatient ? `Total ${money(o.amount)}` : money(o.amount)}
                      </button>
                    )}
                  </td>
                  <td>{o.amountStatus || "NOT_CALCULATED"}</td>
                  <td>
                    <span className={`badge ${o.status}`}>{STATUS_LABEL[o.status] || o.status}</span>
                  </td>
                  <td className="medicine-actions">
                    {isMedical && OPEN.has(o.status) && !o.assignedStoreId && (
                      <>
                        <button type="button" onClick={() => void run(() => api.acceptMedicineOrder(o.id), "Order accepted")}>
                          Accept
                        </button>
                        <button
                          type="button"
                          className="secondary"
                          onClick={() => {
                            const reason = window.prompt("Reject reason", "Medicine not available") || "";
                            if (reason) void run(() => api.rejectMedicineOrder(o.id, reason), "Rejected for this store");
                          }}
                        >
                          Reject
                        </button>
                      </>
                    )}
                    {isMedical && o.status === "IN_PROCESS" && (
                      <button type="button" onClick={() => void openAmount(o)}>
                        Quote
                      </button>
                    )}
                    {isMedical && o.status === "AMOUNT_ACCEPTED" && (
                      <button type="button" onClick={() => void run(() => api.markMedicineReady(o.id), "Marked ready")}>
                        Ready
                      </button>
                    )}
                    {isMedical && o.status === "MEDICINE_READY" && (
                      <button type="button" onClick={() => void run(() => api.completeMedicineOrder(o.id), "Completed")}>
                        Complete
                      </button>
                    )}
                    {isPatient && o.status === "WAITING_FOR_PATIENT_APPROVAL" && (
                      <button type="button" onClick={() => void openAmount(o)}>
                        Review amount
                      </button>
                    )}
                    {canCreate && OPEN.has(o.status) && (
                      <button
                        type="button"
                        className="danger"
                        onClick={() => {
                          const reason = window.prompt("Cancel reason", "") || "";
                          if (reason) void run(() => api.cancelMedicineOrder(o.id, reason), "Canceled");
                        }}
                      >
                        Cancel
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {amountOpen && detail && (
        <div className="modal-backdrop" onClick={() => setAmountOpen(false)}>
          <div className="modal panel medicine-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Amount · {detail.orderNumber}</h2>
              <button type="button" className="modal-close" onClick={() => setAmountOpen(false)}>
                ×
              </button>
            </div>
            <p className="muted">
              {detail.assignedStoreName || "Store not assigned"} · {detail.patientName}
            </p>

            {isMedical && (detail.status === "IN_PROCESS" || detail.status === "AMOUNT_ACCEPTED" || detail.status === "WAITING_FOR_PATIENT_APPROVAL") ? (
              <div className="stack">
                {quoteItems.map((it, idx) => (
                  <div key={idx} className="medicine-quote-row">
                    <input
                      placeholder="Prescribed"
                      value={it.prescribedName}
                      onChange={(e) =>
                        setQuoteItems((rows) => rows.map((r, i) => (i === idx ? { ...r, prescribedName: e.target.value } : r)))
                      }
                    />
                    <input
                      placeholder="Medicine"
                      value={it.medicineName}
                      onChange={(e) =>
                        setQuoteItems((rows) => rows.map((r, i) => (i === idx ? { ...r, medicineName: e.target.value } : r)))
                      }
                    />
                    <input
                      type="number"
                      min={0}
                      step="0.01"
                      value={it.quantity}
                      onChange={(e) =>
                        setQuoteItems((rows) =>
                          rows.map((r, i) => (i === idx ? { ...r, quantity: Number(e.target.value) } : r)),
                        )
                      }
                    />
                    <input
                      type="number"
                      min={0}
                      step="0.01"
                      value={it.unitPrice}
                      onChange={(e) =>
                        setQuoteItems((rows) =>
                          rows.map((r, i) => (i === idx ? { ...r, unitPrice: Number(e.target.value) } : r)),
                        )
                      }
                    />
                    <select
                      value={it.availability}
                      onChange={(e) =>
                        setQuoteItems((rows) => rows.map((r, i) => (i === idx ? { ...r, availability: e.target.value } : r)))
                      }
                    >
                      <option value="AVAILABLE">Available</option>
                      <option value="PARTIALLY_AVAILABLE">Partial</option>
                      <option value="NOT_AVAILABLE">Not available</option>
                      <option value="SUBSTITUTE_AVAILABLE">Substitute</option>
                    </select>
                    <span>{money(Number(it.quantity || 0) * Number(it.unitPrice || 0))}</span>
                  </div>
                ))}
                <button type="button" className="secondary" onClick={() => setQuoteItems((rows) => [...rows, emptyItem()])}>
                  Add medicine
                </button>
                <div className="row">
                  <label>
                    Delivery
                    <input
                      type="number"
                      value={charges.deliveryCharge}
                      onChange={(e) => setCharges((c) => ({ ...c, deliveryCharge: Number(e.target.value) }))}
                    />
                  </label>
                  <label>
                    Packaging
                    <input
                      type="number"
                      value={charges.packagingCharge}
                      onChange={(e) => setCharges((c) => ({ ...c, packagingCharge: Number(e.target.value) }))}
                    />
                  </label>
                  <label>
                    Tax
                    <input
                      type="number"
                      value={charges.tax}
                      onChange={(e) => setCharges((c) => ({ ...c, tax: Number(e.target.value) }))}
                    />
                  </label>
                  <label>
                    Other
                    <input
                      type="number"
                      value={charges.otherCharges}
                      onChange={(e) => setCharges((c) => ({ ...c, otherCharges: Number(e.target.value) }))}
                    />
                  </label>
                  <label>
                    Discount
                    <input
                      type="number"
                      value={charges.discount}
                      onChange={(e) => setCharges((c) => ({ ...c, discount: Number(e.target.value) }))}
                    />
                  </label>
                </div>
                <p>
                  Subtotal {money(subtotal)} · <strong>Grand total {money(grand)}</strong>
                </p>
                {(detail.status === "AMOUNT_ACCEPTED" || detail.status === "WAITING_FOR_PATIENT_APPROVAL") && (
                  <label>
                    Revision reason
                    <input value={reviseReason} onChange={(e) => setReviseReason(e.target.value)} />
                  </label>
                )}
                <div className="modal-actions">
                  {detail.status === "IN_PROCESS" && (
                    <button
                      type="button"
                      className="secondary"
                      onClick={() =>
                        void run(
                          () => api.saveMedicineQuote(detail.id, { items: quoteItems, charges }, false),
                          "Draft saved",
                        )
                      }
                    >
                      Save draft
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() =>
                      void run(
                        () =>
                          api.saveMedicineQuote(
                            detail.id,
                            { items: quoteItems, charges, reason: reviseReason || undefined },
                            true,
                          ),
                        "Amount sent to patient",
                      )
                    }
                  >
                    Send amount to patient
                  </button>
                  {detail.status === "IN_PROCESS" && (
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => {
                        const note = window.prompt("Tell the patient what is unclear", "Please upload a clearer RX") || "";
                        if (note) void run(() => api.markPrescriptionUnclear(detail.id, note), "Patient notified");
                      }}
                    >
                      Prescription unclear
                    </button>
                  )}
                  <button
                    type="button"
                    className="danger"
                    onClick={() => {
                      const reason = window.prompt("Release reason", "Stock issue") || "";
                      if (reason) void run(() => api.releaseMedicineOrder(detail.id, reason), "Order released");
                    }}
                  >
                    Release order
                  </button>
                </div>
              </div>
            ) : (
              <div className="stack">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Medicine</th>
                      <th>Qty</th>
                      <th>Unit</th>
                      <th>Total</th>
                      <th>Availability</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(detail.items || []).map((it, i) => (
                      <tr key={it.id || i}>
                        <td>
                          {it.medicineName}
                          {it.substituteName ? (
                            <div className="muted">
                              Substitute for {it.prescribedName}: {it.substituteName}
                            </div>
                          ) : null}
                        </td>
                        <td>{it.quantity}</td>
                        <td>{money(it.unitPrice)}</td>
                        <td>{money(it.lineTotal)}</td>
                        <td>{it.availability}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <p>
                  Subtotal {money(detail.charges?.medicineSubtotal)} · Delivery {money(detail.charges?.deliveryCharge)} ·
                  Discount -{money(detail.charges?.discount)} · Tax {money(detail.charges?.tax)}
                </p>
                <p>
                  <strong>Total {money(detail.charges?.grandTotal ?? detail.amount)}</strong>
                </p>
                {isPatient && detail.status === "WAITING_FOR_PATIENT_APPROVAL" && (
                  <div className="modal-actions">
                    <button
                      type="button"
                      onClick={() => {
                        if (window.confirm(`Accept amount ${money(detail.charges?.grandTotal ?? detail.amount)}?`)) {
                          void run(() => api.acceptMedicineAmount(detail.id), "Amount accepted");
                        }
                      }}
                    >
                      Accept amount
                    </button>
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => {
                        const reason = window.prompt("Reject reason", "Amount is too high") || "";
                        if (reason) void run(() => api.rejectMedicineAmount(detail.id, reason), "Amount rejected");
                      }}
                    >
                      Reject amount
                    </button>
                  </div>
                )}
                {detail.amountHistory && detail.amountHistory.length > 0 && (
                  <div>
                    <h3>Amount history</h3>
                    <ul>
                      {detail.amountHistory.map((h) => (
                        <li key={h.version}>
                          v{h.version} {money(h.grandTotal)} · {h.status} · {h.createdAt ? new Date(h.createdAt).toLocaleString() : ""}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {filesOpen && detail && (
        <div className="modal-backdrop" onClick={() => setFilesOpen(false)}>
          <div className="modal panel medicine-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Prescriptions · {detail.orderNumber}</h2>
              <button type="button" className="modal-close" onClick={() => setFilesOpen(false)}>
                ×
              </button>
            </div>
            <ul>
              {(detail.documents || []).map((d) => (
                <li key={d.id}>
                  <button
                    type="button"
                    className="link-button"
                    onClick={() => void previewDoc(detail.id, d.id, d.fileName, d.contentType)}
                  >
                    {d.fileName}
                    {d.latest ? " (latest)" : ""}
                  </button>
                </li>
              ))}
            </ul>
            {previewUrl && (
              <div className="medicine-preview">
                <p>{previewName}</p>
                {previewName.toLowerCase().endsWith(".pdf") ? (
                  <iframe title={previewName} src={previewUrl} />
                ) : (
                  <img alt={previewName} src={previewUrl} />
                )}
              </div>
            )}
            {(isPatient || isMedical || isHospital) && (
              <div className="stack">
                <FilePicker files={extraFiles} onPick={setExtraFiles} />
                <button
                  type="button"
                  disabled={extraFiles.length === 0}
                  onClick={() => {
                    const form = new FormData();
                    extraFiles.forEach((f) => form.append("files", f));
                    void run(() => api.uploadMedicineDocuments(detail.id, form), "Prescription uploaded");
                    setExtraFiles([]);
                  }}
                >
                  Upload additional prescription
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
