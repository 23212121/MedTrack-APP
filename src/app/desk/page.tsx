import { redirect } from "next/navigation";
import { UserRole, VisitStatus } from "@prisma/client";
import { getSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import { LogoutButton } from "@/components/LogoutButton";
import { DeskClient } from "./DeskClient";

export default async function DeskPage() {
  const session = await getSession();
  if (!session) redirect("/login");
  if (
    session.role !== UserRole.EMPLOYEE &&
    session.role !== UserRole.ADMIN &&
    session.role !== UserRole.DOCTOR
  ) {
    redirect("/");
  }

  const lockedDoctor =
    session.role === UserRole.DOCTOR
      ? await prisma.doctor.findFirst({ where: { userId: session.id } })
      : null;

  const clinicId = lockedDoctor?.clinicId ?? session.clinicId;
  if (!clinicId) redirect("/login");

  const openStatuses: VisitStatus[] = [
    VisitStatus.CALLED,
    VisitStatus.BOOKED,
    VisitStatus.CHECKED_IN,
    VisitStatus.IN_CONSULT,
  ];

  const visits = await prisma.visit.findMany({
    where: {
      clinicId,
      status: { in: openStatuses },
      ...(lockedDoctor ? { doctorId: lockedDoctor.id } : {}),
    },
    orderBy: [
      { scheduledStart: "asc" },
      { tokenNumber: "asc" },
      { createdAt: "asc" },
    ],
    take: 80,
    include: {
      patient: { select: { fullName: true, phone: true } },
      doctor: { include: { user: { select: { fullName: true } } } },
      events: { orderBy: { createdAt: "asc" }, take: 10 },
    },
  });

  const doctors = await prisma.doctor.findMany({
    where: { clinicId, isActive: true },
    include: {
      user: { select: { fullName: true } },
      department: { select: { id: true, name: true } },
    },
    orderBy: { user: { fullName: "asc" } },
  });

  return (
    <main>
      <div className="topbar">
        <div>
          <h1 className="hero-brand" style={{ fontSize: "1.8rem" }}>
            {session.role === UserRole.DOCTOR ? "Doctor desk" : "MedTrack Desk"}
          </h1>
          <p className="muted" style={{ margin: 0 }}>
            {session.fullName} · {session.role}
            {lockedDoctor ? " · your appointments" : ""}
          </p>
        </div>
        <LogoutButton />
      </div>
      <DeskClient
        role={session.role}
        lockedDoctorId={lockedDoctor?.id ?? null}
        initialDoctors={doctors.map((d) => ({
          id: d.id,
          specialty: d.specialty,
          departmentId: d.departmentId,
          user: d.user,
          department: d.department,
        }))}
        initialBoard={visits.map((v) => ({
          id: v.id,
          status: v.status,
          reason: v.reason,
          tokenNumber: v.tokenNumber,
          delayMinutes: v.delayMinutes,
          scheduledStart: v.scheduledStart?.toISOString() ?? null,
          createdByUserId: v.createdByUserId,
          isPatientBooking: v.createdByUserId == null,
          patient: v.patient,
          doctor: v.doctor,
          events: v.events.map((e) => ({
            id: e.id,
            eventType: e.eventType,
            message: e.message,
            createdAt: e.createdAt.toISOString(),
            toStatus: e.toStatus,
          })),
        }))}
      />
    </main>
  );
}
