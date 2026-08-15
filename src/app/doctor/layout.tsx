import { redirect } from "next/navigation";
import { UserRole } from "@prisma/client";
import { getSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import { DoctorPortalShell } from "@/components/doctor/DoctorPortalShell";

export default async function DoctorLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const session = await getSession();
  if (!session) redirect("/login");
  if (session.role !== UserRole.DOCTOR && session.role !== UserRole.ADMIN) {
    redirect("/");
  }

  const clinic = session.clinicId
    ? await prisma.clinic.findUnique({
        where: { id: session.clinicId },
        select: { name: true },
      })
    : null;

  return (
    <DoctorPortalShell
      doctorName={session.fullName || "Doctor"}
      clinicName={clinic?.name || "MedTrack Clinic"}
    >
      {children}
    </DoctorPortalShell>
  );
}
