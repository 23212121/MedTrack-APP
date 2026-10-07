import { Link } from "react-router-dom";

export default function LabDashboardPage() {
  return (
    <section className="dashboard" aria-label="Lab technician dashboard">
      <h1>Lab technician</h1>
      <p className="lead">
        Search any patient by phone number or Aadhaar number (enter one), upload multiple test
        reports to S3, and the patient will see them on the Reports tab.
      </p>
      <div className="dashboard-fill">
        <div className="home-cards">
          <Link to="/lab/patients" className="home-card">
            <span className="home-card-group">Work</span>
            <h3>Patient reports</h3>
            <p>Enter mobile or Aadhaar; name, age, and gender fill from patient details</p>
          </Link>
          <Link to="/lab/uploads" className="home-card">
            <span className="home-card-group">History</span>
            <h3>Uploaded reports</h3>
            <p>Files this lab has already sent</p>
          </Link>
        </div>
      </div>
    </section>
  );
}
