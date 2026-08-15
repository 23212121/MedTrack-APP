import { redirect } from "next/navigation";
import { getSession } from "@/lib/auth";
import { UserRole } from "@prisma/client";

export default async function HomePage() {
  const session = await getSession();
  if (!session) redirect("/login");

  switch (session.role) {
    case UserRole.EMPLOYEE:
    case UserRole.ADMIN:
      redirect("/desk");
    case UserRole.DOCTOR:
      redirect("/doctor/home");
    case UserRole.PATIENT:
      redirect("/patient");
    default:
      redirect("/login");
  }
}
