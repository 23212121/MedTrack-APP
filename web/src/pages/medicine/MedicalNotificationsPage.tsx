import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, type MedicineOrderNotification } from "../../api";
import { session } from "../../dl/MedTrackSession";

export default function MedicalNotificationsPage() {
  const [rows, setRows] = useState<MedicineOrderNotification[]>([]);
  const [error, setError] = useState("");
  const medical = session.isMedical();

  useEffect(() => {
    api
      .medicineOrderNotifications()
      .then((r) => setRows(r.notifications || []))
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load"));
  }, []);

  return (
    <section className="stack">
      <h1>Notifications</h1>
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
                    {n.orderId ? (
                      <Link
                        to={medical ? "/medical/orders" : "/medicine-orders"}
                        onClick={() => void api.markMedicineNotificationRead(n.id)}
                      >
                        {n.title}
                      </Link>
                    ) : (
                      n.title
                    )}
                    {!n.read && <span className="badge">New</span>}
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
