import { Link } from "react-router-dom";
import { getDashboardTiles } from "../nav";

export default function HomePage() {
  const tiles = getDashboardTiles();

  return (
    <section className="dashboard" aria-label="Dashboard">
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
