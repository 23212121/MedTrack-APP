import { Link } from "react-router-dom";
import { getDoctorDashboardTiles } from "../nav";
import { session } from "../dl/MedTrackSession";

export default function DoctorPortalPage() {
  const tiles = getDoctorDashboardTiles();

  if (!session.isDoctor()) {
    return (
      <section className="dashboard" aria-label="Doctor dashboard">
        <div className="msg error">Sign in with a doctor User ID to open this portal.</div>
      </section>
    );
  }

  return (
    <section className="dashboard" aria-label="Doctor dashboard">
      <div className="dashboard-fill">
        <div className="home-cards">
          {tiles.map((tile) => (
            <Link key={tile.to} to={tile.to} className="home-card">
              <span className="home-card-group">{tile.group}</span>
              <h3>{tile.title}</h3>
              <p>{tile.text}</p>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}
