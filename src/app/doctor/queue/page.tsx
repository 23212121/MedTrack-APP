import { redirect } from "next/navigation";
import { UserRole } from "@prisma/client";
import { getSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import { DoctorQueueClient } from "../DoctorQueueClient";

export default async function DoctorQueuePage() {
  const session = await getSession();
  if (!session) redirect("/login");
  if (session.role !== UserRole.DOCTOR && session.role !== UserRole.ADMIN) {
    redirect("/");
  }

  const doctor =
    session.role === UserRole.DOCTOR
      ? await prisma.doctor.findFirst({ where: { userId: session.id } })
      : await prisma.doctor.findFirst({
          where: { clinicId: session.clinicId ?? undefined },
        });

  const visits = doctor
    ? await prisma.visit.findMany({
        where: {
          doctorId: doctor.id,
          status: {
            in: ["BOOKED", "CHECKED_IN", "IN_CONSULT", "CALLED"],
          },
        },
        orderBy: [
          { scheduledStart: "asc" },
          { tokenNumber: "asc" },
          { createdAt: "asc" },
        ],
        include: { patient: { select: { fullName: true, phone: true } } },
        take: 80,
      })
    : [];

  return (
    <div className="ddash-home">
      <header className="ddash-welcome">
        <p className="ddash-kicker">Live queue</p>
        <h1>Appointment queue</h1>
        <p className="ddash-tagline">
          Manage check-ins, consults, and patient bookings in real time.
        </p>
      </header>
      <DoctorQueueClient
        initialVisits={visits.map((v) => ({
          id: v.id,
          status: v.status,
          tokenNumber: v.tokenNumber,
          scheduledStart: v.scheduledStart?.toISOString() ?? null,
          delayMinutes: v.delayMinutes,
          reason: v.reason,
          patient: v.patient,
          createdByUserId: v.createdByUserId,
          isPatientBooking: v.createdByUserId == null,
        }))}
      />
    </div>
  );
}
