/**
 * Client session DL — holds authenticated user credentials and hospital context.
 * Use anywhere: import { session } from "../dl/MedTrackSession"
 *
 *   session.getUsername()
 *   session.getPassword()
 *   session.getHospitalId()
 *   session.checkAuth(username, password)
 */

const STORAGE_KEY = "medtrack_session";
const TOKEN_KEY = "medtrack_token";

export type MedTrackSessionData = {
  username: string;
  password: string;
  hospitalId: string;
  hospitalName?: string;
  hospitalCode?: string;
  /** HOSPITAL | USER | PATIENT */
  loginType?: string;
  /** Doctor ID when loginType is USER; hospital ID when HOSPITAL; USR… User ID when PATIENT */
  userId?: string;
  role?: string;
  /** Same as userId for PATIENT logins (USR000001). Legacy UUID for old phone-only accounts. */
  patientId?: string;
  /** Patient phone when loginType is PATIENT */
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
      if (!data.patientPhone) return null;
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
  localStorage.setItem(TOKEN_KEY, `mt-${data.hospitalId}-${Date.now()}`);
  // Mirror into sessionStorage so same-tab APIs can read quickly
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
    // legacy keys
    localStorage.removeItem("medtrack_user");
    sessionStorage.removeItem(STORAGE_KEY);
    sessionStorage.clear();
  } catch {
    // ignore
  }
}

/** Singleton session DL — usable from any page / module. */
export const session = {
  /** Full session payload (or null if logged out). */
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

  getPatientPhone(): string {
    return read()?.patientPhone ?? "";
  },

  /** Logged-in patient identifier — User ID (USR000001) when registered via user_details. */
  getPatientId(): string {
    const s = read();
    if (!s) return "";
    if (s.loginType === "PATIENT" && s.userId) {
      return s.userId;
    }
    return s.patientId ?? s.userId ?? "";
  },

  getPatientName(): string {
    return read()?.patientName ?? read()?.username ?? "";
  },

  /** Hospital id for the logged-in tenant — use on APIs / filters. */
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

  /**
   * Verify credentials against the stored session
   * (after login has populated the DL).
   */
  checkAuth(username: string, password: string): boolean {
    const s = read();
    if (!s) return false;
    return (
      s.username === username.trim() &&
      s.password === password &&
      Boolean(s.hospitalId)
    );
  },

  getLoginType(): string {
    return read()?.loginType ?? "";
  },

  getUserId(): string {
    return read()?.userId ?? "";
  },

  getRole(): string {
    return read()?.role ?? "";
  },

  /** Create / replace the session after a successful login. */
  login(input: {
    username: string;
    password: string;
    hospitalId?: string | number;
    hospitalName?: string;
    hospitalCode?: string;
    loginType?: string;
    userId?: string;
    role?: string;
    patientId?: string;
    patientPhone?: string;
    patientName?: string;
  }): MedTrackSessionData {
    const isPatient = input.loginType === "PATIENT";
    const data: MedTrackSessionData = {
      username: String(input.username).trim(),
      password: String(input.password),
      hospitalId: isPatient ? "0" : String(input.hospitalId ?? "").trim(),
      hospitalName: input.hospitalName?.trim() || undefined,
      hospitalCode: input.hospitalCode?.trim() || undefined,
      loginType: input.loginType?.trim() || undefined,
      userId: input.userId?.trim() || undefined,
      role: input.role?.trim() || undefined,
      patientId:
        input.loginType === "PATIENT"
          ? input.userId?.trim() || input.patientId?.trim() || undefined
          : input.patientId?.trim() || input.userId?.trim() || undefined,
      patientPhone: input.patientPhone?.replace(/\D/g, "") || undefined,
      patientName: input.patientName?.trim() || undefined,
      loggedInAt: new Date().toISOString(),
    };
    if (!data.username || !data.password) {
      throw new Error("username and password are required");
    }
    if (!isPatient && !data.hospitalId) {
      throw new Error("hospitalId is required for staff login");
    }
    if (isPatient && !data.patientPhone && !data.userId && !data.patientId) {
      throw new Error("patientId, userId, or patientPhone is required for patient login");
    }
    write(data);
    return data;
  },

  /** Update hospital context without changing credentials. */
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
