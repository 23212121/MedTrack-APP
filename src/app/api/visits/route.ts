import { NextRequest } from "next/server";
import { z } from "zod";
import { UserRole, VisitStatus } from "@prisma/client";
import { requireSession } from "@/lib/auth";
import { prisma } from "@/lib/prisma";
import { createVisitWithTimeline } from "@/lib/notifications";
import { handleApiError, jsonError, jsonOk } from "@/lib/api";

const createSchema = z.object({
  patientId: z.string().min(1),
  doctorId: z.string().optional(),
  departmentId: z.string().optional(),
  reason: z.string().optional(),
  staffNote: z.string().optional(),
  status: z.enum(["CALLED", "BOOKED"]).default("BOOKED"),
  scheduledStart: z.string().optional(),
  scheduledEnd: z.string().optional(),
});

export async function GET(req: NextRequest) {
  try {
    const session = await requireSession([
      UserRole.EMPLOYEE,
      UserRole.ADMIN,
      UserRole.DOCTOR,
      UserRole.PATIENT,
    ]);

    const patientId = req.nextUrl.searchParams.get("patientId");

    if (session.role === UserRole.PATIENT) {
      const patient = await prisma.patient.findFirst({
        where: { userId: session.id },
      });
      if (!patient) return jsonOk({ visits: [] });
      const visits = await prisma.visit.findMany({
        where: { patientId: patient.id },
        orderBy: { createdAt: "desc" },
        include: {
          doctor: { include: { user: { select: { fullName: true } } } },
          events: { orderBy: { createdAt: "asc" } },
        },
      });
      return jsonOk({ visits });
    }

    // Doctors: always load by their doctorId so public patient bookings appear
    if (session.role === UserRole.DOCTOR) {
      const doctor = await prisma.doctor.findFirst({
        where: { userId: session.id },
      });
      if (!doctor) return jsonOk({ visits: [] });

      const openOnly = req.nextUrl.searchParams.get("open") === "1";
      const visits = await prisma.visit.findMany({
        where: {
          doctorId: doctor.id,
          ...(patientId ? { patientId } : {}),
          ...(openOnly
            ? {
                status: {
                  in: [
                    VisitStatus.CALLED,
                    VisitStatus.BOOKED,
                    VisitStatus.CHECKED_IN,
                    VisitStatus.IN_CONSULT,
                  ],
                },
              }
            : {}),
        },
        orderBy: [
          { scheduledStart: "asc" },
          { tokenNumber: "asc" },
          { createdAt: "desc" },
        ],
        take: 80,
        include: {
          patient: true,
          doctor: { include: { user: { select: { fullName: true } } } },
          events: { orderBy: { createdAt: "asc" }, take: 10 },
        },
      });

      return jsonOk({
        visits: visits.map((v) => ({
          ...v,
          isPatientBooking: v.createdByUserId == null,
        })),
      });
    }

    const doctorId = req.nextUrl.searchParams.get("doctorId");

    const where: {
      clinicId: string;
      patientId?: string;
      doctorId?: string;
      status?: { in: VisitStatus[] };
    } = { clinicId: session.clinicId! };

    if (patientId) where.patientId = patientId;
    if (doctorId) where.doctorId = doctorId;

    const openOnly = req.nextUrl.searchParams.get("open") === "1";
    if (openOnly) {
      where.status = {
        in: [
          VisitStatus.CALLED,
          VisitStatus.BOOKED,
          VisitStatus.CHECKED_IN,
          VisitStatus.IN_CONSULT,
        ],
      };
    }

    const visits = await prisma.visit.findMany({
      where,
      orderBy: [
        { scheduledStart: "asc" },
        { tokenNumber: "asc" },
        { createdAt: "desc" },
      ],
      take: 80,
      include: {
        patient: true,
        doctor: { include: { user: { select: { fullName: true } } } },
        events: { orderBy: { createdAt: "asc" }, take: 10 },
      },
    });

    return jsonOk({
      visits: visits.map((v) => ({
        ...v,
        isPatientBooking: v.createdByUserId == null,
      })),
    });
  } catch (err) {
    return handleApiError(err);
  }
}

export async function POST(req: NextRequest) {
  try {
    const session = await requireSession([UserRole.EMPLOYEE, UserRole.ADMIN]);
    const body = createSchema.parse(await req.json());

    const patient = await prisma.patient.findFirst({
      where: { id: body.patientId, clinicId: session.clinicId! },
    });
    if (!patient) return jsonError("Patient not found", 404);

    if (body.status === "BOOKED" && !body.doctorId) {
      return jsonError("doctorId is required when status is BOOKED");
    }

    let departmentId = body.departmentId;
    if (body.doctorId) {
      const doctor = await prisma.doctor.findFirst({
        where: { id: body.doctorId, clinicId: session.clinicId! },
      });
      if (!doctor) return jsonError("Doctor not found", 404);
      departmentId = departmentId ?? doctor.departmentId ?? undefined;
    }

    const status = body.status as VisitStatus;
    const visit = await createVisitWithTimeline(
      {
        clinic: { connect: { id: session.clinicId! } },
        patient: { connect: { id: patient.id } },
        ...(body.doctorId
          ? { doctor: { connect: { id: body.doctorId } } }
          : {}),
        ...(departmentId
          ? { department: { connect: { id: departmentId } } }
          : {}),
        status,
        reason: body.reason,
        scheduledStart: body.scheduledStart
          ? new Date(body.scheduledStart)
          : null,
        scheduledEnd: body.scheduledEnd ? new Date(body.scheduledEnd) : null,
        createdBy: { connect: { id: session.id } },
      },
      session.id,
      body.staffNote
    );

    return jsonOk({ visit }, 201);
  } catch (err) {
    return handleApiError(err);
  }
}
