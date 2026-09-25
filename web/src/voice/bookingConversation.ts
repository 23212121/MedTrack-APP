import type { ParsedVoiceCommand, VoiceField } from "./voiceCommandParser";
import { normalizeGender } from "./voiceCommandParser";

export type VoiceDraft = {
  patientName: string;
  patientPhone: string;
  patientEmail: string;
  patientAge: string;
  gender: string;
  address: string;
  reason: string;
  doctorId: string;
  doctorName: string;
  appointmentDate: string;
  appointmentTime: string;
};

export type VoiceDoctor = {
  doctorId: string;
  doctorName: string;
  department?: string;
  specialization?: string;
};

export type ConversationField = VoiceField | "confirm" | "idle" | "showDoctor" | "showDate" | "showDateTo";

export type PatientSearchHit = {
  id?: string;
  name?: string;
  phone?: string;
  age?: number | null;
  gender?: string;
  email?: string;
  address?: string;
};

export function nextMissingField(
  draft: VoiceDraft,
  publicMode: boolean,
): Exclude<ConversationField, "idle"> {
  if (!draft.patientName.trim()) return "patientName";
  if (!draft.patientPhone.trim()) return "phone";
  if (!draft.patientAge.trim()) return "age";
  if (!draft.gender.trim()) return "gender";
  if (publicMode && !draft.patientEmail.trim()) return "email";
  if (!draft.doctorId) return "doctor";
  if (!draft.appointmentDate) return "date";
  if (!draft.appointmentTime) return "time";
  return "confirm";
}

export function promptFor(field: ConversationField): string {
  switch (field) {
    case "patientName":
      return "Please say the patient name, for example: Book appointment.";
    case "phone":
      return "Phone number is missing. Please speak the phone number.";
    case "age":
      return "Please provide age.";
    case "gender":
      return "Please provide gender: male, female, or other.";
    case "email":
      return "Please speak the email address for booking confirmation.";
    case "doctor":
      return "Please say the doctor name.";
    case "date":
      return "Please say the appointment date, for example tomorrow or 18 September.";
    case "time":
      return "Please say the appointment time, for example 10 AM.";
    case "address":
      return "Please speak the address, or say skip.";
    case "reason":
      return "Please speak the reason, or say skip.";
    case "confirm":
      return "All required information is available. Say book appointment to continue.";
    case "showDoctor":
      return "Which doctor's bookings should I show?";
    case "showDate":
      return "For which date? You can say today, or a range from 18 September to 20 September.";
    case "showDateTo":
      return "Until which date?";
    default:
      return "Tap Speak to Book, then say show booking, or book appointment for a patient.";
  }
}

export function matchDoctor(query: string, doctors: VoiceDoctor[]): VoiceDoctor | undefined {
  const q = query
    .toLowerCase()
    .replace(/^dr\.?\s*/, "")
    .replace(/^doctor\s+/, "")
    .trim();
  if (!q) return undefined;
  const scored = doctors
    .map((d) => {
      const name = (d.doctorName || "").toLowerCase().replace(/^dr\.?\s*/, "");
      const dept = (d.department || d.specialization || "").toLowerCase();
      let score = 0;
      if (name === q) score = 100;
      else if (name.includes(q) || q.includes(name)) score = 80;
      else if (name.split(/\s+/).some((part) => part && q.includes(part))) score = 50;
      else if (dept && q.includes(dept)) score = 20;
      return { d, score };
    })
    .filter((x) => x.score > 0)
    .sort((a, b) => b.score - a.score);
  return scored[0]?.d;
}

export function patientToPatch(hit: PatientSearchHit, draft: VoiceDraft): Partial<VoiceDraft> {
  const patch: Partial<VoiceDraft> = {};
  if (!draft.patientName.trim() && hit.name) patch.patientName = hit.name;
  else if (hit.name) patch.patientName = hit.name;
  if (!draft.patientPhone.trim() && hit.phone) patch.patientPhone = String(hit.phone);
  if (!draft.patientAge.trim() && hit.age != null) patch.patientAge = String(hit.age);
  const gender = hit.gender ? normalizeGender(String(hit.gender)) || String(hit.gender).toUpperCase() : "";
  if (!draft.gender.trim() && gender) {
    patch.gender = ["MALE", "FEMALE", "OTHER", "UNKNOWN"].includes(gender)
      ? gender
      : "";
  }
  if (!draft.patientEmail.trim() && hit.email) patch.patientEmail = hit.email;
  if (!draft.address.trim() && hit.address) patch.address = hit.address;
  return patch;
}

export function applyParsedCommand(
  parsed: ParsedVoiceCommand,
  doctors: VoiceDoctor[],
): { patch: Partial<VoiceDraft>; doctorId?: string; message?: string } {
  const patch: Partial<VoiceDraft> = {};
  if (parsed.patientName) patch.patientName = parsed.patientName;
  if (parsed.phone) patch.patientPhone = parsed.phone;
  if (parsed.age) patch.patientAge = parsed.age;
  if (parsed.gender) patch.gender = parsed.gender;
  if (parsed.email) patch.patientEmail = parsed.email;
  if (parsed.date) patch.appointmentDate = parsed.date;
  if (parsed.time) patch.appointmentTime = parsed.time;
  if (parsed.address) patch.address = parsed.address;
  if (parsed.reason) patch.reason = parsed.reason;

  let doctorId: string | undefined;
  let message: string | undefined;
  if (parsed.doctorQuery) {
    const found = matchDoctor(parsed.doctorQuery, doctors);
    if (found) {
      doctorId = found.doctorId;
      patch.doctorId = found.doctorId;
      patch.doctorName = found.doctorName;
    } else {
      message = "I could not find doctor {name}. Please say another doctor name.";
    }
  }
  return { patch, doctorId, message };
}

export function pickPatientMatch(name: string, patients: PatientSearchHit[]): PatientSearchHit | undefined {
  if (patients.length === 0) return undefined;
  const q = name.trim().toLowerCase();
  const exact = patients.filter((p) => (p.name || "").trim().toLowerCase() === q);
  if (exact.length === 1) return exact[0];
  if (exact.length > 1) return exact[0];
  return patients[0];
}
