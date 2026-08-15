import { NextRequest } from "next/server";
import { z } from "zod";
import { prisma } from "@/lib/prisma";
import {
  createSessionToken,
  setSessionCookie,
  verifyPassword,
} from "@/lib/auth";
import { handleApiError, jsonError, jsonOk } from "@/lib/api";

const schema = z.object({
  email: z.string().email().optional(),
  phone: z.string().optional(),
  password: z.string().min(1),
});

export async function POST(req: NextRequest) {
  try {
    const body = schema.parse(await req.json());
    if (!body.email && !body.phone) {
      return jsonError("Email or phone is required");
    }

    const user = await prisma.user.findFirst({
      where: {
        isActive: true,
        ...(body.email ? { email: body.email } : { phone: body.phone }),
      },
    });

    if (!user?.passwordHash) {
      return jsonError("Invalid credentials", 401);
    }

    const valid = await verifyPassword(body.password, user.passwordHash);
    if (!valid) return jsonError("Invalid credentials", 401);

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

    return jsonOk({ user: session });
  } catch (err) {
    return handleApiError(err);
  }
}
