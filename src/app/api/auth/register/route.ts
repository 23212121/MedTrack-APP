import { NextRequest } from "next/server";
import { z } from "zod";
import { UserRole } from "@prisma/client";
import { prisma } from "@/lib/prisma";
import {
  createSessionToken,
  hashPassword,
  setSessionCookie,
} from "@/lib/auth";
import { handleApiError, jsonError, jsonOk } from "@/lib/api";

const schema = z.object({
  fullName: z.string().min(2),
  email: z.string().email().optional(),
  phone: z.string().min(8).optional(),
  password: z.string().min(8),
  role: z.enum(["EMPLOYEE", "DOCTOR", "PATIENT"]),
  clinicId: z.string().optional(),
  dateOfBirth: z.string().optional(),
  gender: z.enum(["MALE", "FEMALE", "OTHER", "UNKNOWN"]).optional(),
});

export async function POST(req: NextRequest) {
  try {
    const body = schema.parse(await req.json());

    if (!body.email && !body.phone) {
      return jsonError("Email or phone is required");
    }

    if (body.email) {
      const existing = await prisma.user.findUnique({
        where: { email: body.email },
      });
      if (existing) return jsonError("Email already registered", 409);
    }

    let clinicId = body.clinicId;
    if (!clinicId) {
      const clinic = await prisma.clinic.findFirst({ orderBy: { createdAt: "asc" } });
      if (!clinic) return jsonError("No clinic configured. Run db:seed first.", 400);
      clinicId = clinic.id;
    }

    const passwordHash = await hashPassword(body.password);
    const role = body.role as UserRole;

    const user = await prisma.user.create({
      data: {
        fullName: body.fullName,
        email: body.email,
        phone: body.phone,
        passwordHash,
        role,
        clinicId,
        ...(role === UserRole.DOCTOR
          ? {
              doctorProfile: {
                create: {
                  clinicId,
                  specialty: "General",
                },
              },
            }
          : {}),
        ...(role === UserRole.PATIENT && body.phone
          ? {
              patientProfile: {
                create: {
                  clinicId,
                  phone: body.phone,
                  fullName: body.fullName,
                  email: body.email,
                  dateOfBirth: body.dateOfBirth
                    ? new Date(body.dateOfBirth)
                    : undefined,
                  gender: body.gender ?? "UNKNOWN",
                },
              },
            }
          : {}),
      },
    });

    const session = {
      id: user.id,
      email: user.email,
      phone: user.phone,
      fullName: user.fullName,
      role: user.role,
      clinicId: user.clinicId,
    };
    const token = await createSessionToken(session);
    await setSessionCookie(token);

    return jsonOk({ user: session }, 201);
  } catch (err) {
    return handleApiError(err);
  }
}
