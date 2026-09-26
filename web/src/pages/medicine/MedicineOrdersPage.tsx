import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  api,
  fetchMedicineDocumentBlob,
  type MedicineOrder,
  type MedicineOrderItemInput,
  type MedicalStore,
} from "../../api";
import NativeFileInput from "../../components/NativeFileInput";
import { IndiaStateCityFields } from "../../components/IndiaStateCityFields";
import { session } from "../../dl/MedTrackSession";
import { toast } from "../../toast";

const STATUS_LABEL: Record<string, string> = {
  ORDERED: "Ordered",
  PENDING: "Pending",
  IN_PROCESS: "In process",
  WAITING_FOR_PATIENT_APPROVAL: "Awaiting approval from patient",
  AMOUNT_ACCEPTED: "Amount accepted",
  MEDICINE_READY: "Medicine ready",
  COMPLETED: "Completed",
  CANCELED: "Canceled",
};

const OPEN = new Set(["ORDERED", "PENDING"]);

function locKey(v?: string) {
  return (v || "").trim().toLowerCase();
}

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
    days: 30,
    requestedDays: 30,
    quotedQuantity: 1,
    availability: "AVAILABLE",
    substituteName: "",
    substituteReason: "",
  };
}

function prescribedDaysOf(it: QuoteItem) {
  return Math.max(1, Number(it.days || 30));
}

function quotedQtyOf(it: QuoteItem) {
  const q = Number(it.quotedQuantity);
  if (q > 0) return q;
  return Number(it.quantity || 0);
}

function scaleQty(quoted: number, prescribed: number, requested: number) {
  if (prescribed <= 0 || requested >= prescribed) return quoted;
  const raw = quoted * (requested / prescribed);
  if (Number.isInteger(quoted) && raw >= 1) return Math.round(raw);
  return Math.max(0.01, Math.round(raw * 100) / 100);
}

function patientCanEditOrder(status?: string, amountStatus?: string, paymentStatus?: string) {
  const st = (status || "").toUpperCase();
  const amt = (amountStatus || "").toUpperCase();
  const pay = (paymentStatus || "").toUpperCase();
  if (pay === "PAID") return false;
  return (
    st === "WAITING_FOR_PATIENT_APPROVAL" ||
    amt === "SENT" ||
    (st === "AMOUNT_ACCEPTED" && pay !== "PAID")
  );
}

function qtyStepOf(it: QuoteItem) {
  return Number.isInteger(quotedQtyOf(it)) ? 1 : 0.01;
}

type RzpCheckout = {
  open: () => void;
  on: (event: string, handler: (res: { error?: { description?: string } }) => void) => void;
};

async function loadRazorpay(): Promise<new (opts: Record<string, unknown>) => RzpCheckout> {
  const w = window as unknown as {
    Razorpay?: new (opts: Record<string, unknown>) => RzpCheckout;
  };
  if (w.Razorpay) return w.Razorpay;
  await new Promise<void>((resolve, reject) => {
    const existing = document.querySelector("script[data-razorpay]");
    if (existing) {
      existing.addEventListener("load", () => resolve());
      existing.addEventListener("error", () => reject(new Error("Could not load Razorpay")));
      return;
    }
    const s = document.createElement("script");
    s.src = "https://checkout.razorpay.com/v1/checkout.js";
    s.async = true;
    s.dataset.razorpay = "1";
    s.onload = () => resolve();
    s.onerror = () => reject(new Error("Could not load Razorpay"));
    document.body.appendChild(s);
  });
  if (!w.Razorpay) throw new Error("Razorpay is not available");
  return w.Razorpay;
}

type UpiPayApp = { id: string; name: string; uri: string };

function upiPayQuery(upi: string, name: string, amount: number, note: string) {
  const q = new URLSearchParams({
    pa: upi,
    pn: name,
    am: amount.toFixed(2),
    cu: "INR",
    tn: note,
  });
  return q.toString();
}

function defaultUpiApps(upi: string, name: string, amount: number, note: string): UpiPayApp[] {
  const q = upiPayQuery(upi, name, amount, note);
  return [
    { id: "gpay", name: "Google Pay", uri: `tez://upi/pay?${q}` },
    { id: "phonepe", name: "PhonePe", uri: `phonepe://pay?${q}` },
    { id: "paytm", name: "Paytm", uri: `paytmmp://pay?${q}` },
    { id: "bhim", name: "BHIM", uri: `bhim://pay?${q}` },
    { id: "upi", name: "Other UPI apps", uri: `upi://pay?${q}` },
  ];
}

function openUpiApp(uri: string) {
  const link = document.createElement("a");
  link.href = uri;
  link.rel = "noopener";
  document.body.appendChild(link);
  link.click();
  link.remove();
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

  const [stores, setStores] = useState<MedicalStore[]>([]);
  const [storesLoaded, setStoresLoaded] = useState(false);
  const [filterState, setFilterState] = useState("");
  const [filterCity, setFilterCity] = useState("");
  const [storeId, setStoreId] = useState("");
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
  const [wantedDays, setWantedDays] = useState(30);
  const [extraFiles, setExtraFiles] = useState<File[]>([]);
  const [payStep, setPayStep] = useState(false);
  const [payShot, setPayShot] = useState<File[]>([]);
  const [paying, setPaying] = useState(false);
  const [payHint, setPayHint] = useState("");
  const [patientReview, setPatientReview] = useState(false);

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
    if (!isPatient) return;
    void api
      .medicalStores(undefined, true, true)
      .then((r) => setStores(r.stores || []))
      .catch(() => setStores([]))
      .finally(() => setStoresLoaded(true));
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

  const slipDays = useMemo(
    () => quoteItems.reduce((m, it) => Math.max(m, prescribedDaysOf(it)), 30),
    [quoteItems],
  );

  const filteredStores = useMemo(() => {
    return stores.filter((s) => {
      if (filterState && locKey(s.state) !== locKey(filterState)) return false;
      if (filterCity && locKey(s.city) !== locKey(filterCity)) return false;
      return true;
    });
  }, [stores, filterState, filterCity]);

  useEffect(() => {
    if (storeId && !filteredStores.some((s) => s.id === storeId)) {
      setStoreId("");
    }
  }, [storeId, filteredStores]);

  function applyWantedDays(nextDays: number) {
    const wanted = Math.max(1, Math.min(slipDays, Math.round(nextDays) || 1));
    setWantedDays(wanted);
    setQuoteItems((rows) =>
      rows.map((r) => {
        const prescribed = prescribedDaysOf(r);
        const requested = Math.min(wanted, prescribed);
        return {
          ...r,
          requestedDays: requested,
          quantity: scaleQty(quotedQtyOf(r), prescribed, requested),
        };
      }),
    );
  }

  function removeQuoteItem(index: number) {
    setQuoteItems((rows) => {
      if (rows.length <= 1) {
        setError("Keep at least one medicine, or reject the amount");
        return rows;
      }
      return rows.filter((_, idx) => idx !== index);
    });
  }

  function bumpItemQty(index: number, dir: 1 | -1) {
    setQuoteItems((rows) =>
      rows.map((r, idx) => {
        if (idx !== index) return r;
        const prescribed = prescribedDaysOf(r);
        const requested = Math.max(1, Number(r.requestedDays || prescribed));
        const maxQty = scaleQty(quotedQtyOf(r), prescribed, requested);
        const step = qtyStepOf(r);
        const next = Math.round((Number(r.quantity || 0) + dir * step) * 100) / 100;
        return { ...r, quantity: Math.min(maxQty, Math.max(step, next)) };
      }),
    );
  }

  function bumpItemDays(index: number, dir: 1 | -1) {
    setQuoteItems((rows) =>
      rows.map((r, idx) => {
        if (idx !== index) return r;
        const prescribed = prescribedDaysOf(r);
        const days = Math.max(1, Math.min(prescribed, Number(r.requestedDays || prescribed) + dir));
        return {
          ...r,
          requestedDays: days,
          quantity: scaleQty(quotedQtyOf(r), prescribed, days),
        };
      }),
    );
  }

  function reviewPayload() {
    return {
      requestedDays: wantedDays,
      items: quoteItems.map((it) => ({
        id: it.id,
        quantity: Number(it.quantity) || 0,
        requestedDays: Number(it.requestedDays || it.days || wantedDays),
      })),
    };
  }

  function payAppsFor(order: MedicineOrder): UpiPayApp[] {
    if (order.upiApps && order.upiApps.length > 0) return order.upiApps;
    return defaultUpiApps(
      order.upiId || "medtrackpharmacy@upi",
      order.assignedStoreName || "Medical store",
      grand,
      order.orderNumber,
    );
  }

  async function startUpiApp(app: UpiPayApp) {
    setError("");
    setPayHint("");
    setPaying(true);
    try {
      const created = await api.createMedicineRazorpayOrder(detail!.id, reviewPayload());
      if (created.order) setDetail(created.order);
      const match = (created.upiApps || payAppsFor(created.order || detail!)).find((row) => row.id === app.id);
      openUpiApp(match?.uri || app.uri);
      setPayHint(`Complete payment in ${app.name}, then upload the payment screenshot.`);
    } catch (err) {
      openUpiApp(app.uri);
      setPayHint(`Complete payment in ${app.name}, then upload the payment screenshot.`);
      setError(err instanceof Error ? err.message : "");
    } finally {
      setPaying(false);
    }
  }

  async function startRazorpayCheckout() {
    setPaying(true);
    setError("");
    setPayHint("");
    try {
      const created = await api.createMedicineRazorpayOrder(detail!.id, reviewPayload());
      if (created.order) setDetail(created.order);
      if (!created.razorpayOrderId || !created.keyId) {
        setPayHint("Use Google Pay, PhonePe, Paytm, or another UPI app below, then upload the screenshot.");
        return;
      }
      const Razorpay = await loadRazorpay();
      const rzp = new Razorpay({
        key: created.keyId,
        amount: created.amount,
        currency: created.currency || "INR",
        name: "MedTrack",
        description: detail!.orderNumber,
        order_id: created.razorpayOrderId,
        prefill: {
          name: session.getPatientName() || session.getUsername(),
          contact: session.getPatientPhone(),
          method: "upi",
        },
        method: {
          upi: true,
          card: true,
          netbanking: true,
          wallet: true,
        },
        config: {
          display: {
            blocks: {
              upi: {
                name: "UPI apps",
                instruments: [{ method: "upi" }],
              },
              more: {
                name: "Cards, netbanking & wallets",
                instruments: [{ method: "card" }, { method: "netbanking" }, { method: "wallet" }],
              },
            },
            sequence: ["block.upi", "block.more"],
            preferences: { show_default_blocks: true },
          },
        },
        theme: { color: "#0f766e" },
        handler: (res: {
          razorpay_order_id: string;
          razorpay_payment_id: string;
          razorpay_signature: string;
        }) => {
          void run(
            () =>
              api.verifyMedicineRazorpayPayment(detail!.id, {
                ...reviewPayload(),
                razorpayOrderId: res.razorpay_order_id,
                razorpayPaymentId: res.razorpay_payment_id,
                razorpaySignature: res.razorpay_signature,
              }),
            "Payment successful",
          );
        },
        modal: {
          ondismiss: () => setPayHint("Payment window closed. You can still pay with Google Pay, PhonePe, or Paytm."),
        },
      });
      rzp.on("payment.failed", (res) => {
        setError(res.error?.description || "Payment failed. Try another app.");
      });
      rzp.open();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not start online payment.");
      setPayHint("Use Google Pay, PhonePe, Paytm, or scan the QR.");
    } finally {
      setPaying(false);
    }
  }

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (!patientName.trim() || !patientPhone.trim()) {
      setError("Patient name and phone are required");
      return;
    }
    const resolvedHospitalId = hospitalId || session.getHospitalId();
    if (!isPatient && !resolvedHospitalId) {
      setError("Hospital is not set for this session");
      return;
    }
    if (files.length === 0) {
      setError("Upload at least one prescription (PDF, JPG, PNG)");
      return;
    }
    setSaving(true);
    try {
      const form = new FormData();
      if (isPatient) {
        if (storeId) {
          form.set("storeId", storeId);
          if (resolvedHospitalId) form.set("hospitalId", resolvedHospitalId);
        }
      } else if (resolvedHospitalId) {
        form.set("hospitalId", resolvedHospitalId);
      }
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
    setPayStep(false);
    setPayShot([]);
    setPayHint("");
    const reviewFromRow =
      isPatient && patientCanEditOrder(order.status, order.amountStatus, order.paymentStatus);
    setPatientReview(reviewFromRow);
    try {
      const full = await api.medicineOrder(order.id);
      setDetail(full);
      setPatientReview(
        isPatient &&
          (reviewFromRow || patientCanEditOrder(full.status, full.amountStatus, full.paymentStatus)),
      );
      const mapped = (full.items || []).map((it) => ({
        id: it.id,
        prescribedName: it.prescribedName || it.medicineName,
        medicineName: it.medicineName,
        quantity: it.quantity,
        unitPrice: it.unitPrice ?? 0,
        days: it.days || 30,
        requestedDays: it.requestedDays || it.days || 30,
        quotedQuantity: it.quotedQuantity || it.quantity,
        availability: it.availability || "AVAILABLE",
        substituteName: it.substituteName || "",
        substituteReason: it.substituteReason || "",
      }));
      setQuoteItems(mapped.length ? mapped : isPatient ? [] : [emptyItem()]);
      const slip = (full.items || []).reduce((m, it) => Math.max(m, it.days || 30), 30);
      const want = (full.items || []).reduce(
        (m, it) => Math.min(m, it.requestedDays || it.days || slip),
        slip,
      );
      setWantedDays(want || slip);
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
      setPatientReview(false);
      setPayStep(false);
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
          {isPatient ? (
            <>
              <p className="muted">
                State, city, and medical store are optional. Leave them empty to send the prescription
                to all medical stores.
              </p>
              <IndiaStateCityFields
                allowAll
                state={filterState}
                city={filterCity}
                onStateChange={setFilterState}
                onCityChange={setFilterCity}
              />
              <label>
                Medical store
                <select
                  value={storeId}
                  onChange={(e) => {
                    const next = e.target.value;
                    setStoreId(next);
                    const picked = stores.find((s) => s.id === next);
                    setHospitalId(picked ? String(picked.hospitalId) : "");
                  }}
                >
                  <option value="">Any medical store</option>
                  {filteredStores.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.storeName}
                      {s.city || s.state ? ` — ${[s.city, s.state].filter(Boolean).join(", ")}` : ""}
                    </option>
                  ))}
                </select>
              </label>
              {storesLoaded && filteredStores.length === 0 ? (
                <p className="muted">
                  No medical stores in this state/city. Clear the filters to see all, or leave the store empty.
                </p>
              ) : null}
            </>
          ) : null}
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
                {!isPatient && <th>Patient</th>}
                <th>Prescription</th>
                <th>Date</th>
                <th>Store</th>
                <th>Amount</th>
                <th>Amount status</th>
                <th>Order status</th>
                {isPatient && <th>Approve</th>}
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.id}>
                  <td>
                    <strong>{o.orderNumber}</strong>
                  </td>
                  {!isPatient && (
                    <td>
                      {o.patientName}
                      <div className="muted">{o.patientPhone}</div>
                    </td>
                  )}
                  <td>
                    <button type="button" className="link-button" onClick={() => void openFiles(o)}>
                      View files ({o.prescriptionCount ?? o.documents?.length ?? 0})
                    </button>
                  </td>
                  <td>{o.createdAt ? new Date(o.createdAt).toLocaleString() : "—"}</td>
                  <td>{o.assignedStoreName || "—"}</td>
                  <td>
                    {isPatient && patientCanEditOrder(o.status, o.amountStatus, o.paymentStatus) ? (
                      <button
                        type="button"
                        className="link-button medicine-approve-link"
                        onClick={() => void openAmount(o)}
                      >
                        {o.amountVisible === false ? "Review & pay" : `Total ${money(o.amount)}`}
                      </button>
                    ) : o.amountVisible === false && !isMedical ? (
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
                  {isPatient && (
                    <td>
                      {patientCanEditOrder(o.status, o.amountStatus, o.paymentStatus) ? (
                        <button
                          type="button"
                          className="link-button medicine-approve-link"
                          onClick={() => void openAmount(o)}
                        >
                          Approve / Pay
                        </button>
                      ) : (
                        "—"
                      )}
                    </td>
                  )}
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
        <div className="modal-backdrop" onClick={() => { if (!isPatient) setAmountOpen(false); }}>
          <div className="modal panel medicine-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>
                {patientReview
                  ? payStep
                    ? `Pay · ${detail.orderNumber}`
                    : `Review medicines · ${detail.orderNumber}`
                  : `Amount · ${detail.orderNumber}`}
              </h2>
              <button
                type="button"
                className="modal-close"
                onClick={() => {
                  setAmountOpen(false);
                  setPatientReview(false);
                  setPayStep(false);
                }}
              >
                ×
              </button>
            </div>
            <p className="muted">
              {detail.assignedStoreName || "Store not assigned"} · {detail.patientName}
            </p>

            {!patientReview && isMedical && (detail.status === "IN_PROCESS" || detail.status === "AMOUNT_ACCEPTED" || detail.status === "WAITING_FOR_PATIENT_APPROVAL") ? (
              <div className="stack">
                <div className="medicine-quote-row medicine-quote-head">
                  <span>Prescribed</span>
                  <span>Medicine</span>
                  <span>Qty</span>
                  <span>Days</span>
                  <span>Unit price</span>
                  <span>Stock</span>
                  <span>Total</span>
                </div>
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
                      title="Quantity"
                      placeholder="Qty"
                      value={it.quantity}
                      onChange={(e) =>
                        setQuoteItems((rows) =>
                          rows.map((r, i) => (i === idx ? { ...r, quantity: Number(e.target.value), quotedQuantity: Number(e.target.value) } : r)),
                        )
                      }
                    />
                    <input
                      type="number"
                      min={1}
                      step="1"
                      title="Days on slip"
                      placeholder="Days"
                      value={it.days ?? 30}
                      onChange={(e) =>
                        setQuoteItems((rows) =>
                          rows.map((r, i) => {
                            if (i !== idx) return r;
                            const days = Math.max(1, Number(e.target.value) || 1);
                            return { ...r, days, requestedDays: days };
                          }),
                        )
                      }
                    />
                    <input
                      type="number"
                      min={0}
                      step="0.01"
                      title="Unit price"
                      placeholder="Price"
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
                {patientReview && !payStep ? (
                  <p className="patient-note">
                    Medical sent these medicines. Change days or quantity, remove any item you do not want,
                    then Approve. Unit price is locked. Total updates as you edit.
                  </p>
                ) : null}
                {patientReview && !payStep ? (
                  <label>
                    How many days do you want?
                    <input
                      type="number"
                      min={1}
                      max={slipDays}
                      value={wantedDays}
                      onChange={(e) => applyWantedDays(Number(e.target.value))}
                    />
                    <span className="muted"> Slip is {slipDays} days. Quantity scales automatically.</span>
                  </label>
                ) : null}
                {payStep && patientReview ? (
                  <div className="medicine-pay-panel">
                    <p>
                      <strong>Pay {money(grand)}</strong> to {detail.assignedStoreName || "the medical store"}
                    </p>
                    <div className="medicine-pay-grid">
                      <div className="medicine-qr-box">
                        <p className="muted">Scan UPI QR</p>
                        {(() => {
                          const upi = detail.upiId || "medtrackpharmacy@upi";
                          const uri = `upi://pay?pa=${encodeURIComponent(upi)}&pn=${encodeURIComponent(detail.assignedStoreName || "Medical store")}&am=${grand.toFixed(2)}&cu=INR&tn=${encodeURIComponent(detail.orderNumber)}`;
                          const qr = `https://api.qrserver.com/v1/create-qr-code/?size=240x240&data=${encodeURIComponent(uri)}`;
                          return (
                            <>
                              <img alt="UPI payment QR" src={qr} width={220} height={220} />
                              <p className="muted">{upi}</p>
                              <a className="link-button" href={uri}>
                                Open UPI app
                              </a>
                            </>
                          );
                        })()}
                      </div>
                      <div className="stack">
                        <p>
                          <strong>Pay with UPI app</strong>
                        </p>
                        <p className="muted">Google Pay, PhonePe, Paytm, BHIM, and other UPI apps.</p>
                        <div className="pay-app-grid">
                          {payAppsFor(detail).map((app) => (
                            <button
                              key={app.id}
                              type="button"
                              className={`pay-app-btn pay-app-btn--${app.id}`}
                              disabled={paying || quoteItems.length === 0}
                              onClick={() => void startUpiApp(app)}
                            >
                              <span className="pay-app-mark" aria-hidden>
                                {app.id === "gpay"
                                  ? "G"
                                  : app.id === "phonepe"
                                    ? "Pe"
                                    : app.id === "paytm"
                                      ? "Pa"
                                      : app.id === "bhim"
                                        ? "B"
                                        : "U"}
                              </span>
                              {app.name}
                            </button>
                          ))}
                        </div>
                        <button
                          type="button"
                          disabled={paying || quoteItems.length === 0}
                          onClick={() => void startRazorpayCheckout()}
                        >
                          {paying ? "Opening payment…" : "Cards, netbanking & wallets (Razorpay)"}
                        </button>
                        {payHint ? <p className="patient-note">{payHint}</p> : null}
                        <p>
                          <strong>After paying — upload screenshot</strong>
                        </p>
                        <p className="muted">Required for UPI apps. Razorpay success is confirmed automatically.</p>
                        <FilePicker files={payShot} onPick={setPayShot} />
                        <button
                          type="button"
                          disabled={payShot.length === 0 || paying}
                          onClick={() => {
                            const form = new FormData();
                            payShot.forEach((f) => form.append("files", f));
                            form.set("payload", JSON.stringify(reviewPayload()));
                            void run(
                              () => api.uploadMedicinePaymentScreenshot(detail.id, form),
                              "Payment screenshot uploaded",
                            );
                          }}
                        >
                          Upload payment screenshot
                        </button>
                      </div>
                    </div>
                    <div className="modal-actions">
                      <button type="button" className="secondary" onClick={() => setPayStep(false)}>
                        Back to medicines
                      </button>
                    </div>
                  </div>
                ) : (
                  <>
                {quoteItems.length === 0 ? (
                  <p className="muted">No medicines on this quote yet.</p>
                ) : (
                <table className="table">
                  <thead>
                    <tr>
                      <th>Medicine</th>
                      <th>Days</th>
                      <th>Qty</th>
                      <th>Unit price</th>
                      <th>Total</th>
                      <th>Availability</th>
                      {patientReview && <th>Remove</th>}
                    </tr>
                  </thead>
                  <tbody>
                    {quoteItems.map((it, i) => {
                      const prescribed = prescribedDaysOf(it);
                      const requested = Math.max(1, Number(it.requestedDays || prescribed));
                      const maxQty = scaleQty(quotedQtyOf(it), prescribed, requested);
                      const line = Number(it.quantity || 0) * Number(it.unitPrice || 0);
                      const patientEdit = patientReview;
                      return (
                        <tr key={it.id || i}>
                          <td>
                            {it.medicineName}
                            {it.substituteName ? (
                              <div className="muted">
                                Substitute for {it.prescribedName}: {it.substituteName}
                              </div>
                            ) : null}
                            {patientEdit ? (
                              <div className="muted">Slip {prescribed} days · max qty {maxQty}</div>
                            ) : null}
                          </td>
                          <td>
                            {patientEdit ? (
                              <div className="qty-stepper">
                                <button type="button" onClick={() => bumpItemDays(i, -1)} aria-label="Fewer days">
                                  −
                                </button>
                                <input
                                  type="number"
                                  min={1}
                                  max={prescribed}
                                  value={requested}
                                  onChange={(e) => {
                                    const days = Math.max(
                                      1,
                                      Math.min(prescribed, Number(e.target.value) || 1),
                                    );
                                    setQuoteItems((rows) =>
                                      rows.map((r, idx) =>
                                        idx === i
                                          ? {
                                              ...r,
                                              requestedDays: days,
                                              quantity: scaleQty(quotedQtyOf(r), prescribedDaysOf(r), days),
                                            }
                                          : r,
                                      ),
                                    );
                                  }}
                                />
                                <button type="button" onClick={() => bumpItemDays(i, 1)} aria-label="More days">
                                  +
                                </button>
                              </div>
                            ) : (
                              `${requested}/${prescribed}`
                            )}
                          </td>
                          <td>
                            {patientEdit ? (
                              <div className="qty-stepper">
                                <button type="button" onClick={() => bumpItemQty(i, -1)} aria-label="Decrease quantity">
                                  −
                                </button>
                                <input
                                  type="number"
                                  min={qtyStepOf(it)}
                                  max={maxQty}
                                  step={qtyStepOf(it)}
                                  value={it.quantity}
                                  onChange={(e) => {
                                    const raw = Number(e.target.value);
                                    const qty = Math.min(maxQty, Math.max(qtyStepOf(it), raw || 0));
                                    setQuoteItems((rows) =>
                                      rows.map((r, idx) => (idx === i ? { ...r, quantity: qty } : r)),
                                    );
                                  }}
                                />
                                <button type="button" onClick={() => bumpItemQty(i, 1)} aria-label="Increase quantity">
                                  +
                                </button>
                              </div>
                            ) : (
                              it.quantity
                            )}
                          </td>
                          <td>{money(it.unitPrice)}</td>
                          <td>{money(line)}</td>
                          <td>{it.availability}</td>
                          {patientEdit && (
                            <td>
                              <button
                                type="button"
                                className="link-button danger"
                                onClick={() => removeQuoteItem(i)}
                              >
                                Remove
                              </button>
                            </td>
                          )}
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
                )}
                <p>
                  Subtotal {money(subtotal)} · Delivery {money(charges.deliveryCharge)} · Discount -
                  {money(charges.discount)} · Tax {money(charges.tax)}
                </p>
                <p>
                  <strong>Total {money(grand)}</strong>
                </p>
                {patientReview && (
                  <div className="modal-actions">
                    <button
                      type="button"
                      disabled={quoteItems.length === 0}
                      onClick={() => {
                        if (quoteItems.length === 0) {
                          setError("Keep at least one medicine, or reject the amount");
                          return;
                        }
                        setPayStep(true);
                      }}
                    >
                      Approve &amp; pay {money(grand)}
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
                  </>
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
