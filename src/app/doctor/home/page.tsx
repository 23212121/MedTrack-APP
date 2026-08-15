import Link from "next/link";
import { redirect } from "next/navigation";
import { getSession } from "@/lib/auth";
import { loadDoctorDashboard } from "@/lib/doctor-dashboard";

export default async function DoctorLandingPage() {
  const session = await getSession();
  if (!session) redirect("/login");

  const data = await loadDoctorDashboard(session.id, session.clinicId);
  const displayName = data.doctorName.startsWith("Dr")
    ? data.doctorName
    : `Dr. ${data.doctorName}`;

  return (
    <div className="ddash-home">
      <header className="ddash-welcome">
        <p className="ddash-kicker">Landing page</p>
        <h1>Welcome Back, {displayName}</h1>
        <p className="ddash-tagline">Delivering Better Care Every Day</p>
      </header>

      <div className="ddash-stats">
        <article className="ddash-stat">
          <span>Total Patients</span>
          <strong>{data.totalPatients}</strong>
        </article>
        <article className="ddash-stat accent">
          <span>Today&apos;s Appointments</span>
          <strong>{data.todayAppointments}</strong>
        </article>
        <article className="ddash-stat warn">
          <span>Pending Suggestions</span>
          <strong>{data.pendingSuggestions}</strong>
        </article>
      </div>

      <div className="ddash-panels">
        <section className="ddash-panel">
          <div className="ddash-panel-head">
            <h2>Upcoming Appointments</h2>
            <Link href="/doctor/queue">View queue</Link>
          </div>
          {data.upcoming.length === 0 ? (
            <p className="ddash-empty">No upcoming appointments right now.</p>
          ) : (
            <ul className="ddash-appointments">
              {data.upcoming.map((row, i) => (
                <li key={`${row.name}-${i}`}>
                  <time>{row.time}</time>
                  <div>
                    <strong>{row.name}</strong>
                    <span>{row.reason}</span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="ddash-panel">
          <div className="ddash-panel-head">
            <h2>Recent Patients</h2>
            <Link href="/doctor/patients">Patient list</Link>
          </div>
          {data.recent.length === 0 ? (
            <p className="ddash-empty">No recent patient visits yet.</p>
          ) : (
            <div className="ddash-table-wrap">
              <table className="ddash-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Age</th>
                    <th>Disease</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {data.recent.map((row, i) => (
                    <tr key={`${row.name}-${i}`}>
                      <td>{row.name}</td>
                      <td>{row.age}</td>
                      <td>{row.disease}</td>
                      <td>
                        <span
                          className={`ddash-pill ${row.status
                            .toLowerCase()
                            .replace(/\s+/g, "-")}`}
                        >
                          {row.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
