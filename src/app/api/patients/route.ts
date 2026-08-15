import { NextRequest } from "next/server";
import { z } from "zod";
import { UserRole } from "@prisma/client";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import { handleApiError, jsonOk } from "@/lib/api";

const createSchema = z.object({
  phone: z.string().min(8),
  fullName: z.string().min(2),
  dateOfBirth: z.string().optional(),
  gender: z.enum(["MALE", "FEMALE", "OTHER", "UNKNOWN"]).optional(),
  email: z.string().email().optional().or(z.literal("")),
  smsConsent: z.boolean().optional(),
  emailConsent: z.boolean().optional(),
});

export async function POST(req: NextRequest) {
  try {
    const session = await requireSession([UserRole.EMPLOYEE, UserRole.ADMIN]);
    const body = createSchema.parse(await req.json());
    const phone = body.phone.replace(/\D/g, "");

    const patient = await prisma.patient.create({
      data: {
        clinicId: session.clinicId!,
        phone,
        fullName: body.fullName,
        dateOfBirth: body.dateOfBirth ? new Date(body.dateOfBirth) : null,
        gender: body.gender ?? "UNKNOWN",
        email: body.email || null,
        smsConsent: body.smsConsent ?? true,
        emailConsent: body.emailConsent ?? true,
      },
    });

    return jsonOk({ patient }, 201);
  } catch (err) {
    return handleApiError(err);
  }
}
