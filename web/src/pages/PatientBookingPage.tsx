import { Link, useParams, useSearchParams } from "react-router-dom";
import { popupClosePath } from "../auth";
import { useT } from "../i18n";
import BookingsPage from "./BookingsPage";

/**
 * Public patient booking entry (no login).
 * Share links:
 *   /book
 *   /book?hospitalId=10001
 *   /book/10001
 *
 * Hospital dropdown is optional — patient may pick a doctor directly.
 */
export default function PatientBookingPage() {
  const t = useT();
  const { hospitalId: pathHospitalId } = useParams();
  const [params] = useSearchParams();
  const hospitalId =
    pathHospitalId ||
    params.get("hospitalId") ||
    params.get("h") ||
    "";

  return (
    <div className="patient-booking-backdrop" role="dialog" aria-modal="true" aria-labelledby="patient-booking-title">
      <section className="patient-booking-modal panel">
        <header className="patient-booking-head">
          <div>
            <p className="patient-booking-brand">{t("MedTrack Clinic")}</p>
            <h1 id="patient-booking-title">{t("Book an appointment")}</h1>
            <p className="lead patient-booking-lead">
              {t(
                "Filter by hospital or department, then choose a doctor and time. No login required.",
              )}
            </p>
          </div>
          <Link to={popupClosePath()} className="patient-booking-signin" aria-label={t("Close")}>
            ×
          </Link>
        </header>
        <BookingsPage publicMode hospitalIdFilter={hospitalId || undefined} />
      </section>
    </div>
  );
}
