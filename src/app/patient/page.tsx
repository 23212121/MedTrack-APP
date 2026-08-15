import { redirect } from "next/navigation";
import { UserRole } from "@prisma/client";
import { getSession } from "@/lib/auth";
import { LogoutButton } from "@/components/LogoutButton";
import { PatientStatusClient } from "./PatientStatusClient";

export default async function PatientPage() {
  const session = await getSession();
  if (!session) redirect("/login");
  if (session.role !== UserRole.PATIENT) redirect("/");

  return (
    <main>
      <div className="topbar">
        <div>
          <h1 className="hero-brand" style={{ fontSize: "1.8rem" }}>
            My visit
          </h1>
          <p className="muted" style={{ margin: 0 }}>
            {session.fullName} · live status · SMS + email + app alerts
          </p>
        </div>
        <LogoutButton />
      </div>
      <PatientStatusClient />
    </main>
  );
}
