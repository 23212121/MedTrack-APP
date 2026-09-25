import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { api, AppointmentPayment } from "../api";

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
  const onPaidRef = useRef(onPaid);
  onPaidRef.current = onPaid;

  useEffect(() => {
    setPayment(initial);
  }, [initial]);

  useEffect(() => {
    const id = payment.paymentId;
    if (!id) return;
    let ticks = 0;
    const timer = window.setInterval(async () => {
      ticks += 1;
      try {
        const latest =
          ticks % 4 === 0
            ? await api.verifyPaymentWithGateway(id)
            : await api.getPayment(id);
        setPayment(latest);
        if (latest.status === "COMPLETED") {
          window.clearInterval(timer);
          onPaidRef.current(latest);
        }
      } catch {
        /* keep waiting — frontend never marks paid on its own */
      }
    }, 3000);
    return () => window.clearInterval(timer);
  }, [payment.paymentId]);

  const amount = payment.amount ?? 0;
  const pending =
    payment.status === "CREATED" || payment.status === "PAYMENT_PENDING";

  async function checkNow() {
    setChecking(true);
    try {
      const latest = await api.verifyPaymentWithGateway(payment.paymentId);
      setPayment(latest);
      if (latest.status === "COMPLETED") onPaid(latest);
    } catch {
      const latest = await api.getPayment(payment.paymentId);
      setPayment(latest);
      if (latest.status === "COMPLETED") onPaid(latest);
    } finally {
      setChecking(false);
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
        {payment.qrImageUrl ? (
          <img
            className="appointment-pay-qr"
            src={payment.qrImageUrl}
            alt="Scan to pay"
            width={240}
            height={240}
          />
        ) : null}
        <p className="appointment-pay-id">Payment ID: {payment.paymentId}</p>
        <p className="muted appointment-pay-hint">
          Scan this QR with PhonePe, Google Pay, Paytm or any UPI app. Status stays{" "}
          <strong>{payment.status}</strong> until the payment provider confirms — scanning
          does not complete payment.
        </p>
        {payment.upiApps && payment.upiApps.length > 0 ? (
          <div className="appointment-pay-apps">
            {payment.upiApps.map((app) => (
              <a key={app.id} className="secondary" href={app.uri}>
                {app.name}
              </a>
            ))}
          </div>
        ) : null}
        <div className="modal-actions">
          {pending ? (
            <button type="button" onClick={() => void checkNow()} disabled={checking}>
              {checking ? "Checking…" : "I have paid — check status"}
            </button>
          ) : null}
          <button type="button" className="secondary" onClick={onClose}>
            Pay later
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
