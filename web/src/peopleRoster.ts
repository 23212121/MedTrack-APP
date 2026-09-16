import { api, HospitalDoctor, SchedulePerson, StaffMember } from "./api";
import { session } from "./dl/MedTrackSession";

export function buildPeople(doctors: HospitalDoctor[], staff: StaffMember[]): SchedulePerson[] {
  const people: SchedulePerson[] = [];
  const seen = new Set<string>();

  for (const d of doctors) {
    if (!d.doctorId || seen.has(d.doctorId)) continue;
    seen.add(d.doctorId);
    people.push({
      id: d.doctorId,
      name: d.doctorName || [d.firstName, d.lastName].filter(Boolean).join(" ") || d.doctorId,
      role: "Doctor",
      typeLabel: d.specialization?.trim() || d.department?.trim() || "Doctor",
    });
  }

  for (const s of staff) {
    const isDoctor = (s.employeeType || "").toLowerCase() === "doctor";
    const id = (isDoctor && s.doctorLinkId) || s.employeeId;
    if (!id || seen.has(id)) continue;
    seen.add(id);
    people.push({
      id,
      name: s.name || [s.firstName, s.lastName].filter(Boolean).join(" ") || id,
      role: isDoctor ? "Doctor" : "Staff",
      typeLabel: s.designation?.trim() || s.employeeType?.trim() || s.department?.trim() || "Staff",
    });
  }

  return people.sort((a, b) => {
    if (a.role !== b.role) return a.role === "Doctor" ? -1 : 1;
    return a.name.localeCompare(b.name);
  });
}

export async function loadHospitalPeople(): Promise<SchedulePerson[]> {
  const hospitalId = session.getHospitalId();
  const [doctorData, staffData] = await Promise.all([
    hospitalId
      ? api.doctorsByHospital(hospitalId).catch(() => api.doctors())
      : api.doctors(),
    api.employees().catch(() => ({ employees: [] as StaffMember[] })),
  ]);
  const doctors = ((doctorData as { doctors?: HospitalDoctor[] }).doctors ?? []).filter(
    (d) => d.doctorId,
  ) as HospitalDoctor[];
  return buildPeople(doctors, staffData.employees ?? []);
}
