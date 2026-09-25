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
  if (session.isMedical() || session.isPatient() || session.getLoginType() === "HOSPITAL") {
    return "";
  }
  const userId = session.getUserId();
  if (session.getLoginType() === "USER" && userId) return userId;
  if (userId && userId.startsWith("DOC-")) return userId;
  return "";
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const anonymousAuth = path.startsWith("/api/auth/");
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init?.headers as Record<string, string> | undefined),
  };
  if (!anonymousAuth) {
    const hospitalId = session.getHospitalId();
    if (hospitalId) {
      headers["X-Hospital-Id"] = hospitalId;
    }
    const loginType = session.getLoginType();
    if (loginType) headers["X-Login-Type"] = loginType;
    const storeId = session.getMedicalStoreId();
    if (storeId) headers["X-Medical-Store-Id"] = storeId;
    const patientPhone = session.getPatientPhone();
    if (patientPhone) headers["X-Patient-Phone"] = patientPhone;
    const patientId = session.getPatientId();
    if (patientId) headers["X-Patient-Id"] = patientId;
    const patientName = session.getPatientName();
    if (patientName) headers["X-Patient-Name"] = patientName;
    const doctorId = resolveDoctorId();
    if (doctorId) {
      headers["X-Doctor-Id"] = doctorId;
    }
    const username = session.getUsername();
    if (username) {
      headers["X-User"] = username;
    }
    const token = session.getAccessToken();
    if (token && token.includes(".")) {
      headers.Authorization = `Bearer ${token}`;
    }
  }
  const timeoutMs = anonymousAuth ? 20000 : 0;
  const controller = timeoutMs ? new AbortController() : null;
  const timer = controller ? window.setTimeout(() => controller.abort(), timeoutMs) : 0;
  let res: Response;
  try {
    res = await fetch(path, {
      ...init,
      headers,
      signal: init?.signal ?? controller?.signal,
    });
  } catch (err) {
    if (controller && err instanceof DOMException && err.name === "AbortError") {
      throw new Error(
        "Sign-in timed out. Check that the MedTrack API is running on :8090.",
      );
    }
    throw err;
  } finally {
    if (timer) window.clearTimeout(timer);
  }
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
  const loginType = session.getLoginType();
  if (loginType) headers["X-Login-Type"] = loginType;
  const storeId = session.getMedicalStoreId();
  if (storeId) headers["X-Medical-Store-Id"] = storeId;
  const patientPhone = session.getPatientPhone();
  if (patientPhone) headers["X-Patient-Phone"] = patientPhone;
  const patientId = session.getPatientId();
  if (patientId) headers["X-Patient-Id"] = patientId;
  const patientName = session.getPatientName();
  if (patientName) headers["X-Patient-Name"] = patientName;
  const doctorId = resolveDoctorId();
  if (doctorId) headers["X-Doctor-Id"] = doctorId;
  const username = session.getUsername();
  if (username) headers["X-User"] = username;
  const token = session.getAccessToken();
  if (token && token.includes(".")) {
    headers.Authorization = `Bearer ${token}`;
  }
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

export type SystemStatusResponse = {
  checkedAt?: string;
  mode?: string;
  overall?: string;
  summary?: { up: number; down: number; total: number };
  local?: {
    name?: string;
    port?: number;
    status?: string;
    actuator?: string;
    components?: Record<string, string>;
  };
  services?: Array<{
    name: string;
    port?: number;
    status?: string;
    mode?: string;
    detail?: string;
    url?: string;
    embedded?: boolean;
  }>;
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
  hospitalId?: number;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  slotMinutes: number;
};

export type SchedulePerson = {
  id: string;
  name: string;
  role: "Doctor" | "Staff";
  typeLabel?: string;
};

export type StaffMember = {
  employeeId: string;
  name?: string;
  firstName?: string;
  lastName?: string;
  employeeType?: string;
  designation?: string;
  department?: string;
  doctorLinkId?: string;
};

export type Availability = {
  id: string;
  doctorId: string;
  hospitalId?: number;
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
  doctorName?: string;
  department?: string;
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

export type AppointmentPayment = {
  paymentId: string;
  appointmentId?: string;
  referenceType?: string;
  referenceId?: string;
  patientId: string;
  amount: number;
  currency?: string;
  status: string;
  gatewayTransactionId?: string | null;
  gatewayName?: string;
  qrImageUrl?: string;
  upiUri?: string;
  upiId?: string;
  razorpayEnabled?: boolean;
  message?: string;
  createdAt?: string;
  updatedAt?: string;
  upiApps?: Array<{ id: string; name: string; uri: string }>;
  booking?: EmergencyBedBooking;
};

/** Primary appointment store — only doctorId stored; display fields enriched at read time. */
export type Appointment = {
  id: string;
  hospitalId?: number | null;
  hospitalName?: string;
  doctorName?: string;
  doctorId: string;
  department?: string;
  patientId: string;
  patientName: string;
  patientPhone?: string;
  phoneNumber?: string;
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
  paymentStatus?: string;
  paymentId?: string;
  chatCount?: number;
  unreadCount?: number;
  otherMessageAts?: string[];
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
  department?: string;
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
  hospitalSchedules: (hospitalId: string | number, personIds?: string[]) => {
    const q =
      personIds && personIds.length
        ? `?personIds=${encodeURIComponent(personIds.join(","))}`
        : "";
    return request<{ schedules: ScheduleSlot[] }>(
      `/api/schedules/hospital/${encodeURIComponent(String(hospitalId))}${q}`,
    );
  },
  saveWeekly: (doctorId: string, schedules: Omit<ScheduleSlot, "id" | "doctorId">[]) =>
    request<{ schedules: ScheduleSlot[] }>(`/api/schedules/weekly/${doctorId}`, {
      method: "PUT",
      body: JSON.stringify(schedules),
    }),
  saveWeeklyBatch: (
    personIds: string[],
    schedules: Omit<ScheduleSlot, "id" | "doctorId">[],
    hospitalId?: string | number,
  ) =>
    request<{ schedules: ScheduleSlot[] }>("/api/schedules/weekly-batch", {
      method: "PUT",
      body: JSON.stringify({
        personIds,
        hospitalId: hospitalId != null && hospitalId !== "" ? Number(hospitalId) : undefined,
        schedules,
      }),
    }),
  employees: () =>
    request<{ count: number; employees: StaffMember[] }>("/api/hrm/employees"),
  availability: (doctorId: string, from: string, to: string) =>
    request<{ availability: Availability[] }>(
      `/api/schedules/availability/${doctorId}?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
    ),
  hospitalAvailability: (
    hospitalId: string | number,
    from: string,
    to: string,
    personIds?: string[],
  ) => {
    const q = new URLSearchParams({ from, to });
    if (personIds && personIds.length) q.set("personIds", personIds.join(","));
    return request<{ availability: Availability[] }>(
      `/api/schedules/availability-hospital/${encodeURIComponent(String(hospitalId))}?${q}`,
    );
  },
  availableDays: (doctorId: string, from: string, to: string) =>
    request<DoctorAvailableDays>(
      `/api/schedules/available-days/${encodeURIComponent(doctorId)}?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
    ),
  addAvailability: (body: {
    doctorId: string;
    hospitalId?: number;
    startsAt: string;
    endsAt: string;
    availabilityType: string;
    reason?: string;
  }) =>
    request<Availability>("/api/schedules/availability", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  addAvailabilityBatch: (body: {
    personIds: string[];
    hospitalId?: number;
    startsAt: string;
    endsAt: string;
    availabilityType: string;
    reason?: string;
  }) =>
    request<{ availability: Availability[] }>("/api/schedules/availability-batch", {
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

  systemStatus: () =>
    request<SystemStatusResponse>("/api/system/status"),

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
    return requestForm<{
      message: string;
      document: PatientDocument;
      s3Path?: string;
      s3Uploaded?: boolean;
    }>(
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
    const token = session.getAccessToken();
    if (token && token.includes(".")) {
      headers.Authorization = `Bearer ${token}`;
    }
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
  searchPatients: (name: string) =>
    request<{
      count: number;
      name: string;
      patients: Array<{
        id?: string;
        name?: string;
        phone?: string;
        age?: number | null;
        gender?: string;
        email?: string;
        address?: string;
        source?: string;
      }>;
    }>(`/api/patients/search?name=${encodeURIComponent(name)}`),
  createBooking: (body: CreateBookingBody) =>
    request<{ message: string; booking: Booking; appointment?: Appointment }>(
      "/api/bookings",
      {
        method: "POST",
        body: JSON.stringify(body),
      }
    ),
  createPayment: (body: {
    appointmentId: string;
    patientId?: string;
    amount?: number;
  }) =>
    request<AppointmentPayment>("/api/payments/create", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  getPayment: (paymentId: string) =>
    request<AppointmentPayment>(`/api/payments/${encodeURIComponent(paymentId)}`),
  verifyPaymentWithGateway: (paymentId: string) =>
    request<AppointmentPayment>(
      `/api/payments/${encodeURIComponent(paymentId)}/verify-with-gateway`,
      { method: "POST" }
    ),
  cancelPayment: (paymentId: string) =>
    request<AppointmentPayment>(
      `/api/payments/${encodeURIComponent(paymentId)}/cancel`,
      { method: "POST" }
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
      totalBookings?: number;
      bookedCount?: number;
      inProcessCount?: number;
      completedCount?: number;
      cancelledCount?: number;
      patientCount?: number;
      specialization?: string;
      department?: string;
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
      previousRecords?: {
        id: string;
        appointmentDate?: string;
        status?: string;
        tokenNumber?: number;
        doctorName?: string;
        reason?: string;
      }[];
    }>(`/api/doctor/patients/${encodeURIComponent(appointmentId)}`),

  uploadDoctorPatientDocuments: (appointmentId: string, body: { aadhaarNumber: string; files: File[] }) => {
    const form = new FormData();
    form.append("aadhaarNumber", body.aadhaarNumber);
    for (const file of body.files) {
      form.append("files", file);
    }
    return requestForm<{
      patient: Appointment;
      documents: {
        id: string;
        slot: number;
        patientName?: string;
        aadhaarNumber?: string;
        documentType: string;
        documentName: string;
        uploadDate?: string;
        downloadUrl?: string;
      }[];
      documentCount: number;
    }>(`/api/doctor/patients/${encodeURIComponent(appointmentId)}/documents`, form);
  },

  doctorPatientStatus: (appointmentId: string, action: "start" | "complete" | "IN-PROCESS" | "COMPLETED") =>
    request<{
      patient: Appointment;
      documents: unknown[];
      documentCount: number;
    }>(`/api/doctor/patients/${encodeURIComponent(appointmentId)}/status`, {
      method: "PATCH",
      body: JSON.stringify({ action }),
    }),

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
  statuses: () =>
    request<{ count: number; statuses: LoginStatusLookup[] }>("/api/statuses"),
  status: (statusId: number) =>
    request<{ status: LoginStatusLookup }>(`/api/statuses/${statusId}`),
  hospital: (id: string | number) =>
    request<{ hospital: HospitalRegistrationSummary }>(`/api/hospitals/${id}`),
  nextHospitalId: () =>
    request<{ nextHospitalId: number }>("/api/hospitals/next-id"),
  registerHospital: (body: Record<string, unknown>) =>
    request<{ message: string; hospital: HospitalRegistrationSummary }>(
      "/api/hospitals/register",
      { method: "POST", body: JSON.stringify(body) },
    ),

  emergencyContext: () =>
    request<EmergencyContext>("/api/emergency/context"),
  emergencyHospitals: (opts?: { state?: string; city?: string; q?: string }) => {
    const qs = new URLSearchParams();
    if (opts?.state) qs.set("state", opts.state);
    if (opts?.city) qs.set("city", opts.city);
    if (opts?.q) qs.set("q", opts.q);
    const suffix = qs.toString() ? `?${qs}` : "";
    return request<{ count: number; state?: string; city?: string; hospitals: EmergencyHospital[] }>(
      `/api/emergency/hospitals${suffix}`,
    );
  },
  emergencyHospital: (hospitalId: string | number) =>
    request<EmergencyHospitalDetail>(`/api/emergency/hospitals/${encodeURIComponent(String(hospitalId))}`),
  bookEmergencyBed: (
    bedId: string,
    body: { patientName: string; patientPhone?: string; patientId?: string; notes?: string },
  ) =>
    request<{ message: string; booking: EmergencyBedBooking; bed: EmergencyBed }>(
      `/api/emergency/beds/${encodeURIComponent(bedId)}/book`,
      { method: "POST", body: JSON.stringify(body) },
    ),
  emergencyBooking: (bookingId: string) =>
    request<{ booking: EmergencyBedBooking }>(
      `/api/emergency/bookings/${encodeURIComponent(bookingId)}`,
    ),
  payEmergencyBooking: (bookingId: string) =>
    request<AppointmentPayment>(
      `/api/emergency/bookings/${encodeURIComponent(bookingId)}/pay`,
      { method: "POST" },
    ),
  attachEmergencySlip: (bookingId: string, form: FormData) =>
    requestForm<{ message: string; booking: EmergencyBedBooking }>(
      `/api/emergency/bookings/${encodeURIComponent(bookingId)}/slip`,
      form,
    ),

  login: (body: { loginType: "HOSPITAL" | "USER" | "PATIENT" | "MEDICAL"; id: string; password: string }) =>
    request<{
      message: string;
      loginType: "HOSPITAL" | "USER" | "PATIENT" | "MEDICAL";
      username: string;
      userId?: string;
      doctorId?: string;
      hospitalId?: number | string;
      hospitalName?: string;
      hospitalCode?: string;
      role?: string;
      token?: string;
      patientPhone?: string;
      patientName?: string;
      patientId?: string;
      patientUserId?: string;
      medicalStoreId?: string;
      storeCode?: string;
      storeName?: string;
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

  hospitalPatientList: () =>
    request<{
      hospitalId: number;
      hospitalName?: string;
      totalPatients: number;
      totalDoctors: number;
      totalBookings: number;
      rows: HospitalPatientListRow[];
    }>("/api/hospital/patient-list"),

  patientChatThreads: (phone: string) =>
    request<{ count: number; threads: PatientChatThread[] }>(
      `/api/care-chats?phone=${encodeURIComponent(phone.replace(/\D/g, ""))}`,
    ),

  careChatUnreadHints: (ids: string[], viewer: "HOSPITAL" | "DOCTOR" | "PATIENT") =>
    request<{
      viewer: string;
      unreadCount: Record<string, number>;
      otherMessageAts: Record<string, string[]>;
    }>("/api/care-chats/unread-hints", {
      method: "POST",
      body: JSON.stringify({ viewer, ids }),
    }),

  careChatThread: (appointmentId: string, phone?: string) => {
    const q = phone ? `?phone=${encodeURIComponent(phone.replace(/\D/g, ""))}` : "";
    return request<CareChatThread>(`/api/care-chats/${encodeURIComponent(appointmentId)}${q}`);
  },

  sendCareChat: (
    appointmentId: string,
    body: { message: string; senderType: "HOSPITAL" | "DOCTOR" | "PATIENT"; senderName: string },
    phone?: string,
  ) => {
    const q = phone ? `?phone=${encodeURIComponent(phone.replace(/\D/g, ""))}` : "";
    return request<CareChatThread>(`/api/care-chats/${encodeURIComponent(appointmentId)}${q}`, {
      method: "POST",
      body: JSON.stringify(body),
    });
  },

  attachCareChat: (
    appointmentId: string,
    file: File,
    body: { senderType: "HOSPITAL" | "DOCTOR" | "PATIENT"; senderName: string; message?: string },
    phone?: string,
  ) => {
    const q = new URLSearchParams();
    if (phone) q.set("phone", phone.replace(/\D/g, ""));
    q.set("senderType", body.senderType);
    q.set("senderName", body.senderName);
    if (body.message?.trim()) q.set("message", body.message.trim());
    const form = new FormData();
    form.append("file", file, file.name);
    return requestForm<CareChatThread>(
      `/api/care-chats/${encodeURIComponent(appointmentId)}/attachments?${q}`,
      form,
    );
  },

  careChatFileUrl: (path: string, phone?: string) => {
    const q = new URLSearchParams();
    if (phone) q.set("phone", phone.replace(/\D/g, ""));
    const hospitalId = session.getHospitalId();
    if (hospitalId) q.set("hospitalId", hospitalId);
    const qs = q.toString();
    return qs ? `${path}?${qs}` : path;
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

  medicineOrders: (opts?: {
    status?: string;
    amountStatus?: string;
    q?: string;
    fulfillment?: string;
    from?: string;
    to?: string;
  }) => {
    const qs = new URLSearchParams();
    if (opts?.status) qs.set("status", opts.status);
    if (opts?.amountStatus) qs.set("amountStatus", opts.amountStatus);
    if (opts?.q) qs.set("q", opts.q);
    if (opts?.fulfillment) qs.set("fulfillment", opts.fulfillment);
    if (opts?.from) qs.set("from", opts.from);
    if (opts?.to) qs.set("to", opts.to);
    const suffix = qs.toString() ? `?${qs}` : "";
    return request<{ count: number; orders: MedicineOrder[] }>(`/api/medicine-orders${suffix}`);
  },

  medicineOrder: (id: string) =>
    request<MedicineOrder>(`/api/medicine-orders/${encodeURIComponent(id)}`),

  medicineOrderCounts: () => request<MedicineOrderCounts>("/api/medicine-orders/counts"),

  medicineOrderNotifications: () =>
    request<{ count: number; notifications: MedicineOrderNotification[] }>(
      "/api/medicine-orders/notifications",
    ),

  markMedicineNotificationRead: (id: string) =>
    request<{ ok: boolean }>(`/api/medicine-orders/notifications/${encodeURIComponent(id)}/read`, {
      method: "POST",
      body: "{}",
    }),

  createMedicineOrder: (form: FormData) =>
    requestForm<{ id: string; orderNumber: string } & MedicineOrder>("/api/medicine-orders", form),

  acceptMedicineOrder: (id: string) =>
    request<MedicineOrder>(`/api/medicine-orders/${encodeURIComponent(id)}/accept`, {
      method: "POST",
      body: "{}",
    }),

  rejectMedicineOrder: (id: string, reason: string) =>
    request<{ message: string; order: MedicineOrder }>(
      `/api/medicine-orders/${encodeURIComponent(id)}/reject`,
      { method: "POST", body: JSON.stringify({ reason }) },
    ),

  markMedicinePending: (id: string, reason: string) =>
    request<MedicineOrder>(`/api/medicine-orders/${encodeURIComponent(id)}/pending`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    }),

  saveMedicineQuote: (
    id: string,
    body: { items: MedicineOrderItemInput[]; charges: MedicineOrderChargesInput; reason?: string },
    send: boolean,
  ) =>
    request<MedicineOrder>(
      `/api/medicine-orders/${encodeURIComponent(id)}/quote?send=${send ? "true" : "false"}`,
      { method: "PUT", body: JSON.stringify(body) },
    ),

  acceptMedicineAmount: (
    id: string,
    body?: {
      requestedDays?: number;
      items?: { id?: string; quantity: number; requestedDays?: number }[];
    },
  ) =>
    request<MedicineOrder>(`/api/medicine-orders/${encodeURIComponent(id)}/amount/accept`, {
      method: "POST",
      body: JSON.stringify(body || {}),
    }),

  createMedicineRazorpayOrder: (
    id: string,
    body?: {
      requestedDays?: number;
      items?: { id?: string; quantity: number; requestedDays?: number }[];
    },
  ) =>
    request<{
      razorpayOrderId?: string;
      amount: number;
      currency: string;
      keyId?: string;
      razorpayEnabled?: boolean;
      upiId?: string;
      upiUri?: string;
      upiApps?: { id: string; name: string; uri: string }[];
      order: MedicineOrder;
    }>(`/api/medicine-orders/${encodeURIComponent(id)}/payment/razorpay/order`, {
      method: "POST",
      body: JSON.stringify(body || {}),
    }),

  verifyMedicineRazorpayPayment: (
    id: string,
    body: {
      requestedDays?: number;
      items?: { id?: string; quantity: number; requestedDays?: number }[];
      razorpayOrderId: string;
      razorpayPaymentId: string;
      razorpaySignature: string;
    },
  ) =>
    request<MedicineOrder>(`/api/medicine-orders/${encodeURIComponent(id)}/payment/razorpay/verify`, {
      method: "POST",
      body: JSON.stringify(body),
    }),

  uploadMedicinePaymentScreenshot: (id: string, form: FormData) =>
    requestForm<MedicineOrder>(
      `/api/medicine-orders/${encodeURIComponent(id)}/payment/screenshot`,
      form,
    ),

  rejectMedicineAmount: (id: string, reason: string, comments?: string) =>
    request<MedicineOrder>(`/api/medicine-orders/${encodeURIComponent(id)}/amount/reject`, {
      method: "POST",
      body: JSON.stringify({ reason, comments }),
    }),

  markMedicineReady: (id: string) =>
    request<MedicineOrder>(`/api/medicine-orders/${encodeURIComponent(id)}/ready`, {
      method: "POST",
      body: "{}",
    }),

  completeMedicineOrder: (id: string) =>
    request<MedicineOrder>(`/api/medicine-orders/${encodeURIComponent(id)}/complete`, {
      method: "POST",
      body: "{}",
    }),

  cancelMedicineOrder: (id: string, reason: string) =>
    request<MedicineOrder>(`/api/medicine-orders/${encodeURIComponent(id)}/cancel`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    }),

  releaseMedicineOrder: (id: string, reason: string) =>
    request<MedicineOrder>(`/api/medicine-orders/${encodeURIComponent(id)}/release`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    }),

  markPrescriptionUnclear: (id: string, note: string) =>
    request<{ message: string; order: MedicineOrder }>(
      `/api/medicine-orders/${encodeURIComponent(id)}/prescription-unclear`,
      { method: "POST", body: JSON.stringify({ note }) },
    ),

  uploadMedicineDocuments: (id: string, form: FormData) =>
    requestForm<MedicineOrder>(`/api/medicine-orders/${encodeURIComponent(id)}/documents`, form),

  medicalStores: (hospitalId?: string | number, activeOnly = false, allHospitals = false) => {
    const qs = new URLSearchParams();
    if (hospitalId != null && String(hospitalId)) qs.set("hospitalId", String(hospitalId));
    if (activeOnly) qs.set("activeOnly", "true");
    if (allHospitals) qs.set("allHospitals", "true");
    const suffix = qs.toString() ? `?${qs}` : "";
    return request<{ count: number; stores: MedicalStore[] }>(`/api/medical-stores${suffix}`);
  },

  registerMedicalStore: (body: {
    storeName: string;
    phone?: string;
    address?: string;
    city?: string;
    state?: string;
    password?: string;
  }) =>
    request<MedicalStore & { loginId?: string; message?: string }>("/api/medical-stores", {
      method: "POST",
      body: JSON.stringify(body),
    }),

  setMedicalStoreStatus: (id: string, status: "ACTIVE" | "INACTIVE") =>
    request<MedicalStore>(`/api/medical-stores/${encodeURIComponent(id)}/status`, {
      method: "PUT",
      body: JSON.stringify({ status }),
    }),
};

export type MedicineOrderDocument = {
  id: string;
  fileName: string;
  contentType?: string;
  latest?: boolean;
  kind?: string;
  createdAt?: string;
  previewUrl?: string;
};

export type MedicineOrderItem = {
  id?: string;
  prescribedName?: string;
  medicineName: string;
  quantity: number;
  quotedQuantity?: number;
  days?: number;
  requestedDays?: number;
  unitPrice?: number;
  lineTotal?: number;
  availability?: string;
  substituteName?: string;
  substituteReason?: string;
};

export type MedicineOrderItemInput = {
  id?: string;
  prescribedName?: string;
  medicineName: string;
  quantity: number;
  unitPrice: number;
  days?: number;
  requestedDays?: number;
  quotedQuantity?: number;
  availability?: string;
  substituteName?: string;
  substituteReason?: string;
};

export type MedicineOrderCharges = {
  deliveryCharge: number;
  packagingCharge: number;
  tax: number;
  otherCharges: number;
  discount: number;
  medicineSubtotal: number;
  grandTotal: number;
  draft?: boolean;
  quoteVersion?: number;
};

export type MedicineOrderChargesInput = {
  deliveryCharge?: number;
  packagingCharge?: number;
  tax?: number;
  otherCharges?: number;
  discount?: number;
};

export type MedicineOrder = {
  id: string;
  orderNumber: string;
  hospitalId: number;
  patientName: string;
  patientPhone: string;
  patientId?: string;
  doctorId?: string;
  doctorName?: string;
  fulfillment?: string;
  deliveryAddress?: string;
  notes?: string;
  pendingReason?: string;
  status: string;
  statusCode?: number;
  assignedStoreId?: string;
  assignedStoreName?: string;
  amount?: number | null;
  currentAmount?: number | null;
  amountVisible?: boolean;
  amountStatus?: string;
  paymentStatus?: string;
  paymentMethod?: string;
  razorpayEnabled?: boolean;
  upiId?: string;
  upiUri?: string;
  upiQrUrl?: string;
  upiApps?: { id: string; name: string; uri: string }[];
  bookedBy?: string;
  cancelReason?: string;
  cancelledAt?: string;
  completedAt?: string;
  createdAt?: string;
  prescriptionCount?: number;
  documents?: MedicineOrderDocument[];
  items?: MedicineOrderItem[];
  charges?: MedicineOrderCharges | null;
  amountHistory?: {
    version: number;
    grandTotal: number;
    status: string;
    reason?: string;
    createdAt?: string;
    createdBy?: string;
  }[];
  statusHistory?: {
    previousStatus?: string;
    newStatus: string;
    actor?: string;
    note?: string;
    createdAt?: string;
  }[];
};

export type MedicineOrderCounts = {
  newOrders: number;
  pending: number;
  inProcess: number;
  waitingApproval: number;
  amountAccepted: number;
  medicineReady: number;
  completed: number;
  canceled: number;
  unreadNotifications: number;
};

export type MedicineOrderNotification = {
  id: string;
  orderId?: string;
  title: string;
  message: string;
  read: boolean;
  createdAt?: string;
};

export type MedicalStore = {
  id: string;
  hospitalId: number;
  hospitalName?: string;
  storeCode: string;
  storeName: string;
  phone?: string;
  address?: string;
  city?: string;
  state?: string;
  status: string;
  createdAt?: string;
  loginId?: string;
  message?: string;
};

export async function fetchMedicineDocumentBlob(orderId: string, docId: string): Promise<Blob> {
  const headers: Record<string, string> = {};
  const hospitalId = session.getHospitalId();
  if (hospitalId) headers["X-Hospital-Id"] = hospitalId;
  const loginType = session.getLoginType();
  if (loginType) headers["X-Login-Type"] = loginType;
  const storeId = session.getMedicalStoreId();
  if (storeId) headers["X-Medical-Store-Id"] = storeId;
  const patientPhone = session.getPatientPhone();
  if (patientPhone) headers["X-Patient-Phone"] = patientPhone;
  const username = session.getUsername();
  if (username) headers["X-User"] = username;
  const token = session.getAccessToken();
  if (token && token.includes(".")) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(
    `/api/medicine-orders/${encodeURIComponent(orderId)}/documents/${encodeURIComponent(docId)}`,
    { headers },
  );
  if (!res.ok) {
    const text = await res.text();
    throw new Error(friendlyHttpError(res.status, text));
  }
  return res.blob();
}

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

export type HospitalPatientListRow = {
  appointmentId: string;
  hospitalId: number;
  hospitalName?: string;
  patientName: string;
  patientPhone?: string;
  patientId?: string;
  doctorId: string;
  doctorName: string;
  tokenNumber?: number;
  status?: string;
  appointmentDate?: string;
  createdDate?: string;
  createdUser?: string;
  chatCount: number;
  unreadCount?: number;
  otherMessageAts?: string[];
  chatLink?: string;
};

export type CareChatMessage = {
  id: string;
  appointmentId?: string;
  senderType: string;
  senderName?: string;
  message: string;
  documentUrl?: string;
  documentName?: string;
  createdAt?: string;
};

export type CareChatThread = {
  appointmentId: string;
  patientName?: string;
  phoneNumber?: string;
  doctorId?: string;
  hospitalId?: number;
  status?: string;
  tokenNumber?: number;
  messages: CareChatMessage[];
  s3Uploaded?: boolean;
  s3Error?: string;
};

export type PatientChatThread = {
  appointmentId: string;
  hospitalName?: string;
  patientName: string;
  doctorName?: string;
  tokenNumber?: number;
  status?: string;
  appointmentDate?: string;
  chatCount: number;
  unreadCount?: number;
  otherMessageAts?: string[];
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

export type LoginStatusLookup = {
  statusId: number;
  statusCode: string;
  statusName: string;
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
  departments?: string[];
};

export type EmergencyContext = {
  state?: string;
  city?: string;
  patientName?: string;
  patientPhone?: string;
  loginType?: string;
};

export type EmergencyHospital = {
  id?: number;
  hospitalId: number;
  hospitalName: string;
  hospitalType?: string;
  city?: string;
  state?: string;
  primaryContact?: string;
  status?: string;
  totalBeds: number;
  bookedBeds: number;
  availableBeds: number;
  emergencyDoctorsAvailable?: number;
};

export type EmergencyDoctor = {
  doctorId: string;
  doctorName: string;
  department?: string;
  specialization?: string;
  mobileNumber?: string;
  availableDays?: string;
  availableTimeSlots?: string;
  availableNow?: boolean;
  emergencyDoctor?: boolean;
};

export type EmergencyBedBooking = {
  id: string;
  hospitalId: number;
  bedId: string;
  bedNumber: string;
  patientName: string;
  patientPhone?: string;
  status: string;
  fees?: number;
  paymentId?: string;
  paymentStatus?: string;
  hasPaymentSlip?: boolean;
  paymentSlipName?: string;
  notes?: string;
  createdAt?: string;
};

export type EmergencyBed = {
  id: string;
  hospitalId: number;
  bedNumber: string;
  ward?: string;
  fees: number;
  status: string;
  notes?: string;
  booking?: EmergencyBedBooking | null;
};

export type EmergencyHospitalDetail = EmergencyHospital & {
  beds: EmergencyBed[];
  doctors: EmergencyDoctor[];
  email?: string;
  country?: string;
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
  employeeId?: string;
  employeeName?: string;
  leaveTypeId: string;
  leaveType: string;
  fromDate: string;
  toDate: string;
  days?: number;
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
  history?: LeaveRequestRow[];
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
  employeeId?: string;
  employeeName?: string;
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

export type AttendanceLog = {
  id: string;
  employeeId?: string;
  employeeName?: string;
  attendanceDate: string;
  inTime?: string | null;
  outTime?: string | null;
  totalHours?: number | null;
  status?: string;
  source?: string;
};

export type AttendanceBoard = {
  today: string;
  employeeId: string;
  employeeName?: string;
  inTime?: string | null;
  outTime?: string | null;
  status?: string | null;
  totalHours?: number | null;
  canClockIn: boolean;
  canClockOut: boolean;
  logs: AttendanceLog[];
};

export function getAttendanceBoard() {
  return request<AttendanceBoard>("/api/hrm/attendance");
}

export function clockIn() {
  return request<AttendanceLog>("/api/hrm/attendance/clock-in", {
    method: "POST",
    body: "{}",
  });
}

export function clockOut() {
  return request<AttendanceLog>("/api/hrm/attendance/clock-out", {
    method: "POST",
    body: "{}",
  });
}

export type HrmHomePerson = {
  name: string;
  initials: string;
  dateOfBirth?: string;
  daysUntil?: number;
};

export type HrmHomeHoliday = {
  id?: string;
  name: string;
  date: string;
  day?: string;
  reason?: string;
};

export type HrmPostComment = {
  id: string;
  postId: string;
  userId: string;
  userName?: string;
  body: string;
  createdAt?: string;
};

export type HrmHomeAnnouncement = {
  id: string;
  title: string;
  body: string;
  likes?: number;
  comments?: number;
  likedByMe?: boolean;
  author?: string;
  createdAt?: string;
  commentItems?: HrmPostComment[];
};

export type HrmHomeFeed = {
  today: string;
  inboxPendingCount: number;
  holidays: HrmHomeHoliday[];
  onLeaveToday: HrmHomePerson[];
  workingRemotely: HrmHomePerson[];
  attendance: AttendanceBoard;
  leaveBalances: LeaveBalanceRow[];
  departments: { id: string; name: string }[];
  announcements: HrmHomeAnnouncement[];
  birthdaysToday: HrmHomePerson[];
  upcomingBirthdays: HrmHomePerson[];
  workAnniversaryCount: number;
  newJoineeCount: number;
};

export function getHrmHome() {
  return request<HrmHomeFeed>("/api/hrm/home");
}

export function createHrmPost(body: { body: string; title?: string }) {
  return request<HrmHomeAnnouncement>("/api/hrm/posts", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function likeHrmPost(id: string) {
  return request<HrmHomeAnnouncement>(`/api/hrm/posts/${id}/like`, {
    method: "POST",
    body: "{}",
  });
}

export function commentHrmPost(id: string, body: string) {
  return request<HrmHomeAnnouncement>(`/api/hrm/posts/${id}/comments`, {
    method: "POST",
    body: JSON.stringify({ body }),
  });
}

export type HrmRights = {
  hospitalId: number;
  userId: string;
  fromSettings: boolean;
  rights: string[];
  canApprove: boolean;
  canManageHolidays?: boolean;
  catalog: string[];
};

export function getHrmRights() {
  return request<HrmRights>("/api/hrm/rights");
}

export type HrmHoliday = {
  id: string;
  name: string;
  date: string;
  day: string;
  reason?: string;
};

export function getHrmHolidays() {
  return request<HrmHoliday[]>("/api/hrm/holidays");
}

export function createHrmHoliday(body: { date: string; reason: string }) {
  return request<HrmHoliday>("/api/hrm/holidays", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function deleteHrmHoliday(id: string) {
  return request<void>(`/api/hrm/holidays/${id}`, { method: "DELETE" });
}

export function grantHrmRights(userId: string, rights: string[]) {
  return request<{ userId: string; rights: string[] }>("/api/hrm/rights", {
    method: "PUT",
    body: JSON.stringify({ userId, rights }),
  });
}

export type ApproverPending = {
  leave: LeaveRequestRow[];
  wfh: WfhRequest[];
};

export function getApproverPending() {
  return request<ApproverPending>("/api/hrm/approver/pending");
}

export function decideApproverLeave(id: string, status: string, remarks?: string) {
  return request<LeaveRequestRow>(`/api/hrm/approver/leave/${id}/decide`, {
    method: "POST",
    body: JSON.stringify({ status, remarks }),
  });
}

export function decideApproverWfh(id: string, status: string) {
  return request<WfhRequest>(`/api/hrm/approver/wfh/${id}/decide`, {
    method: "POST",
    body: JSON.stringify({ status }),
  });
}

export function submitContactInquiry(body: {
  name: string;
  email: string;
  phone?: string;
  organization?: string;
  subject: string;
  message: string;
}) {
  return request<{ ok: boolean; inquiryId: string; emailed?: boolean; message: string }>("/api/contact", {
    method: "POST",
    body: JSON.stringify(body),
  });
}
