import { useEffect } from "react";
import { Link } from "react-router-dom";
import { session } from "../dl/MedTrackSession";

/**
 * Stylish logout screen styled as a 404 "session not found" moment.
 * Clears local clinic session markers and offers a way back in.
 */
export default function LogoutPage() {
  useEffect(() => {
    session.logout();
  }, []);

  return (
    <section className="error-stage" aria-labelledby="logout-404-title">
      <div className="error-glow" aria-hidden="true" />
      <div className="error-card">
        <p className="error-kicker">Signed out</p>
        <p className="error-code" aria-hidden="true">
          404
        </p>
        <h1 id="logout-404-title" className="error-title">
          Session not found
        </h1>
        <p className="error-copy">
          You have left MedTrack Clinic. Your session is gone — like a page that
          never existed — so charts, queues, and bookings stay protected until
          you sign in again.
        </p>

        <div className="error-actions">
          <Link to="/login" className="error-btn error-btn--primary">
            Sign in again
          </Link>
        </div>

        <p className="error-foot">Error code: LOGOUT_404 · MedTrack Clinic</p>
      </div>
    </section>
  );
}
