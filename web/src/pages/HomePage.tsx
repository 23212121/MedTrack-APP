import { Link } from "react-router-dom";
import { useT } from "../i18n";
import { getDashboardTiles } from "../nav";

export default function HomePage() {
  const t = useT();
  const tiles = getDashboardTiles();

  return (
    <section className="dashboard" aria-label={t("Dashboard")}>
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
