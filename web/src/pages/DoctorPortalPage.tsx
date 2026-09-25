import { Link } from "react-router-dom";
import { useT } from "../i18n";
import { getDoctorDashboardTiles } from "../nav";
import { session } from "../dl/MedTrackSession";

export default function DoctorPortalPage() {
  const t = useT();
  const tiles = getDoctorDashboardTiles();

  if (!session.isDoctor()) {
    return (
      <section className="dashboard" aria-label={t("Doctor dashboard")}>
        <div className="msg error">{t("Sign in with a doctor User ID to open this portal.")}</div>
      </section>
    );
  }

  return (
    <section className="dashboard" aria-label={t("Doctor dashboard")}>
      <div className="dashboard-fill">
        <div className="home-cards">
          {tiles.map((tile) => (
            <Link key={tile.to} to={tile.to} className="home-card">
              <span className="home-card-group">{t(tile.group)}</span>
              <h3>{t(tile.title)}</h3>
              <p>{t(tile.text)}</p>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}
