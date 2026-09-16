import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, type MedicineOrderCounts } from "../../api";

const TILES: { to: string; key: keyof MedicineOrderCounts; title: string; group: string }[] = [
  { to: "/medical/orders/check", key: "newOrders", title: "Check order", group: "Inbox" },
  { to: "/medical/orders/new", key: "pending", title: "New orders", group: "Inbox" },
  { to: "/medical/orders/in-process", key: "inProcess", title: "In process", group: "Work" },
  { to: "/medical/orders/waiting", key: "waitingApproval", title: "Waiting approval", group: "Work" },
  { to: "/medical/orders/ready", key: "medicineReady", title: "Medicine ready", group: "Work" },
  { to: "/medical/orders/completed", key: "completed", title: "Completed", group: "History" },
  { to: "/medical/orders/canceled", key: "canceled", title: "Canceled", group: "History" },
];

export default function MedicalDashboardPage() {
  const [counts, setCounts] = useState<MedicineOrderCounts | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api
      .medicineOrderCounts()
      .then(setCounts)
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load counts"));
  }, []);

  return (
    <section className="dashboard" aria-label="Medical store dashboard">
      <h1>Medical store</h1>
      <p className="lead">Accept prescriptions, send amounts, and complete medicine orders.</p>
      {error && <div className="msg error">{error}</div>}
      <div className="dashboard-fill">
        <div className="home-cards">
          {TILES.map((tile) => (
            <Link key={tile.to} to={tile.to} className="home-card">
              <span className="home-card-group">{tile.group}</span>
              <h3>
                {tile.title} ({counts ? counts[tile.key] : "…"})
              </h3>
              <p>Open this queue</p>
            </Link>
          ))}
          <Link to="/medical/notifications" className="home-card">
            <span className="home-card-group">Alerts</span>
            <h3>Notifications ({counts?.unreadNotifications ?? "…"})</h3>
            <p>Unread order alerts</p>
          </Link>
        </div>
      </div>
    </section>
  );
}
