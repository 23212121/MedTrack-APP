import { useState } from "react";
import { api, AppointmentPayment, BookingSummary } from "../api";
import AppointmentPaymentModal from "./AppointmentPaymentModal";
import { toast } from "../toast";

export function appointmentPaymentDue(
  appt: Pick<BookingSummary, "paymentStatus" | "consultationFee">,
): boolean {
  const status = (appt.paymentStatus || "UNPAID").toUpperCase();
  if (status === "PAID" || status === "COMPLETED") return false;
  const fee = Number(appt.consultationFee);
  return Number.isFinite(fee) && fee >= 1;
}

function paymentStatusLabel(appt: BookingSummary): string {
  const status = (appt.paymentStatus || "UNPAID").toUpperCase();
  if (status === "PAID" || status === "COMPLETED") return "Paid";
  if (status === "PAYMENT_PENDING") return "Payment pending";
  return "Unpaid";
}

type Props = {
  appt: BookingSummary;
  onUpdated?: () => void;
  compact?: boolean;
};

export default function PatientPayConsultationButton({
  appt,
  onUpdated,
  compact,
}: Props) {
  const [payModal, setPayModal] = useState<AppointmentPayment | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");

  const due = appointmentPaymentDue(appt);

  async function openPay() {
    const appointmentId = appt.appointmentId;
    if (!appointmentId) {
      setErr("This booking is not linked to payment yet. Refresh the page and try again.");
      return;
    }
    setBusy(true);
    setErr("");
    try {
      const payment = appt.paymentId
        ? await api.getPayment(appt.paymentId)
        : await api.createPayment({
            appointmentId,
            patientId: appt.patientId,
            amount: appt.consultationFee,
          });
      setPayModal(payment);
    } catch (e) {
      setErr(e instanceof Error ? e.message : "Could not open payment");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      {err ? <span className="msg error">{err}</span> : null}
      {due ? (
        <button type="button" className={compact ? "secondary" : undefined} onClick={() => void openPay()} disabled={busy}>
          {busy ? "Loading…" : "Pay now"}
        </button>
      ) : (
        <span className="muted">{paymentStatusLabel(appt)}</span>
      )}
      {payModal ? (
        <AppointmentPaymentModal
          payment={payModal}
          doctorName={appt.doctorName}
          onPaid={(paid) => {
            setPayModal(null);
            toast.success(
              `Payment successful. Transaction ${paid.gatewayTransactionId || paid.paymentId}.`,
            );
            onUpdated?.();
          }}
          onClose={() => setPayModal(null)}
        />
      ) : null}
    </>
  );
}
