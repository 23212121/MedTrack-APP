import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, type MedicineOrderNotification, type NotificationRow } from "../../api";
import { session } from "../../dl/MedTrackSession";

type UnifiedRow = {
  id: string;
  title: string;
  message: string;
  createdAt?: string;
  orderId?: string;
  medicineNotificationId?: string;
  read?: boolean;
  href?: string;
};

function eventTitle(code: string, subject?: string) {
  switch (code) {
    case "DOCUMENT_ATTACHED":
      return "Document attached";
    case "BOOKING_CONFIRMED":
      return "Appointment booked";
    case "PAYMENT_COMPLETED":
      return "Payment successful";
    case "CHECKED_IN":
      return "Checked in";
    case "CHECKUP_STARTED":
      return "Checkup started";
    case "YOU_ARE_NEXT":
      return "You're next";
    case "VISIT_COMPLETED":
      return "Visit completed";
    default:
      return subject || code;
  }
}

function patientHref(code: string) {
  if (code === "DOCUMENT_ATTACHED") return "/patient/reports";
  if (code === "PAYMENT_COMPLETED") return "/patient/booking";
  return "/patient/status";
}

export default function MedicalNotificationsPage() {
  const [rows, setRows] = useState<UnifiedRow[]>([]);
  const [error, setError] = useState("");
  const medical = session.isMedical();
  const patient = session.isPatient();

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setError("");
      try {
        const [med, appt] = await Promise.all([
          medical || patient
            ? api.medicineOrderNotifications().catch(() => ({ notifications: [] as MedicineOrderNotification[] }))
            : Promise.resolve({ notifications: [] as MedicineOrderNotification[] }),
          patient
            ? api.myNotifications().catch(() => ({ notifications: [] as NotificationRow[] }))
            : Promise.resolve({ notifications: [] as NotificationRow[] }),
        ]);
        if (cancelled) return;
        const medRows: UnifiedRow[] = (med.notifications || []).map((n) => ({
          id: `med-${n.id}`,
          title: n.title,
          message: n.message,
          createdAt: n.createdAt,
          orderId: n.orderId,
          medicineNotificationId: n.id,
          read: n.read,
        }));
        const apptRows: UnifiedRow[] = (appt.notifications || [])
          .filter((n) => n.channel === "APP")
          .map((n) => ({
            id: `app-${n.id}`,
            title: eventTitle(n.eventCode, n.subject),
            message: n.body,
            createdAt: n.createdAt,
            href: patientHref(n.eventCode),
          }));
        const merged = [...medRows, ...apptRows].sort((a, b) =>
          String(b.createdAt || "").localeCompare(String(a.createdAt || "")),
        );
        setRows(merged);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : "Failed to load");
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [medical, patient]);

  return (
    <section className="stack">
      <h1>Notifications</h1>
      <p className="lead">
        {patient
          ? "Appointment, document, and pharmacy updates — the same alerts sent by SMS and email."
          : "Order quotes and status updates from the medical store."}
      </p>
      {error && <div className="msg error">{error}</div>}
      <div className="table-scroll panel">
        <table className="table">
          <thead>
            <tr>
              <th>Title</th>
              <th>Message</th>
              <th>When</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={3} className="muted">
                  No notifications
                </td>
              </tr>
            ) : (
              rows.map((n) => (
                <tr key={n.id}>
                  <td>
                    {n.orderId && n.medicineNotificationId ? (
                      <Link
                        to={
                          medical
                            ? "/medical/orders"
                            : session.isPatient()
                              ? "/patient/medicine-orders"
                              : "/medicine-orders"
                        }
                        onClick={() => void api.markMedicineNotificationRead(n.medicineNotificationId!)}
                      >
                        {n.title}
                      </Link>
                    ) : n.href ? (
                      <Link to={n.href}>{n.title}</Link>
                    ) : (
                      n.title
                    )}
                    {n.read === false && <span className="badge">New</span>}
                  </td>
                  <td>{n.message}</td>
                  <td>{n.createdAt ? new Date(n.createdAt).toLocaleString() : ""}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
