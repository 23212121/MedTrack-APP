import { redirect } from "next/navigation";
import { getSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";

function ageFromDob(dob: Date | null | undefined): string {
  if (!dob) return "—";
  const now = new Date();
  let age = now.getFullYear() - dob.getFullYear();
  const m = now.getMonth() - dob.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < dob.getDate())) age -= 1;
  return String(age);
}

export default async function DoctorPatientsPage() {
  const session = await getSession();
  if (!session) redirect("/login");

  const doctor =
    (await prisma.doctor.findFirst({ where: { userId: session.id } })) ||
    (session.clinicId
      ? await prisma.doctor.findFirst({ where: { clinicId: session.clinicId } })
      : null);

  const visits = doctor
    ? await prisma.visit.findMany({
        where: { doctorId: doctor.id },
        orderBy: { updatedAt: "desc" },
        take: 100,
        include: {
          patient: {
            select: {
              id: true,
              fullName: true,
              phone: true,
              dateOfBirth: true,
              gender: true,
            },
          },
        },
      })
    : [];

  const byPatient = new Map<
    string,
    {
      name: string;
      phone: string;
      age: string;
      gender: string;
      lastReason: string;
      lastStatus: string;
    }
  >();

  for (const v of visits) {
    if (byPatient.has(v.patient.id)) continue;
    byPatient.set(v.patient.id, {
      name: v.patient.fullName,
      phone: v.patient.phone,
      age: ageFromDob(v.patient.dateOfBirth),
      gender: v.patient.gender,
      lastReason: v.reason || "General",
      lastStatus: v.status,
    });
  }

  const patients = [...byPatient.values()];

  return (
    <div className="ddash-home">
      <header className="ddash-welcome">
        <p className="ddash-kicker">Patients</p>
        <h1>Patient list</h1>
        <p className="ddash-tagline">
          Patients linked to your appointments and visits.
        </p>
      </header>

      <section className="ddash-panel">
        {patients.length === 0 ? (
          <p className="ddash-empty">No patients found for this doctor yet.</p>
        ) : (
          <div className="ddash-table-wrap">
            <table className="ddash-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Age</th>
                  <th>Gender</th>
                  <th>Phone</th>
                  <th>Last visit reason</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {patients.map((p) => (
                  <tr key={`${p.phone}-${p.name}`}>
                    <td>{p.name}</td>
                    <td>{p.age}</td>
                    <td>{p.gender}</td>
                    <td>{p.phone}</td>
                    <td>{p.lastReason}</td>
                    <td>
                      <span className="ddash-pill">{p.lastStatus}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
