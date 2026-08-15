/**
 * Auth helpers — thin wrappers over the MedTrack session DL.
 * Prefer `import { session } from "./dl/MedTrackSession"` for hospitalId etc.
 */
import { session, type MedTrackSessionData } from "./dl/MedTrackSession";

export type MedtrackSession = MedTrackSessionData;
export { session };

export function isLoggedIn(): boolean {
  return session.isAuthenticated();
}

export function isPatientLoggedIn(): boolean {
  return session.isPatient();
}

export function getPatientPhone(): string {
  return session.getPatientPhone();
}

export function getPatientId(): string {
  return session.getPatientId();
}

export function getSession(): MedTrackSessionData | null {
  return session.get();
}

export function getHospitalId(): string {
  return session.getHospitalId();
}

export function checkAuth(username: string, password: string): boolean {
  return session.checkAuth(username, password);
}

export function login(
  username: string,
  password: string,
  hospitalId: string | number,
  hospitalName?: string,
  hospitalCode?: string,
  extras?: {
    loginType?: string;
    userId?: string;
    role?: string;
    patientPhone?: string;
    patientName?: string;
    patientId?: string;
  },
): void {
  session.login({
    username,
    password,
    hospitalId,
    hospitalName,
    hospitalCode,
    loginType: extras?.loginType,
    userId: extras?.userId,
    role: extras?.role,
    patientId: extras?.patientId,
    patientPhone: extras?.patientPhone,
    patientName: extras?.patientName,
  });
}

export function logout(): void {
  session.logout();
}

/** Routes that render outside the logged-in app shell (no sidebar). */
export const PUBLIC_PATHS = new Set([
  "/login",
  "/logout",
  "/forgot-password",
  "/hospital-register",
  "/book", // patient booking — no login required
  "/queue", // patient queue board — no login required
  "/track", // track appointment by mobile — no login required
  "/download-file", // download a direct file URL
  "/patient/login",
  "/patient/register",
]);

export function isPublicPath(pathname: string): boolean {
  if (PUBLIC_PATHS.has(pathname)) return true;
  // /book/10001 style share links
  return pathname.startsWith("/book/");
}
