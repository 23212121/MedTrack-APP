import bcrypt from "bcryptjs";
import { SignJWT, jwtVerify } from "jose";
import { cookies } from "next/headers";
import { NextRequest } from "next/server";
import { UserRole } from "@prisma/client";
import { prisma } from "./prisma";

const COOKIE_NAME = "mvt_session";
const SESSION_DAYS = 7;

export type SessionUser = {
  id: string;
  email: string | null;
  phone: string | null;
  fullName: string;
  role: UserRole;
  clinicId: string | null;
};

function getSecret() {
  const secret = process.env.JWT_SECRET;
  if (!secret) {
    throw new Error("JWT_SECRET is not set");
  }
  return new TextEncoder().encode(secret);
}

export async function hashPassword(password: string) {
  return bcrypt.hash(password, 10);
}

export async function verifyPassword(password: string, hash: string) {
  return bcrypt.compare(password, hash);
}

export async function createSessionToken(user: SessionUser) {
  return new SignJWT({
    sub: user.id,
    email: user.email,
    phone: user.phone,
    fullName: user.fullName,
    role: user.role,
    clinicId: user.clinicId,
  })
    .setProtectedHeader({ alg: "HS256" })
    .setIssuedAt()
    .setExpirationTime(`${SESSION_DAYS}d`)
    .sign(getSecret());
}

export async function setSessionCookie(token: string) {
  const jar = await cookies();
  jar.set(COOKIE_NAME, token, {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: SESSION_DAYS * 24 * 60 * 60,
  });
}

export async function clearSessionCookie() {
  const jar = await cookies();
  jar.delete(COOKIE_NAME);
}

export async function readSessionFromToken(
  token: string | undefined
): Promise<SessionUser | null> {
  if (!token) return null;
  try {
    const { payload } = await jwtVerify(token, getSecret());
    if (!payload.sub || !payload.role) return null;
    return {
      id: String(payload.sub),
      email: (payload.email as string | null) ?? null,
      phone: (payload.phone as string | null) ?? null,
      fullName: String(payload.fullName ?? ""),
      role: payload.role as UserRole,
      clinicId: (payload.clinicId as string | null) ?? null,
    };
  } catch {
    return null;
  }
}

export async function getSession(): Promise<SessionUser | null> {
  const jar = await cookies();
  return readSessionFromToken(jar.get(COOKIE_NAME)?.value);
}

export async function getSessionFromRequest(
  req: NextRequest
): Promise<SessionUser | null> {
  return readSessionFromToken(req.cookies.get(COOKIE_NAME)?.value);
}

export async function requireSession(
  roles?: UserRole[]
): Promise<SessionUser> {
  const session = await getSession();
  if (!session) {
    throw new AuthError("Unauthorized", 401);
  }
  if (roles && !roles.includes(session.role)) {
    throw new AuthError("Forbidden", 403);
  }
  if (!session.clinicId && session.role !== UserRole.ADMIN) {
    throw new AuthError("User is not assigned to a clinic", 403);
  }
  return session;
}

export class AuthError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

export async function loadActiveUser(userId: string) {
  return prisma.user.findFirst({
    where: { id: userId, isActive: true },
  });
}
