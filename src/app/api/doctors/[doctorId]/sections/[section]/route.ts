import { NextRequest, NextResponse } from "next/server";

const BOOKING_SERVICE_URL =
  process.env.BOOKING_SERVICE_URL || "http://localhost:8084";

type Ctx = { params: Promise<{ doctorId: string; section: string }> };

async function proxy(path: string, init?: RequestInit) {
  try {
    const upstream = await fetch(`${BOOKING_SERVICE_URL}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...(init?.headers || {}),
      },
    });
    const text = await upstream.text();
    let data: unknown = text;
    try {
      data = text ? JSON.parse(text) : {};
    } catch {
      data = { error: text || upstream.statusText };
    }
    return NextResponse.json(data, { status: upstream.status });
  } catch (err) {
    const message =
      err instanceof Error ? err.message : "Could not reach booking-service";
    return NextResponse.json(
      { error: `${message}. Is booking-service running on :8084?` },
      { status: 502 }
    );
  }
}

export async function GET(_req: NextRequest, ctx: Ctx) {
  const { doctorId, section } = await ctx.params;
  return proxy(`/api/doctors/${doctorId}/sections/${section}`);
}

export async function PUT(req: NextRequest, ctx: Ctx) {
  const { doctorId, section } = await ctx.params;
  const body = await req.text();
  return proxy(`/api/doctors/${doctorId}/sections/${section}`, {
    method: "PUT",
    body,
  });
}
