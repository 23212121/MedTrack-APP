import { redirect } from "next/navigation";
import { getSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";

export default async function DoctorSuggestionsPage() {
  const session = await getSession();
  if (!session) redirect("/login");

  const doctor =
    (await prisma.doctor.findFirst({ where: { userId: session.id } })) ||
    (session.clinicId
      ? await prisma.doctor.findFirst({ where: { clinicId: session.clinicId } })
      : null);

  const pending = doctor
    ? await prisma.visit.findMany({
        where: {
          doctorId: doctor.id,
          status: { in: ["BOOKED", "CHECKED_IN", "CALLED"] },
        },
        orderBy: [{ scheduledStart: "asc" }, { createdAt: "asc" }],
        take: 40,
        include: { patient: { select: { fullName: true, phone: true } } },
      })
    : [];

  const suggestions = pending.map((v, i) => ({
    id: v.id,
    title:
      v.status === "BOOKED"
        ? "Review new booking"
        : v.status === "CHECKED_IN"
          ? "Patient waiting — start consult"
          : "Patient called — ready for room",
    detail: `${v.patient.fullName} · ${v.reason || "Consultation"}${
      v.tokenNumber ? ` · Token #${v.tokenNumber}` : ""
    }`,
    priority: i < 3 ? "High" : "Normal",
  }));

  return (
    <div className="ddash-home">
      <header className="ddash-welcome">
        <p className="ddash-kicker">Suggestions</p>
        <h1>Care suggestions</h1>
        <p className="ddash-tagline">
          Recommended actions based on your current queue and appointments.
        </p>
      </header>

      <section className="ddash-panel">
        {suggestions.length === 0 ? (
          <p className="ddash-empty">
            No pending suggestions. Your queue is clear.
          </p>
        ) : (
          <ul className="ddash-suggestions">
            {suggestions.map((s) => (
              <li key={s.id}>
                <div>
                  <strong>{s.title}</strong>
                  <span>{s.detail}</span>
                </div>
                <em className={s.priority === "High" ? "high" : undefined}>
                  {s.priority}
                </em>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
