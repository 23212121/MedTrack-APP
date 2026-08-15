import { session } from "./dl/MedTrackSession";

const DOCTOR_ID = "seed-doctor-1";
const DOCTOR_NAME = "Dr. Mehta";

export { DOCTOR_ID, DOCTOR_NAME };

function friendlyHttpError(status: number, text: string): string {
  const looksLikeHtml =
    /^\s*<!DOCTYPE/i.test(text) || /^\s*<html[\s>]/i.test(text);
  if (status === 404 && looksLikeHtml) {
    return (
      "API gateway not found (404). Start services with " +
      "services\\start-all.ps1 — gateway should listen on :8090 " +
      "(not Apache on :8080)."
    );
  }
  if (looksLikeHtml) {
    return `Request failed (${status}). Is the MedTrack gateway running on :8090?`;
  }
  if (text?.trim()) {
    try {
      const json = JSON.parse(text) as {
        error?: string;
        message?: string;
        path?: string;
      };
      if (typeof json.error === "string" && json.error !== "Bad Request") {
        return json.error;
      }
      if (typeof json.message === "string" && json.message) {
        return json.message;
      }
      if (status === 400 && json.path?.includes("/api/auth/login")) {
        return (
          "Patient login is not available on this server yet. " +
          "Restart the backend: cd medical-visit-tracker\\services && .\\start-all.ps1"
        );
      }
      if (status === 404 && json.path?.includes("/api/auth/register-user")) {
        return (
          "Registration API not found. Restart the backend with the latest code " +
          "(services\\start-all.ps1), then try again."
        );
      }
    } catch {
      // not JSON — use raw text below
    }
    if (!text?.trim()) {
      if (status === 500) {
        return (
          "Booking API error (500). Check PostgreSQL is running and " +
          "booking-service can connect (medtrackapp / schema svc)."
        );
      }
      return `Request failed (${status})`;
    }
    return text;
  }
  if (status === 500) {
    return (
      "Booking API error (500). Check PostgreSQL is running and " +
      "booking-service can connect (medtrackapp / schema svc)."
    );
  }
  return `Request failed (${status})`;
}

function resolveDoctorId(): string {
  const userId = session.getUserId();
  if (session.getLoginType() === "USER" && userId) return userId;
  if (userId && userId.startsWith("DOC-")) return userId;
  return DOCTOR_ID;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const hospitalId = session.getHospitalId();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init?.headers as Record<string, string> | undefined),
  };
  if (hospitalId) {
    headers["X-Hospital-Id"] = hospitalId;
  }
  const doctorId = resolveDoctorId();
  if (doctorId) {
    headers["X-Doctor-Id"] = doctorId;
  }
  const username = session.getUsername();
  if (username) {
    headers["X-User"] = username;
  }
  const res = await fetch(path, {
    ...init,
    headers,
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(friendlyHttpError(res.status, text));
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

/** Multipart upload — do not set Content-Type (browser sets boundary). */
async function requestForm<T>(path: string, form: FormData): Promise<T> {
  const headers: Record<string, string> = {};
  const hospitalId = session.getHospitalId();
  if (hospitalId) headers["X-Hospital-Id"] = hospitalId;
  const doctorId = resolveDoctorId();
  if (doctorId) headers["X-Doctor-Id"] = doctorId;
  const username = session.getUsername();
  if (username) headers["X-User"] = username;
  const res = await fetch(path, { method: "POST", headers, body: form });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(friendlyHttpError(res.status, text));
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

export type PatientDocument = {
  id: string;
  patientName: string;
  aadhaarNumber?: string;
  hospitalId: number;
  phoneNumber: string;
  filePath?: string;
  destinationPath?: string;
  sourcePath?: string;
  fileUpload1?: string;
  fileUpload2?: string;
  fileUpload3?: string;
  fileUpload4?: string;
  fileUpload5?: string;
  creationDate?: string;
  creationUser?: string;
  updateDate?: string;
  updateUser?: string;
};

export type Visit = {
  id: string;
  clinicId?: string;
  patientName: string;
  patientPhone: string;
  doctorId: string;
  doctorName: string;
  status: string;
  tokenNumber?: number;
  reason?: string;
  scheduledStart?: string;
  scheduledEnd?: string;
  checkedInAt?: string;
  actualStart?: string;
  actualEnd?: string;
  delayMinutes?: number;
  baseFee?: number;
  overtimeMinutes?: number;
  overtimeFee?: number;
  totalFee?: number;
  feeCurrency?: string;
};

export type ScheduleSlot = {
  id: string;
  doctorId: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  slotMinutes: number;
};

export type Availability = {
  id: string;
  doctorId: string;
  startsAt: string;
  endsAt: string;
  availabilityType: string;
  reason?: string;
};

export type DoctorAvailableDay = {
  date: string;
  dayName: string;
  available: boolean;
  summary: string;
  windows: { start: string; end: string; slotMinutes?: string }[];
  blocked: { type: string; reason: string; startsAt: string; endsAt: string }[];
};

export type DoctorAvailableDays = {
  doctorId: string;
  from: string;
  to: string;
  hasWeeklySchedule: boolean;
  days: DoctorAvailableDay[];
  availableDates: string[];
};

export type FeeRule = {
  id: string;
  doctorId: string;
  baseConsultFee: number;
  fixedConsultMinutes: number;
  overtimeFeeAmount: number;
  overtimeFeePerBlockMinutes: number;
  currency: string;
};

export type ChartBlock = {
  startsAt: string;
  endsAt: string;
  kind: string;
  label: string;
  visitId?: string;
  status?: string;
};

export type DayChart = {
  doctorId: string;
  doctorName: string;
  date: string;
  blocks: ChartBlock[];
  availableMinutes: number;
  busyMinutes: number;
  bookedMinutes: number;
  utilizationPercent: number;
};

export type NotificationRow = {
  id: string;
  eventCode: string;
  channel: string;
  recipient: string;
  subject?: string;
  body: string;
  status: string;
  createdAt: string;
};

/** Booking row — only doctorId stored; hospitalName/fee enriched at read time. */
export type Booking = {
  id: string;
  hospitalId: number;
  hospitalName?: string;
  doctorId: string;
  patientName: string;
  patientPhone: string;
  patientAge?: number;
  gender?: string;
  address?: string;
  reason?: string;
  appointmentDate: string;
  appointmentTime: string;
  tokenNumber?: number;
  status: string;
  consultationFee?: number;
  currency?: string;
  createdAt: string;
  updatedAt?: string;
};

export type CreateBookingBody = {
  doctorId: string;
  hospitalId?: number;
  patientId?: string;
  patientName: string;
  patientPhone: string;
  patientEmail?: string;
  patientAge?: number;
  gender?: string;
  address?: string;
  reason?: string;
  appointmentDate: string;
  appointmentTime: string;
  bookedBy?: "HOSPITAL" | "PATIENT";
  createdBy?: string;
};

/** Primary appointment store — only doctorId stored; display fields enriched at read time. */
export type Appointment = {
  id: string;
  hospitalId?: number | null;
  hospitalName?: string;
  doctorId: string;
  patientId: string;
  patientName: string;
  patientAge?: number;
  gender?: string;
  address?: string;
  reason?: string;
  appointmentDate: string;
  appointmentTime: string;
  tokenNumber?: number;
  status: string;
  consultationFee?: number;
  currency?: string;
  bookedBy?: string;
  bookingRefId?: string;
  createdBy?: string;
  createdDate: string;
  updatedBy?: string;
  updatedDate?: string;
};

/** Mirrors booking-service DoctorRegistrationEntity / doctor_details. */
export type DoctorRegistration = {
  id: string;
  doctorId: string;
  firstName: string;
  middleName?: string;
  lastName: string;
  gender: string;
  dateOfBirth: string;
  email: string;
  mobileNumber: string;
  specialization: string;
  status: string;
  hospitalId?: number;
  hospitalName?: string;
  createdDate?: string;
};

/** Doctor option for Book Appointment / Check-in (filtered by hospital). */
export type HospitalDoctor = {
  doctorId: string;
  doctorName: string;
  firstName?: string;
  middleName?: string;
  lastName?: string;
  specialization?: string;
  department?: string;
  hospitalId: number;
  hospitalName?: string;
  clinicName?: string;
  consultationFee?: number;
};

export type DoctorRegistrationBody = {
  firstName: string;
  middleName?: string;
  lastName: string;
  /** Chosen User ID for login (stored as doctor_id) */
  doctorUserId?: string;
  doctorId?: string;
  /** Password for User ID login */
  loginPassword?: string;
  password?: string;
  gender: string;
  dateOfBirth: string;
  profilePhoto?: string;
  bloodGroup?: string;
  maritalStatus?: string;
  mobileNumber: string;
  alternateMobileNumber?: string;
  email: string;
  emergencyContactNumber?: string;
  residentialAddress: string;
  city: string;
  state: string;
  country: string;
  postalCode: string;
  medicalRegistrationNumber: string;
  medicalCouncilName: string;
  registrationDate: string;
  registrationValidUntil?: string;
  yearsOfExperience?: number;
  currentDesignation?: string;
  department?: string;
  specialization: string;
  subSpecialization?: string;
  qualification?: string;
  medicalCollege?: string;
  graduationYear?: number;
  hospitalName?: string;
  clinicName?: string;
  hospitalId?: number;
  branch?: string;
  consultationType?: string;
  consultationFee?: number;
  followUpFee?: number;
  availableDays?: string;
  availableTimeSlots?: string;
  aadhaarNumber?: string;
  panNumber?: string;
  passportNumber?: string;
  governmentIdUpload?: string;
  accountHolderName?: string;
  bankName?: string;
  accountNumber?: string;
  ifscCode?: string;
  upiId?: string;
  medicalRegistrationCertificate?: string;
  degreeCertificate?: string;
  experienceCertificate?: string;
  identityProof?: string;
  addressProof?: string;
  passportSizePhotograph?: string;
  digitalSignature?: string;
  emergencyContactName?: string;
  emergencyRelationship?: string;
  emergencyContactPhone?: string;
  languagesKnown?: string;
  biography?: string;
  awardsAchievements?: string;
  publications?: string;
  researchExperience?: string;
  memberships?: string;
  status?: string;
  createdBy?: string;
  remarks?: string;
  infoCorrectConfirmed: boolean;
  termsAccepted: boolean;
  privacyPolicyAccepted: boolean;
};

export const api = {
  visits: (doctorId?: string, date?: string) => {
    const q = new URLSearchParams();
    if (doctorId) q.set("doctorId", doctorId);
    if (date) q.set("date", date);
    const qs = q.toString();
    return request<{ visits: Visit[] }>(`/api/visits${qs ? `?${qs}` : ""}`);
  },
  checkInBoard: (opts?: { hospitalId?: string | number; doctorId?: string }) => {
    const q = new URLSearchParams();
    if (opts?.hospitalId != null && String(opts.hospitalId).trim()) {
      q.set("hospitalId", String(opts.hospitalId).trim());
    }
    if (opts?.doctorId?.trim()) q.set("doctorId", opts.doctorId.trim());
    const qs = q.toString();
    return request<{ visits: Visit[] }>(
      `/api/visits/check-in-board${qs ? `?${qs}` : ""}`
    );
  },
  visitDetail: (id: string) =>
    request<{ visit: Visit; events: { id: string; eventType: string; message: string; createdAt: string }[] }>(
      `/api/visits/${id}`
    ),
  checkIn: (id: string) =>
    request<Visit>(`/api/visits/${id}/check-in`, { method: "POST", body: "{}" }),
  start: (id: string) =>
    request<Visit>(`/api/visits/${id}/start`, { method: "POST", body: "{}" }),
  late: (id: string) =>
    request<Visit>(`/api/visits/${id}/late`, {
      method: "POST",
      body: JSON.stringify({ minutes: 15 }),
    }),
  complete: (id: string) =>
    request<Visit>(`/api/visits/${id}/complete`, { method: "POST", body: "{}" }),
  createVisit: (body: Record<string, unknown>) =>
    request<Visit>("/api/visits", { method: "POST", body: JSON.stringify(body) }),

  weekly: (doctorId: string) =>
    request<{ schedules: ScheduleSlot[] }>(`/api/schedules/weekly/${doctorId}`),
  saveWeekly: (doctorId: string, schedules: Omit<ScheduleSlot, "id" | "doctorId">[]) =>
    request<{ schedules: ScheduleSlot[] }>(`/api/schedules/weekly/${doctorId}`, {
      method: "PUT",
      body: JSON.stringify(schedules),
    }),
  availability: (doctorId: string, from: string, to: string) =>
    request<{ availability: Availability[] }>(
      `/api/schedules/availability/${doctorId}?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
    ),
  availableDays: (doctorId: string, from: string, to: string) =>
    request<DoctorAvailableDays>(
      `/api/schedules/available-days/${encodeURIComponent(doctorId)}?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
    ),
  addAvailability: (body: {
    doctorId: string;
    startsAt: string;
    endsAt: string;
    availabilityType: string;
    reason?: string;
  }) =>
    request<Availability>("/api/schedules/availability", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  deleteAvailability: (id: string) =>
    request<void>(`/api/schedules/availability/${id}`, { method: "DELETE" }),
  chart: (doctorId: string, date: string) =>
    request<DayChart>(
      `/api/schedules/chart/${doctorId}?date=${date}&doctorName=${encodeURIComponent(DOCTOR_NAME)}`
    ),
  fees: (doctorId: string) => request<FeeRule>(`/api/schedules/fees/${doctorId}`),
  saveFees: (doctorId: string, body: Partial<FeeRule>) =>
    request<FeeRule>(`/api/schedules/fees/${doctorId}`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  calcFees: (doctorId: string, actualConsultMinutes: number) =>
    request<Record<string, number | string>>("/api/schedules/fees/calculate", {
      method: "POST",
      body: JSON.stringify({ doctorId, actualConsultMinutes }),
    }),
  notifications: () =>
    request<{ notifications: NotificationRow[] }>("/api/notifications"),

  listPatientDocuments: () =>
    request<{ count: number; documents: PatientDocument[] }>("/api/documents"),

  uploadPatientDocuments: (body: {
    patientName: string;
    aadhaarNumber: string;
    phoneNumber: string;
    files: File[];
  }) => {
    const form = new FormData();
    form.append("patientName", body.patientName);
    form.append("aadhaarNumber", body.aadhaarNumber);
    form.append("phoneNumber", body.phoneNumber);
    for (const file of body.files) {
      form.append("files", file);
    }
    return requestForm<{ message: string; document: PatientDocument }>(
      "/api/documents",
      form,
    );
  },

  /** Download a hospital-owned document file (sends X-Hospital-Id). */
  downloadPatientDocument: async (documentId: string, slot: number, filename?: string) => {
    const headers: Record<string, string> = {};
    const hospitalId = session.getHospitalId();
    if (hospitalId) headers["X-Hospital-Id"] = hospitalId;
    const username = session.getUsername();
    if (username) headers["X-User"] = username;
    const res = await fetch(`/api/documents/${documentId}/files/${slot}`, { headers });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(friendlyHttpError(res.status, text));
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename || `document-${slot}`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  },

  bookings: () => request<{ count: number; bookings: Booking[] }>("/api/bookings"),
  createBooking: (body: CreateBookingBody) =>
    request<{ message: string; booking: Booking; appointment?: Appointment }>(
      "/api/bookings",
      {
        method: "POST",
        body: JSON.stringify(body),
      }
    ),
  appointments: (opts?: {
    doctorId?: string;
    patientId?: string;
    hospitalId?: number;
    date?: string;
  }) => {
    const q = new URLSearchParams();
    if (opts?.doctorId) q.set("doctorId", opts.doctorId);
    if (opts?.patientId) q.set("patientId", opts.patientId);
    if (opts?.hospitalId != null) q.set("hospitalId", String(opts.hospitalId));
    if (opts?.date) q.set("date", opts.date);
    const qs = q.toString();
    return request<{ count: number; appointments: Appointment[] }>(
      `/api/appointments${qs ? `?${qs}` : ""}`
    );
  },

  /** Doctor portal: same-day patients for session hospital + doctor only. */
  doctorTodayPatients: (date?: string) => {
    const q = new URLSearchParams();
    if (date) q.set("date", date);
    const qs = q.toString();
    return request<{
      date: string;
      hospitalId: number;
      doctorId: string;
      doctorName?: string;
      hospitalName?: string;
      tokenCount: number;
      withinClinicHours?: boolean;
      scheduleWindows?: { startTime: string; endTime: string; slotMinutes?: number }[];
      patients: Appointment[];
      count: number;
    }>(`/api/doctor/patients${qs ? `?${qs}` : ""}`);
  },

  doctorPatientDetail: (appointmentId: string) =>
    request<{
      patient: Appointment;
      documents: {
        id: string;
        slot: number;
        patientName?: string;
        documentType: string;
        documentName: string;
        uploadDate?: string;
      }[];
      documentCount: number;
    }>(`/api/doctor/patients/${encodeURIComponent(appointmentId)}`),

  doctors: () =>
    request<{ count: number; doctors: DoctorRegistration[] }>("/api/doctors"),
  doctorsByHospital: (hospitalId: string | number) =>
    request<{ count: number; hospitalId: number; doctors: HospitalDoctor[] }>(
      `/api/doctors?hospitalId=${encodeURIComponent(String(hospitalId))}`
    ),
  registerDoctor: (body: DoctorRegistrationBody) =>
    request<{ message: string; doctor: DoctorRegistration }>("/api/doctors/register", {
      method: "POST",
      body: JSON.stringify(body),
    }),

  hospitals: () =>
    request<{ count: number; hospitals: HospitalRegistrationSummary[] }>("/api/hospitals"),
  hospital: (id: string | number) =>
    request<{ hospital: HospitalRegistrationSummary }>(`/api/hospitals/${id}`),
  nextHospitalId: () =>
    request<{ nextHospitalId: number }>("/api/hospitals/next-id"),
  registerHospital: (body: Record<string, unknown>) =>
    request<{ message: string; hospital: HospitalRegistrationSummary }>(
      "/api/hospitals/register",
      { method: "POST", body: JSON.stringify(body) }
    ),

  login: (body: { loginType: "HOSPITAL" | "USER" | "PATIENT"; id: string; password: string }) =>
    request<{
      message: string;
      loginType: "HOSPITAL" | "USER" | "PATIENT";
      username: string;
      userId?: string;
      doctorId?: string;
      hospitalId?: number | string;
      hospitalName?: string;
      hospitalCode?: string;
      role?: string;
      patientPhone?: string;
      patientName?: string;
      patientId?: string;
      patientUserId?: string;
    }>("/api/auth/login", { method: "POST", body: JSON.stringify(body) }),

  registerPatient: (body: {
    phone: string;
    password: string;
    name: string;
    age?: number;
    gender?: string;
    email?: string;
    address?: string;
  }) =>
    request<{ message: string; patientId: string; phone: string; name: string }>(
      "/api/auth/register-patient",
      { method: "POST", body: JSON.stringify(body) },
    ),

  nextUserId: () =>
    request<{ userId: string; sequence: number }>("/api/auth/next-user-id"),

  registerUser: (body: {
    userName: string;
    password: string;
    phone?: string;
    email?: string;
    createdBy?: string;
  }) =>
    request<{
      message: string;
      userId: string;
      userName: string;
      phone?: string;
      email?: string;
      patientId?: string;
    }>("/api/auth/register-user", {
      method: "POST",
      body: JSON.stringify(body),
    }),

  patientDashboard: (phone: string) =>
    request<PatientDashboard>(`/api/patient/dashboard?phone=${encodeURIComponent(phone)}`),

  patientProfile: (phone: string) =>
    request<PatientProfileResponse>(`/api/patient/profile?phone=${encodeURIComponent(phone)}`),

  updatePatientProfile: (phone: string, body: Record<string, unknown>) =>
    request<{ message: string; profile: PatientProfile }>(
      `/api/patient/profile?phone=${encodeURIComponent(phone)}`,
      { method: "PUT", body: JSON.stringify(body) },
    ),

  patientBookings: (phone: string, patientId?: string) => {
    const q = new URLSearchParams();
    if (phone) q.set("phone", phone);
    if (patientId) q.set("patientId", patientId);
    return request<{
      upcoming: BookingSummary[];
      history: BookingSummary[];
      upcomingCount: number;
      historyCount: number;
      patientId?: string;
    }>(`/api/patient/bookings?${q}`);
  },

  patientQueueStatus: (phone: string, patientId?: string) => {
    const q = new URLSearchParams();
    if (phone) q.set("phone", phone);
    if (patientId) q.set("patientId", patientId);
    return request<PatientQueueStatusResponse>(`/api/patient/queue-status?${q}`);
  },

  /** Distinct patient names from appointments for a shared phone (reports dropdown). */
  appointmentPatients: (phone: string) => {
    const q = new URLSearchParams({ phone: phone.replace(/\D/g, "") });
    return request<{
      phone: string;
      count: number;
      patients: AppointmentPatientOption[];
    }>(`/api/patient/appointment-patients?${q}`);
  },

  /** Reports filtered by phone + optional patientName (WHERE phone AND patient_name). */
  patientReports: (
    phone: string,
    opts?: { patientName?: string; from?: string; to?: string },
  ) => {
    const q = new URLSearchParams({ phone: phone.replace(/\D/g, "") });
    if (opts?.patientName) q.set("patientName", opts.patientName);
    if (opts?.from) q.set("from", opts.from);
    if (opts?.to) q.set("to", opts.to);
    return request<{
      count: number;
      reports: PatientReport[];
      patients: AppointmentPatientOption[];
      selectedPatientName?: string;
    }>(`/api/patient/reports?${q}`);
  },

  /** Hospital-uploaded documents for selected patient (phone + patientName filter). */
  patientDocuments: (phone: string, patientName: string) => {
    const q = new URLSearchParams({
      phone: phone.replace(/\D/g, ""),
      patientName,
    });
    return request<{ count: number; documents: PatientPortalDocument[] }>(
      `/api/patient/documents?${q}`,
    );
  },

  /** Download document: backend fetches path from table → Amazon S3 (demo keys). */
  downloadPatientPortalDocument: async (
    documentId: string,
    slot: number,
    phone: string,
    filename?: string,
  ) => {
    const q = new URLSearchParams({ phone: phone.replace(/\D/g, "") });
    const res = await fetch(`/api/patient/documents/${documentId}/files/${slot}?${q}`);
    if (!res.ok) {
      const text = await res.text();
      throw new Error(friendlyHttpError(res.status, text));
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename || `document-${slot}`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  },

  bookingsByPhone: (phone: string) =>
    request<{ count: number; bookings: Booking[] }>(
      `/api/bookings?phone=${encodeURIComponent(phone.replace(/\D/g, ""))}`,
    ),

  cancelBooking: (id: string, phone: string) =>
    request<{ message: string; booking: Booking }>(
      `/api/bookings/${id}/cancel?phone=${encodeURIComponent(phone.replace(/\D/g, ""))}`,
      { method: "POST", body: "{}" },
    ),

  rescheduleBooking: (
    id: string,
    phone: string,
    body: { appointmentDate: string; appointmentTime: string; reason?: string },
  ) =>
    request<{ message: string; booking: Booking }>(
      `/api/bookings/${id}?phone=${encodeURIComponent(phone.replace(/\D/g, ""))}`,
      { method: "PATCH", body: JSON.stringify(body) },
    ),

  queueBoard: (hospitalId: string | number, doctorId: string) =>
    request<QueueStatus>(
      `/api/queue/board?hospitalId=${encodeURIComponent(String(hospitalId))}&doctorId=${encodeURIComponent(doctorId)}`
    ),
  queueStatus: (hospitalId: string | number, doctorId: string, tokenNo?: number) => {
    const qs = new URLSearchParams({
      hospitalId: String(hospitalId),
      doctorId,
    });
    if (tokenNo != null) qs.set("tokenNo", String(tokenNo));
    return request<QueueStatus>(`/api/queue/status?${qs}`);
  },
};

export type QueuePatient = {
  token: number;
  patient: string;
  status: string;
  visitId?: string;
};

export type QueueStatus = {
  hospitalId: number;
  doctorId: string;
  doctorName: string;
  department: string;
  queueDate: string;
  currentTime: string;
  avgConsultationMinutes: number;
  doctorAvailable: boolean;
  waitingCount: number;
  currentRunning: QueuePatient | null;
  waitingPatients: QueuePatient[];
  waitingBeforeYou?: QueuePatient[];
  upcoming: {
    current: number | null;
    next: number | null;
    then: number | null;
    then2?: number | null;
  };
  progress: {
    completed: number;
    total: number;
    pct: number;
    waiting: number;
  };
  yourStatus?: {
    token: number;
    currentRunningToken?: number | null;
    patientsBefore: number;
    patientsBeforeTokens?: number[];
    patientsBeforeDisplay?: string;
    avgMinutes: number;
    waitMinutes: number;
    expectedTurnTime: string;
  };
  lastCompletedTokens?: number[];
  emergencyCases?: { token: number; patient: string; status: string }[];
  booking?: BookingSummary;
};

export type BookingSummary = {
  id: string;
  appointmentId?: string;
  doctorId: string;
  doctorName?: string;
  hospitalId?: number;
  hospitalName?: string;
  department?: string;
  clinicName?: string;
  branch?: string;
  patientId?: string;
  patientName?: string;
  patientPhone?: string;
  patientAge?: number;
  gender?: string;
  address?: string;
  appointmentDate: string;
  appointmentTime: string;
  tokenNumber?: number;
  status: string;
  appointmentStatus?: string;
  bookedBy?: "HOSPITAL" | "PATIENT" | string;
  bookedByLabel?: string;
  createdBy?: string;
  /** Display name for created_by (resolved server-side; not a raw id). */
  createdByName?: string;
  reason?: string;
  consultationFee?: number;
  currency?: string;
  createdAt?: string;
  updatedAt?: string;
  bookedAt?: string;
};

export type PatientProfile = {
  id: string;
  name: string;
  phone: string;
  age?: number | string;
  gender?: string;
  email?: string;
  address?: string;
  bloodGroup?: string;
  emergencyContact?: string;
};

/** Distinct patient from appointments for a shared phone (reports dropdown). */
export type AppointmentPatientOption = {
  patientId?: string;
  patientName: string;
  age?: number | string;
  gender?: string;
  phone?: string;
};

export type PatientReport = {
  id: string;
  patientName?: string;
  reportType: string;
  title: string;
  description?: string;
  fileUrl?: string;
  doctorName?: string;
  reportDate?: string;
  createdAt?: string;
};

export type PatientPortalDocument = {
  id: string;
  slot: number;
  patientName: string;
  documentType: string;
  hospitalName: string;
  hospitalId?: number;
  documentName: string;
  documentPath?: string;
  filePath?: string;
  destinationPath?: string;
  uploadDate?: string;
  downloadUrl?: string;
};

export type PatientDashboard = {
  patient: PatientProfile;
  nextAppointment: BookingSummary | null;
  queueSummary: {
    doctorName?: string;
    department?: string;
    tokenNumber?: number;
    patientsBefore?: number;
    waitMinutes?: number;
    expectedTurnTime?: string;
    currentRunningToken?: number | null;
  } | null;
  upcomingCount: number;
  reportCount: number;
};

export type PatientProfileResponse = {
  profile: PatientProfile;
  appointmentHistory: BookingSummary[];
  reports: PatientReport[];
};

export type LiveQueueStatus =
  | "BOOKED"
  | "WAITING"
  | "YOU_ARE_NEXT"
  | "IN_CONSULTATION";

export type QueueSummary = {
  yourToken?: number | null;
  doctorName?: string;
  department?: string;
  hospitalId?: number | string;
  doctorId?: string;
  waitingCount?: number;
  totalPatients?: number;
  totalTokens?: number;
  upcomingPatientCount?: number;
  completedCount?: number;
  patientsBefore?: number | null;
  waitMinutes?: number | null;
  expectedTurnTime?: string | null;
  currentRunningToken?: number | null;
  currentRunningPatient?: string | null;
  currentRunningStatus?: string | null;
  currentConsultation?: {
    token: number;
    patient: string;
    status: string;
  } | null;
  patientsAhead?: { token: number; patient: string; status: string }[];
};

export type PatientQueueStatusResponse = QueueStatus & {
  booking: BookingSummary;
  patientId?: string;
  liveQueueStatus?: LiveQueueStatus;
  queueSummary?: QueueSummary;
};

export type HospitalRegistrationSummary = {
  id: number;
  hospitalId?: number;
  hospitalCode: string;
  hospitalName: string;
  hospitalType?: string;
  registrationNumber?: string;
  email?: string;
  primaryContact?: string;
  city?: string;
  state?: string;
  country?: string;
  subscriptionPlan?: string;
  status?: string;
  createdAt?: string;
};

export type LeaveBalanceRow = {
  id: string;
  leaveTypeId: string;
  leaveType: string;
  total: number;
  used: number;
  available: number;
  percentAvailable: number;
  percentUsed: number;
};

export type LeaveRequestRow = {
  id: string;
  leaveTypeId: string;
  leaveType: string;
  fromDate: string;
  toDate: string;
  reason?: string;
  status: string;
};

export type LeaveSummary = {
  year: number;
  employee: {
    id: string;
    employeeId: string;
    name: string;
    doctorLinkId?: string;
  };
  pending: LeaveRequestRow[];
  balances: LeaveBalanceRow[];
  stats: {
    weeklyPattern: number[];
    weeklyLabels: string[];
    monthlyStats: number[];
    monthlyLabels: string[];
    consumedByType: { leaveType: string; days: number; color: string }[];
  };
};

export function getLeaveSummary(year?: number) {
  const q = year ? `?year=${year}` : "";
  return request<LeaveSummary>(`/api/hrm/leave/summary${q}`);
}

export function requestLeave(body: {
  leaveTypeId: string;
  fromDate: string;
  toDate: string;
  reason?: string;
  autoApprove?: boolean;
}) {
  return request<LeaveRequestRow>("/api/hrm/leave/requests", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** Set which leave types this doctor has (drives Leave balance cards). */
export function setLeaveEntitlements(
  rows: { name: string; annualQuota: number }[],
) {
  return request<{ employeeId: string; balances: LeaveBalanceRow[] }>(
    "/api/hrm/leave/entitlements",
    {
      method: "PUT",
      body: JSON.stringify(rows),
    },
  );
}

export type InboxItem = {
  id: string;
  hospitalId: number;
  doctorId: string;
  employeeId: string;
  employeeName?: string;
  category: string;
  documentName: string;
  title: string;
  status: string;
  actionRequired: string;
  messageBody?: string;
  fileUrl?: string;
  requestedBy?: string;
  requestedAt?: string;
  acknowledgedAt?: string;
  archived?: boolean;
  creationDate?: string;
  creationUser?: string;
};

export type InboxCategoryCount = Record<string, number>;

export type InboxResponse = {
  hospitalId: number;
  doctorId: string;
  view: string;
  pendingCount: number;
  categoryCounts: InboxCategoryCount;
  items: InboxItem[];
};

export function getHrmInbox(view: string = "action", category?: string) {
  const params = new URLSearchParams({ view });
  if (category) params.set("category", category);
  return request<InboxResponse>(`/api/hrm/inbox?${params}`);
}

export function acknowledgeInboxItem(id: string) {
  return request<InboxItem>(`/api/hrm/inbox/${id}/acknowledge`, { method: "POST" });
}

export function downloadInboxItem(id: string) {
  return request<InboxItem>(`/api/hrm/inbox/${id}/download`, { method: "POST" });
}

export function releaseInboxDocument(body: {
  category: string;
  documentName: string;
  title?: string;
  fileUrl?: string;
  requestedBy?: string;
}) {
  return request<InboxItem>("/api/hrm/inbox", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export type WfhRequest = {
  id: string;
  hospitalId: number;
  doctorId: string;
  fromDate: string;
  toDate: string;
  days: number;
  note?: string;
  notifyTo?: string;
  status: string;
};

export function requestWorkFromHome(body: {
  fromDate: string;
  toDate: string;
  note?: string;
  notifyTo?: string;
}) {
  return request<WfhRequest>("/api/hrm/wfh", {
    method: "POST",
    body: JSON.stringify(body),
  });
}
