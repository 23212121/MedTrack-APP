import { Link, useLocation } from "react-router-dom";
import { useT } from "../i18n";

/** Stylish catch-all 404 for unknown routes. */
export default function NotFoundPage() {
  const t = useT();
  const location = useLocation();

  return (
    <section className="error-stage" aria-labelledby="not-found-title">
      <div className="error-glow error-glow--rose" aria-hidden="true" />
      <div className="error-card">
        <p className="error-kicker">{t("Lost in the clinic")}</p>
        <p className="error-code" aria-hidden="true">
          404
        </p>
        <h1 id="not-found-title" className="error-title">
          {t("Page not found")}
        </h1>
        <p className="error-copy">
          {t("Nothing lives at")} <code className="error-path">{location.pathname}</code>.
          {t(" The corridor ends here — head back to a known desk.")}
        </p>

        <div className="error-actions">
          <Link to="/about" className="error-btn error-btn--primary">
            {t("About MedTrack")}
          </Link>
          <Link to="/" className="error-btn error-btn--ghost">
            {t("Go to dashboard")}
          </Link>
          <Link to="/login" className="error-btn error-btn--ghost">
            {t("Sign in")}
          </Link>
        </div>

        <p className="error-foot">{t("Error code: HTTP_404 · MedTrack Clinic")}</p>
      </div>
    </section>
  );
}
