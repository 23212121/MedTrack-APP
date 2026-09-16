/**
 * Client session DL — holds authenticated user credentials and hospital context.
 * Use anywhere: import { session } from "../dl/MedTrackSession"
 */

const STORAGE_KEY = "medtrack_session";
const TOKEN_KEY = "medtrack_token";

export type MedTrackSessionData = {
  username: string;
  password: string;
  hospitalId: string;
  hospitalName?: string;
  hospitalCode?: string;
  /** HOSPITAL | USER | PATIENT | MEDICAL */
  loginType?: string;
  /** Doctor ID when loginType is USER; hospital ID when HOSPITAL; USR… when PATIENT; store id when MEDICAL */
  userId?: string;
  medicalStoreId?: string;
  role?: string;
  accessToken?: string;
  /** Same as userId for PATIENT logins (USR000001). Legacy UUID for old phone-only accounts. */
  patientId?: string;
  patientPhone?: string;
  patientName?: string;
  loggedInAt: string;
};

function read(): MedTrackSessionData | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const data = JSON.parse(raw) as MedTrackSessionData;
    if (!data?.username || !data?.password) return null;
    if (data.loginType === "PATIENT") {
      if (!data.patientPhone && !data.userId && !data.patientId) return null;
      return data;
    }
    if (!data?.hospitalId) return null;
    return data;
  } catch {
    return null;
  }
}

function write(data: MedTrackSessionData): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  const token = data.accessToken || `mt-${data.hospitalId || data.userId || "local"}-${Date.now()}`;
  localStorage.setItem(TOKEN_KEY, token);
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(data));
  } catch {
    // ignore
  }
}

function clear(): void {
  try {
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem("medtrack_user");
    sessionStorage.removeItem(STORAGE_KEY);
    sessionStorage.clear();
  } catch {
    // ignore
  }
}

export const session = {
  get(): MedTrackSessionData | null {
    return read();
  },

  isAuthenticated(): boolean {
    const s = read();
    if (!s?.username || !s?.password) return false;
    if (s.loginType === "PATIENT") {
      return Boolean(s.patientPhone || s.userId || s.patientId);
    }
    return Boolean(s.hospitalId);
  },

  getUsername(): string {
    return read()?.username ?? "";
  },

  getPassword(): string {
    return read()?.password ?? "";
  },

  isPatient(): boolean {
    return read()?.loginType === "PATIENT";
  },

  isDoctor(): boolean {
    return read()?.loginType === "USER";
  },

  isMedical(): boolean {
    return read()?.loginType === "MEDICAL";
  },

  getMedicalStoreId(): string {
    return read()?.medicalStoreId ?? (read()?.loginType === "MEDICAL" ? read()?.userId ?? "" : "");
  },

  getLoginType(): string {
    return read()?.loginType ?? "";
  },

  getUserId(): string {
    return read()?.userId ?? "";
  },

  getAccessToken(): string {
    const s = read();
    if (s?.accessToken) return s.accessToken;
    try {
      return localStorage.getItem(TOKEN_KEY) ?? "";
    } catch {
      return "";
    }
  },

  getPatientPhone(): string {
    return read()?.patientPhone ?? "";
  },

  getPatientId(): string {
    const s = read();
    if (!s) return "";
    if (s.loginType === "PATIENT" && s.userId) return s.userId;
    return s.patientId ?? s.userId ?? "";
  },

  getPatientName(): string {
    return read()?.patientName ?? read()?.username ?? "";
  },

  getHospitalId(): string {
    return read()?.hospitalId ?? "";
  },

  getHospitalIdNumber(): number | null {
    const id = read()?.hospitalId;
    if (!id) return null;
    const n = Number(id);
    return Number.isFinite(n) ? n : null;
  },

  getHospitalName(): string {
    return read()?.hospitalName ?? "";
  },

  getHospitalCode(): string {
    return read()?.hospitalCode ?? "";
  },

  checkAuth(username: string, password: string): boolean {
    const s = read();
    if (!s) return false;
    return s.username === username.trim() && s.password === password;
  },

  login(input: {
    username: string;
    password: string;
    hospitalId?: string | number;
    hospitalName?: string;
    hospitalCode?: string;
    loginType?: string;
    userId?: string;
    role?: string;
    accessToken?: string;
    patientId?: string;
    patientPhone?: string;
    patientName?: string;
    medicalStoreId?: string;
  }): MedTrackSessionData {
    const isPatient = input.loginType === "PATIENT";
    const data: MedTrackSessionData = {
      username: String(input.username).trim(),
      password: String(input.password),
      hospitalId: input.hospitalId === undefined || input.hospitalId === null
        ? ""
        : String(input.hospitalId).trim(),
      hospitalName: input.hospitalName?.trim() || undefined,
      hospitalCode: input.hospitalCode?.trim() || undefined,
      loginType: input.loginType?.trim() || undefined,
      userId: input.userId?.trim() || undefined,
      role: input.role?.trim() || undefined,
      accessToken: input.accessToken?.trim() || undefined,
      patientId: isPatient
        ? input.userId?.trim() || input.patientId?.trim() || undefined
        : input.patientId?.trim() || input.userId?.trim() || undefined,
      patientPhone: input.patientPhone?.trim() || undefined,
      patientName: input.patientName?.trim() || undefined,
      medicalStoreId: input.medicalStoreId?.trim() || undefined,
      loggedInAt: new Date().toISOString(),
    };
    if (!data.username || !data.password) {
      throw new Error("username and password are required");
    }
    if (isPatient && !data.patientPhone && !data.userId && !data.patientId) {
      throw new Error("patientId, userId, or patientPhone is required for patient login");
    }
    if (!isPatient && !data.hospitalId) {
      throw new Error("username, password and hospitalId are required");
    }
    write(data);
    return data;
  },

  setHospital(hospitalId: string | number, hospitalName?: string, hospitalCode?: string): void {
    const s = read();
    if (!s) throw new Error("Not authenticated");
    write({
      ...s,
      hospitalId: String(hospitalId).trim(),
      hospitalName: hospitalName?.trim() || s.hospitalName,
      hospitalCode: hospitalCode?.trim() || s.hospitalCode,
    });
  },

  logout(): void {
    clear();
  },
};

export default session;
