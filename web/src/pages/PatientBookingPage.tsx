import { Link, useParams, useSearchParams } from "react-router-dom";
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
            <p className="patient-booking-brand">MedTrack Clinic</p>
            <h1 id="patient-booking-title">Book an appointment</h1>
            <p className="lead patient-booking-lead">
              Choose a doctor and preferred time. No login required.
            </p>
          </div>
          <Link to="/login" className="patient-booking-signin">
            Staff sign in
          </Link>
        </header>
        <BookingsPage publicMode hospitalIdFilter={hospitalId || undefined} />
      </section>
    </div>
  );
}
