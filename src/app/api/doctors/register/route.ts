import { NextRequest, NextResponse } from "next/server";

const BOOKING_SERVICE_URL =
  process.env.BOOKING_SERVICE_URL || "http://localhost:8084";

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
      err instanceof Error
        ? err.message
        : "Could not reach booking-service";
    return NextResponse.json(
      { error: `${message}. Is booking-service running on :8084?` },
      { status: 502 }
    );
  }
}

/** POST /api/doctors/register → start wizard (init doctor_id). */
export async function POST() {
  return proxy("/api/doctors/init", { method: "POST", body: "{}" });
}
