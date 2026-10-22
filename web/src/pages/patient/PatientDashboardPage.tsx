import { Link } from "react-router-dom";

const PORTAL_TILES = [
  {
    to: "/patient/emergency",
    group: "Emergency",
    title: "Check Emergency Service",
    text: "Search hospitals, beds, and book an emergency bed",
  },
  {
    to: "/patient/ambulance",
    group: "Emergency",
    title: "Request ambulance",
    text: "Share your live location and alert the nearest available ambulance",
  },
  {
    to: "/patient/booking",
    group: "Appointments",
    title: "Booking",
    text: "Book, view, reschedule, or cancel appointments",
  },
  {
    to: "/patient/status",
    group: "Queue",
    title: "Check Status",
    text: "Live queue, your token, and wait time",
  },
  {
    to: "/patient/chat",
    group: "Support",
    title: "Chat",
    text: "Message the hospital or doctor",
  },
  {
    to: "/patient/reports",
    group: "Records",
    title: "Reports",
    text: "Lab reports, prescriptions, and downloads",
  },
  {
    to: "/patient/medicine-orders",
    group: "Pharmacy",
    title: "Medicine orders",
    text: "Upload a prescription and track the pharmacy quote",
  },
  {
    to: "/patient/lab-booking",
    group: "Lab",
    title: "Lab booking",
    text: "Book home sample collection or visit the lab with token time",
  },
  {
    to: "/patient/notifications",
    group: "Alerts",
    title: "Notifications",
    text: "Appointment, document, and pharmacy updates",
  },
] as const;

export default function PatientDashboardPage() {
  return (
    <div className="patient-portal-stack">
      <section className="dashboard" aria-label="Patient portal tiles">
        <div className="dashboard-fill">
          <div className="home-cards patient-portal-tiles">
            {PORTAL_TILES.map((tile) => (
              <Link key={tile.to} to={tile.to} className="home-card patient-portal-tile">
                <span className="home-card-group">{tile.group}</span>
                <h3>{tile.title}</h3>
                <p>{tile.text}</p>
              </Link>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
