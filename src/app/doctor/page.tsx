import { redirect } from "next/navigation";

/** Doctor root → landing page after login. */
export default function DoctorIndexPage() {
  redirect("/doctor/home");
}
