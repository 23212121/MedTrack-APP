import { useEffect, useMemo, useState } from "react";
import { api } from "./api";

export type CareChatRole = "HOSPITAL" | "DOCTOR" | "PATIENT";

const STORAGE_KEY = "medtrack.careChat.lastRead";
export const CARE_CHAT_READ_EVENT = "medtrack-care-chat-read";

function lastReadKey(appointmentId: string, role: string) {
  return `${appointmentId}:${role.toUpperCase()}`;
}

function readMap(): Record<string, number> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as Record<string, number>;
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

export function getCareChatLastRead(appointmentId: string, role: string): number | null {
  const value = readMap()[lastReadKey(appointmentId, role)];
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

export function markCareChatRead(appointmentId: string, role: string) {
  if (!appointmentId || !role) return;
  const map = readMap();
  map[lastReadKey(appointmentId, role)] = Date.now();
  localStorage.setItem(STORAGE_KEY, JSON.stringify(map));
  window.dispatchEvent(new CustomEvent(CARE_CHAT_READ_EVENT, { detail: { appointmentId, role } }));
}

/** Unread = messages from others after last open. No last-read → all others' messages. */
export function unreadFromOtherAts(
  appointmentId: string,
  role: string,
  otherAts?: string[] | null,
  fallbackCount?: number,
): number {
  const lastRead = getCareChatLastRead(appointmentId, role);
  if (Array.isArray(otherAts)) {
    if (!lastRead) return otherAts.length;
    return otherAts.filter((stamp) => {
      const ms = Date.parse(stamp);
      return Number.isFinite(ms) && ms > lastRead;
    }).length;
  }
  const fallback = fallbackCount ?? 0;
  if (fallback <= 0) return 0;
  return lastRead ? 0 : fallback;
}

export function useCareChatUnread(
  appointmentIds: string[],
  role: CareChatRole,
  preloaded?: Record<string, string[] | undefined>,
  fallbackCounts?: Record<string, number | undefined>,
) {
  const [hints, setHints] = useState<Record<string, string[]>>({});
  const [tick, setTick] = useState(0);
  const idKey = appointmentIds.filter(Boolean).join(",");
  const hasPreload = Boolean(preloaded && Object.values(preloaded).some((value) => Array.isArray(value)));
  const hasFallback = Boolean(
    fallbackCounts && Object.values(fallbackCounts).some((value) => value != null),
  );

  useEffect(() => {
    if (hasPreload || hasFallback) return;
    const ids = idKey.split(",").filter(Boolean);
    if (ids.length === 0) {
      setHints({});
      return;
    }
    let cancelled = false;
    api
      .careChatUnreadHints(ids, role)
      .then((data) => {
        if (!cancelled) setHints(data.otherMessageAts || {});
      })
      .catch(async () => {
        if (ids.length > 25) {
          if (!cancelled) setHints({});
          return;
        }
        const next: Record<string, string[]> = {};
        await Promise.all(
          ids.map(async (id) => {
            try {
              const thread = await api.careChatThread(id);
              next[id] = (thread.messages || [])
                .filter((msg) => msg.senderType && msg.senderType.toUpperCase() !== role)
                .map((msg) => msg.createdAt || "")
                .filter(Boolean);
            } catch {
              next[id] = [];
            }
          }),
        );
        if (!cancelled) setHints(next);
      });
    return () => {
      cancelled = true;
    };
  }, [idKey, role, hasPreload, hasFallback]);

  useEffect(() => {
    const onRead = () => setTick((n) => n + 1);
    window.addEventListener(CARE_CHAT_READ_EVENT, onRead);
    return () => window.removeEventListener(CARE_CHAT_READ_EVENT, onRead);
  }, []);

  return useMemo(() => {
    const out: Record<string, number> = {};
    for (const id of idKey.split(",").filter(Boolean)) {
      out[id] = unreadFromOtherAts(id, role, preloaded?.[id] ?? hints[id], fallbackCounts?.[id]);
    }
    return out;
  }, [idKey, role, preloaded, hints, fallbackCounts, tick]);
}
