import { FormEvent, useEffect, useMemo, useState } from "react";
import { api, type MedicineOrder } from "../../api";
import { toast } from "../../toast";

type MedRow = { medicineName: string; quantity: string; days: string; price: string };

function emptyRow(): MedRow {
  return { medicineName: "", quantity: "1", days: "30", price: "" };
}

function money(n?: number | null) {
  if (n == null || Number.isNaN(n)) return "—";
  return `₹${n.toFixed(2)}`;
}

function addressOf(order: MedicineOrder) {
  if (order.fulfillment === "DELIVERY" && order.deliveryAddress?.trim()) {
    return order.deliveryAddress.trim();
  }
  return order.fulfillment === "DELIVERY" ? "Delivery address not given" : "Pickup at medical store";
}

export default function MedicalCheckOrderPage() {
  const [orders, setOrders] = useState<MedicineOrder[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [pendingReason, setPendingReason] = useState<Record<string, string>>({});
  const [amountOrder, setAmountOrder] = useState<MedicineOrder | null>(null);
  const [rows, setRows] = useState<MedRow[]>([emptyRow()]);
  const [savingAmount, setSavingAmount] = useState(false);

  const total = useMemo(
    () =>
      rows.reduce(
        (sum, row) => sum + (Number(row.quantity) || 0) * (Number(row.price) || 0),
        0,
      ),
    [rows],
  );

  async function load() {
    setLoading(true);
    setError("");
    try {
      const res = await api.medicineOrders();
      const list = (res.orders || []).filter((o) =>
        ["ORDERED", "PENDING", "IN_PROCESS", "WAITING_FOR_PATIENT_APPROVAL"].includes(o.status),
      );
      setOrders(list);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load orders");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function run(action: () => Promise<unknown>, ok: string) {
    setError("");
    try {
      await action();
      toast.success(ok);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Action failed");
    }
  }

  async function openAmount(order: MedicineOrder) {
    setError("");
    try {
      const full = await api.medicineOrder(order.id);
      setAmountOrder(full);
      setRows(
        full.items && full.items.length
          ? full.items.map((it) => ({
              medicineName: it.medicineName || "",
              quantity: String(it.quantity ?? 1),
              days: String(it.days || 30),
              price: String(it.unitPrice ?? ""),
            }))
          : [emptyRow()],
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not open amount");
    }
  }

  async function saveAmount(e: FormEvent) {
    e.preventDefault();
    if (!amountOrder) return;
    const items = rows
      .map((row) => ({
        medicineName: row.medicineName.trim(),
        quantity: Number(row.quantity) || 1,
        days: Math.max(1, Number(row.days) || 30),
        unitPrice: Number(row.price) || 0,
      }))
      .filter((row) => row.medicineName);
    if (items.length === 0) {
      setError("Add at least one medicine name and price");
      return;
    }
    setSavingAmount(true);
    setError("");
    try {
      await api.saveMedicineQuote(
        amountOrder.id,
        {
          items,
          charges: {
            deliveryCharge: 0,
            packagingCharge: 0,
            tax: 0,
            otherCharges: 0,
            discount: 0,
          },
        },
        true,
      );
      toast.success(`Total ${money(items.reduce((s, it) => s + it.quantity * it.unitPrice, 0))} sent to patient`);
      setAmountOrder(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save amount");
    } finally {
      setSavingAmount(false);
    }
  }

  return (
    <section className="stack check-order-page">
      <h1>Check order</h1>
      <p className="lead">
        Each order opens as a card. Accept to start processing, fill amount, or mark pending with a
        reason.
      </p>
      {error && <div className="msg error">{error}</div>}
      {loading ? (
        <p className="muted">Loading…</p>
      ) : orders.length === 0 ? (
        <p className="muted">No new, pending, or in-process orders.</p>
      ) : (
        <div className="check-order-grid">
          {orders.map((order) => {
            const open = !order.assignedStoreId && ["ORDERED", "PENDING"].includes(order.status);
            const working =
              order.status === "IN_PROCESS" ||
              order.status === "WAITING_FOR_PATIENT_APPROVAL" ||
              (order.status === "PENDING" && !!order.assignedStoreId);
            return (
              <article key={order.id} className="panel check-order-card">
                <header className="check-order-card-head">
                  <strong>{order.orderNumber}</strong>
                  <span className={`badge ${order.status}`}>
                    {order.status === "IN_PROCESS"
                      ? "In process"
                      : order.status === "WAITING_FOR_PATIENT_APPROVAL"
                        ? "Waiting approval"
                      : order.status === "PENDING"
                        ? "Pending"
                        : "New"}
                  </span>
                </header>
                <dl className="check-order-meta">
                  <div>
                    <dt>Patient name</dt>
                    <dd>{order.patientName}</dd>
                  </div>
                  <div>
                    <dt>Address</dt>
                    <dd>{addressOf(order)}</dd>
                  </div>
                  <div>
                    <dt>Amount</dt>
                    <dd>
                      {working ? (
                        <button type="button" className="link-button" onClick={() => void openAmount(order)}>
                          {order.amount != null ? money(order.amount) : "Fill amount"}
                        </button>
                      ) : (
                        money(order.amount)
                      )}
                    </dd>
                  </div>
                </dl>
                {order.pendingReason && (
                  <p className="muted">Pending reason: {order.pendingReason}</p>
                )}
                {open && (
                  <div className="check-order-actions">
                    <button
                      type="button"
                      onClick={() => void run(() => api.acceptMedicineOrder(order.id), "Order is in process")}
                    >
                      Accept
                    </button>
                    <button
                      type="button"
                      className="secondary"
                      onClick={() => {
                        const reason = window.prompt("Reject reason", "") || "";
                        if (reason) void run(() => api.rejectMedicineOrder(order.id, reason), "Order rejected");
                      }}
                    >
                      Reject
                    </button>
                  </div>
                )}
                {working && (
                  <div className="check-order-pending">
                    {order.status === "WAITING_FOR_PATIENT_APPROVAL" ? (
                      <p className="muted">Amount sent. Patient sees this as Total.</p>
                    ) : order.status === "PENDING" ? (
                      <button
                        type="button"
                        onClick={() => void run(() => api.acceptMedicineOrder(order.id), "Order is in process")}
                      >
                        Accept / resume
                      </button>
                    ) : (
                      <>
                        <label>
                          Pending reason
                          <input
                            value={pendingReason[order.id] || ""}
                            onChange={(e) =>
                              setPendingReason((cur) => ({ ...cur, [order.id]: e.target.value }))
                            }
                            placeholder="Why is this order pending?"
                            required
                          />
                        </label>
                        <button
                          type="button"
                          className="secondary"
                          onClick={() => {
                            const reason = (pendingReason[order.id] || "").trim();
                            if (!reason) {
                              setError("Enter a pending reason");
                              return;
                            }
                            void run(() => api.markMedicinePending(order.id, reason), "Order marked pending");
                          }}
                        >
                          Pending
                        </button>
                      </>
                    )}
                  </div>
                )}
              </article>
            );
          })}
        </div>
      )}

      {amountOrder && (
        <div className="modal-backdrop" onClick={() => setAmountOrder(null)}>
          <div className="modal panel medicine-modal check-order-amount" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Amount · {amountOrder.orderNumber}</h2>
              <button type="button" className="modal-close" onClick={() => setAmountOrder(null)}>
                ×
              </button>
            </div>
            <p className="muted">
              {amountOrder.patientName} · Enter quantity, days on the slip, and unit price. Patient can
              reduce quantity or days; unit price stays locked.
            </p>
            <form className="stack" onSubmit={(e) => void saveAmount(e)}>
              {rows.map((row, idx) => (
                <div key={idx} className="check-order-med-row">
                  <label>
                    Medicine name
                    <input
                      value={row.medicineName}
                      onChange={(e) =>
                        setRows((cur) =>
                          cur.map((r, i) => (i === idx ? { ...r, medicineName: e.target.value } : r)),
                        )
                      }
                      placeholder="e.g. Paracetamol 500"
                      required
                    />
                  </label>
                  <label>
                    Qty
                    <input
                      type="number"
                      min={0.01}
                      step="0.01"
                      value={row.quantity}
                      onChange={(e) =>
                        setRows((cur) =>
                          cur.map((r, i) => (i === idx ? { ...r, quantity: e.target.value } : r)),
                        )
                      }
                      required
                    />
                  </label>
                  <label>
                    Days
                    <input
                      type="number"
                      min={1}
                      step="1"
                      value={row.days}
                      onChange={(e) =>
                        setRows((cur) =>
                          cur.map((r, i) => (i === idx ? { ...r, days: e.target.value } : r)),
                        )
                      }
                      required
                    />
                  </label>
                  <label>
                    Unit price
                    <input
                      type="number"
                      min={0}
                      step="0.01"
                      value={row.price}
                      onChange={(e) =>
                        setRows((cur) => cur.map((r, i) => (i === idx ? { ...r, price: e.target.value } : r)))
                      }
                      required
                    />
                  </label>
                </div>
              ))}
              <button type="button" className="secondary" onClick={() => setRows((cur) => [...cur, emptyRow()])}>
                Add medicine
              </button>
              <p>
                <strong>Total {money(total)}</strong>
              </p>
              <div className="modal-actions">
                <button type="submit" disabled={savingAmount}>
                  {savingAmount ? "Saving…" : "Save"}
                </button>
                <button type="button" className="secondary" onClick={() => setAmountOrder(null)}>
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </section>
  );
}
