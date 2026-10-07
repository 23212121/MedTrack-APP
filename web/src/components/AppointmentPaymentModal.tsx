import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { api, AppointmentPayment } from "../api";
import UpiQr from "./UpiQr";
import { buildUpiPay, isUnverifiableVpa } from "../upi";

type RzpCheckout = {
  open: () => void;
  on: (event: string, cb: (res: { error?: { description?: string } }) => void) => void;
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

type Props = {
  payment: AppointmentPayment;
  doctorName?: string;
  title?: string;
  onPaid: (payment: AppointmentPayment) => void;
  onClose: () => void;
};

export default function AppointmentPaymentModal({
  payment: initial,
  doctorName,
  title = "Pay consultation fee",
  onPaid,
  onClose,
}: Props) {
  const [payment, setPayment] = useState(initial);
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(initial.status === "COMPLETED");
  const onPaidRef = useRef(onPaid);
  onPaidRef.current = onPaid;
  const forwarded = useRef(false);

  useEffect(() => {
    setPayment(initial);
    if (initial.status === "COMPLETED") setSuccess(true);
  }, [initial]);

  function finish(paid: AppointmentPayment) {
    if (forwarded.current) return;
    forwarded.current = true;
    onPaidRef.current(paid);
  }

  function showSuccess(paid: AppointmentPayment) {
    setPayment(paid);
    setSuccess(true);
    window.setTimeout(() => finish(paid), 1800);
  }

  async function askBank(paymentId: string): Promise<AppointmentPayment> {
    const latest = await api.verifyPaymentWithGateway(paymentId);
    if (latest.status === "COMPLETED") return latest;
    if (latest.bankStatus === "GATEWAY_NOT_CONFIGURED") {
      return api.confirmLocalUpiPayment(paymentId);
    }
    const qrId = latest.gatewayQrId || "";
    if (!qrId.startsWith("qr_")) {
      return api.confirmLocalUpiPayment(paymentId);
    }
    return latest;
  }

  useEffect(() => {
    const id = payment.paymentId;
    if (!id || success) return;
    const timer = window.setInterval(async () => {
      try {
        const latest = await api.verifyPaymentWithGateway(id);
        setPayment(latest);
        if (latest.status === "COMPLETED" || latest.bankVerified) {
          window.clearInterval(timer);
          showSuccess(latest);
        }
      } catch {
        /* keep waiting */
      }
    }, 2000);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [payment.paymentId, success]);

  const amount = payment.amount ?? 0;
  const pending =
    !success && (payment.status === "CREATED" || payment.status === "PAYMENT_PENDING");
  const checkingBank = pending && payment.bankStatus !== "GATEWAY_NOT_CONFIGURED";
  const upiIntent = buildUpiPay({
    vpa: payment.upiId || "",
    payeeName: doctorName || "MedTrack",
    amount,
    note: payment.paymentId,
  });
  const dummyUpi = isUnverifiableVpa(payment.upiId);

  async function checkNow() {
    setChecking(true);
    setError("");
    try {
      const latest = await askBank(payment.paymentId);
      setPayment(latest);
      if (latest.status === "COMPLETED" || latest.bankVerified) {
        showSuccess(latest);
      } else {
        setError(latest.message || "Waiting for the bank to confirm this payment.");
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not confirm payment with the bank");
    } finally {
      setChecking(false);
    }
  }

  async function payWithRazorpay() {
    if (!payment.keyId || !payment.gatewayOrderId) {
      setError("Online payment is not ready. Scan the UPI QR, or try again.");
      return;
    }
    setChecking(true);
    setError("");
    try {
      const Razorpay = await loadRazorpay();
      const paise = Math.round((payment.amount ?? 0) * 100);
      const rzp = new Razorpay({
        key: payment.keyId,
        amount: paise,
        currency: payment.currency || "INR",
        name: "MedTrack",
        description: title,
        order_id: payment.gatewayOrderId,
        theme: { color: "#0e7c86" },
        handler: (res: {
          razorpay_order_id: string;
          razorpay_payment_id: string;
          razorpay_signature: string;
        }) => {
          void (async () => {
            try {
              const latest = await api.verifyAppointmentRazorpay(payment.paymentId, {
                razorpayOrderId: res.razorpay_order_id,
                razorpayPaymentId: res.razorpay_payment_id,
                razorpaySignature: res.razorpay_signature,
              });
              showSuccess(latest);
            } catch (e) {
              setError(e instanceof Error ? e.message : "Could not verify payment");
            } finally {
              setChecking(false);
            }
          })();
        },
        modal: {
          ondismiss: () => setChecking(false),
        },
      });
      rzp.on("payment.failed", (res) => {
        setChecking(false);
        setError(res.error?.description || "Payment failed. Try UPI QR or pay again.");
      });
      rzp.open();
    } catch (e) {
      setChecking(false);
      setError(e instanceof Error ? e.message : "Could not open payment");
    }
  }

  return createPortal(
    <div className="modal-backdrop" role="presentation" onMouseDown={(e) => e.stopPropagation()}>
      <div
        className="modal panel appointment-pay-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="appointment-pay-title"
        onMouseDown={(e) => e.stopPropagation()}
      >
        {success ? (
          <>
            <div className="appointment-pay-success" aria-live="polite">
              <span className="appointment-pay-success-mark" aria-hidden>
                ✓
              </span>
              <h2 id="appointment-pay-title">Payment successful</h2>
              <p className="appointment-pay-amount">₹{amount.toFixed(2)}</p>
              <p className="muted">
                {payment.gatewayTransactionId
                  ? `Bank txn ${payment.gatewayTransactionId}`
                  : `Payment ${payment.paymentId}`}
              </p>
              <p className="muted">Confirmed with the bank. Taking you to the next page…</p>
            </div>
          </>
        ) : (
          <>
            <div className="modal-header">
              <div>
                <p className="modal-eyebrow">MedTrack payment</p>
                <h2 id="appointment-pay-title">{title}</h2>
              </div>
            </div>
            <p className="appointment-pay-amount">
              ₹{amount.toFixed(2)}
              {doctorName ? <span> · {doctorName}</span> : null}
            </p>
            {upiIntent && payment.upiId ? <UpiQr payload={upiIntent} size={240} /> : null}
            <p className="appointment-pay-id">UPI: {payment.upiId || "scan QR"}</p>
            {dummyUpi ? (
              <div className="msg error">
                PhonePe cannot verify this UPI ID. Set your real Google Pay / PhonePe ID (example
                98xxxxxxxx@ybl) in doctor bank details or MEDTRACK_UPI_ID, then open pay again.
              </div>
            ) : null}
            <p className="muted appointment-pay-hint">
              Scan with PhonePe or Google Pay. Pay in that app, then tap Confirm with bank.
            </p>
            {checkingBank ? (
              <p className="appointment-pay-bank" aria-live="polite">
                {checking ? "Confirming with the bank…" : "Checking with the bank…"}
              </p>
            ) : null}
            {error ? <div className="msg error">{error}</div> : null}
            <div className="appointment-pay-apps">
              <a className="secondary" href={`tez://upi/pay?${upiIntent.slice(upiIntent.indexOf("?") + 1)}`}>
                Google Pay
              </a>
              <a className="secondary" href={`phonepe://pay?${upiIntent.slice(upiIntent.indexOf("?") + 1)}`}>
                PhonePe
              </a>
            </div>
            <div className="modal-actions">
              {pending && payment.razorpayEnabled && payment.gatewayOrderId ? (
                <button type="button" onClick={() => void payWithRazorpay()} disabled={checking}>
                  {checking ? "Opening payment…" : "Pay now"}
                </button>
              ) : null}
              {pending ? (
                <button type="button" className="secondary" onClick={() => void checkNow()} disabled={checking}>
                  {checking ? "Confirming…" : "Confirm UPI payment"}
                </button>
              ) : null}
              <button type="button" className="secondary" onClick={onClose}>
                Pay later
              </button>
            </div>
          </>
        )}
      </div>
    </div>,
    document.body,
  );
}
