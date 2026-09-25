/** Clinical departments used for hospital setup and doctor onboarding. */
export const CLINICAL_DEPARTMENTS = [
  "Cardiology",
  "Neurology",
  "Orthopedics",
  "Pediatrics",
  "Gynecology",
  "Dermatology",
  "Oncology",
  "Emergency",
  "ICU",
  "Radiology",
  "Laboratory",
  "ENT",
  "Ophthalmology",
  "Psychiatry",
  "General Medicine",
  "General Surgery",
  "Urology",
  "Nephrology",
  "Gastroenterology",
  "Pulmonology",
] as const;

export function mergeDepartmentOptions(...lists: Array<Iterable<string> | undefined>) {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const list of lists) {
    if (!list) continue;
    for (const raw of list) {
      const name = String(raw || "").trim();
      if (!name) continue;
      const key = name.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      out.push(name);
    }
  }
  return out;
}
