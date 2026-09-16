import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  HrmHomeAnnouncement,
  HrmHomeFeed,
  HrmHomeHoliday,
  HrmHomePerson,
  LeaveBalanceRow,
  clockIn,
  clockOut,
  createHrmHoliday,
  createHrmPost,
  commentHrmPost,
  getAttendanceBoard,
  getHrmHome,
  getHrmInbox,
  getHrmRights,
  getLeaveSummary,
  likeHrmPost,
  requestWorkFromHome,
} from "../../api";
import { toast } from "../../toast";
import HolidayModal from "./HolidayModal";

const RING_COLORS = ["#14b8a6", "#38bdf8", "#a78bfa"];
const AVATAR_COLORS = ["#14b8a6", "#0ea5e9", "#6366f1", "#0f766e", "#8b5cf6"];

function pad(n: number) {
  return String(n).padStart(2, "0");
}

function dayCount(from: string, to: string) {
  if (!from || !to) return 0;
  const a = new Date(from + "T00:00:00");
  const b = new Date(to + "T00:00:00");
  if (Number.isNaN(a.getTime()) || Number.isNaN(b.getTime()) || b < a) return 0;
  return Math.floor((b.getTime() - a.getTime()) / 86400000) + 1;
}

function formatHolidayDate(iso: string) {
  const d = new Date(iso + "T00:00:00");
  if (Number.isNaN(d.getTime())) return iso;
  const weekday = d.toLocaleDateString(undefined, { weekday: "short" });
  const month = d.toLocaleDateString(undefined, { month: "long" });
  return `${weekday}, ${pad(d.getDate())} ${month} ${d.getFullYear()}`;
}

function BalanceRing({
  value,
  label,
  color,
}: {
  value: number;
  label: string;
  color: string;
}) {
  const pct = Math.max(0, Math.min(100, value <= 0 ? 0 : Math.min(100, value * 4)));
  const r = 28;
  const c = 2 * Math.PI * r;
  const dash = (pct / 100) * c;
  const text = Number.isInteger(value) ? String(value) : value.toFixed(2);
  return (
    <div className="hrm-home-ring">
      <svg viewBox="0 0 72 72" width="72" height="72" aria-hidden="true">
        <circle cx="36" cy="36" r={r} fill="none" stroke="#e2e8f0" strokeWidth="7" />
        <circle
          cx="36"
          cy="36"
          r={r}
          fill="none"
          stroke={color}
          strokeWidth="7"
          strokeLinecap="round"
          strokeDasharray={`${dash} ${c - dash}`}
          transform="rotate(-90 36 36)"
        />
        <text x="36" y="40" textAnchor="middle" className="hrm-home-ring-val">
          {text}
        </text>
      </svg>
      <span>{label}</span>
    </div>
  );
}

function Avatar({ person, index }: { person: HrmHomePerson; index: number }) {
  return (
    <span
      className="hrm-home-avatar"
      title={person.name}
      style={{ background: AVATAR_COLORS[index % AVATAR_COLORS.length] }}
    >
      {person.initials || "TM"}
    </span>
  );
}

function InboxArt() {
  return (
    <svg className="hrm-home-art" viewBox="0 0 120 80" aria-hidden="true">
      <circle cx="88" cy="28" r="18" fill="#fde68a" opacity="0.7" />
      <rect x="18" y="28" width="52" height="36" rx="8" fill="#e0f2fe" />
      <circle cx="44" cy="40" r="10" fill="#bae6fd" />
      <path d="M34 62c4-8 16-8 20 0" fill="#7dd3fc" />
    </svg>
  );
}

function HolidayArt() {
  return (
    <svg className="hrm-home-art hrm-home-art--portrait" viewBox="0 0 90 90" aria-hidden="true">
      <circle cx="45" cy="45" r="42" fill="#ecfdf5" />
      <path
        d="M45 22c8 0 14 8 14 18 0 6-3 11-7 14 10 3 16 10 16 18H22c0-8 6-15 16-18-4-3-7-8-7-14 0-10 6-18 14-18z"
        fill="none"
        stroke="#0d9488"
        strokeWidth="1.6"
      />
    </svg>
  );
}

function LeaveArt() {
  return (
    <svg className="hrm-home-art" viewBox="0 0 120 70" aria-hidden="true">
      <rect x="20" y="22" width="80" height="36" rx="10" fill="#ecfeff" />
      <circle cx="48" cy="40" r="10" fill="#a5f3fc" />
      <rect x="64" y="32" width="28" height="6" rx="3" fill="#67e8f9" />
      <rect x="64" y="42" width="20" height="6" rx="3" fill="#a5f3fc" />
    </svg>
  );
}

function AssetArt() {
  return (
    <svg className="hrm-home-announce-art" viewBox="0 0 88 88" aria-hidden="true">
      <rect width="88" height="88" rx="16" fill="#eef2ff" />
      <circle cx="32" cy="40" r="10" fill="#c7d2fe" />
      <path d="M22 62c4-10 16-10 20 0" fill="#a5b4fc" />
      <rect x="50" y="28" width="22" height="28" rx="4" fill="#ddd6fe" />
    </svg>
  );
}

export default function HrmHomePage() {
  const [now, setNow] = useState(() => new Date());
  const [feed, setFeed] = useState<HrmHomeFeed | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [holidayIdx, setHolidayIdx] = useState(0);
  const [space, setSpace] = useState("org");
  const [post, setPost] = useState("");
  const [otherOpen, setOtherOpen] = useState(false);
  const [wfhOpen, setWfhOpen] = useState(false);
  const [wfhFrom, setWfhFrom] = useState("");
  const [wfhTo, setWfhTo] = useState("");
  const [wfhNote, setWfhNote] = useState("");
  const [wfhNotify, setWfhNotify] = useState("");
  const [wfhSaving, setWfhSaving] = useState(false);
  const [wfhError, setWfhError] = useState("");
  const [posting, setPosting] = useState(false);
  const [expandedIds, setExpandedIds] = useState<Record<string, boolean>>({});
  const [commentOpen, setCommentOpen] = useState<Record<string, boolean>>({});
  const [commentDrafts, setCommentDrafts] = useState<Record<string, string>>({});
  const [reactingId, setReactingId] = useState("");
  const [canManageHolidays, setCanManageHolidays] = useState(false);
  const [holidayOpen, setHolidayOpen] = useState(false);
  const [holidayDate, setHolidayDate] = useState("");
  const [holidayReason, setHolidayReason] = useState("");
  const [holidaySaving, setHolidaySaving] = useState(false);
  const [holidayError, setHolidayError] = useState("");

  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(id);
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [homeFeed, rights] = await Promise.all([getHrmHome(), getHrmRights()]);
      setFeed(homeFeed);
      setCanManageHolidays(!!rights.canManageHolidays);
    } catch {
      try {
        const [inbox, leave, attendance] = await Promise.all([
          getHrmInbox("action"),
          getLeaveSummary(),
          getAttendanceBoard(),
        ]);
        setFeed({
          today: attendance.today,
          inboxPendingCount: inbox.pendingCount,
          holidays: [
            { name: "Mahatma Gandhi Jayanti", date: `${new Date().getFullYear()}-10-02` },
          ],
          onLeaveToday: [],
          workingRemotely: [],
          attendance,
          leaveBalances: leave.balances ?? [],
          departments: [
            { id: "org", name: "Organization" },
            { id: "eng", name: "Engineering > OpEx, R&D" },
          ],
          announcements: [],
          birthdaysToday: [],
          upcomingBirthdays: [],
          workAnniversaryCount: 0,
          newJoineeCount: 0,
        });
      } catch (e) {
        setFeed(null);
        setError(e instanceof Error ? e.message : "Failed to load HRM home");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const holidays = feed?.holidays ?? [];
  const holiday: HrmHomeHoliday | undefined = holidays[holidayIdx] ?? holidays[0];
  const balances: LeaveBalanceRow[] = (feed?.leaveBalances ?? []).slice(0, 3);
  const canClockIn = feed?.attendance?.canClockIn ?? false;
  const canClockOut = feed?.attendance?.canClockOut ?? false;
  const wfhDays = dayCount(wfhFrom, wfhTo);

  const clock = useMemo(() => {
    let h = now.getHours();
    const m = pad(now.getMinutes());
    const s = pad(now.getSeconds());
    const ampm = h >= 12 ? "PM" : "AM";
    h = h % 12 || 12;
    return `${pad(h)}:${m}:${s} ${ampm}`;
  }, [now]);

  const dateLabel = useMemo(
    () =>
      now.toLocaleDateString(undefined, {
        weekday: "short",
        day: "2-digit",
        month: "short",
        year: "numeric",
      }),
    [now],
  );

  async function punch(kind: "in" | "out") {
    setBusy(true);
    setError("");
    setOtherOpen(false);
    try {
      if (kind === "in") {
        await clockIn();
        toast.success("Office in time recorded.");
      } else {
        await clockOut();
        toast.success("Office out time recorded.");
      }
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Clock update failed");
    } finally {
      setBusy(false);
    }
  }

  async function submitWfh(e: FormEvent) {
    e.preventDefault();
    if (!wfhFrom || !wfhTo) {
      setWfhError("Please select From and To dates");
      return;
    }
    if (wfhDays <= 0) {
      setWfhError("To date must be on or after From date");
      return;
    }
    setWfhSaving(true);
    setWfhError("");
    try {
      await requestWorkFromHome({
        fromDate: wfhFrom,
        toDate: wfhTo,
        note: wfhNote || undefined,
        notifyTo: wfhNotify || undefined,
      });
      toast.success(`Work from home requested for ${wfhDays} day${wfhDays === 1 ? "" : "s"}.`);
      setWfhOpen(false);
      await load();
    } catch (err) {
      setWfhError(err instanceof Error ? err.message : "Request failed");
    } finally {
      setWfhSaving(false);
    }
  }

  async function publishPost(e: FormEvent) {
    e.preventDefault();
    const text = post.trim();
    if (!text) {
      setError("Write a post before adding it.");
      return;
    }
    setPosting(true);
    setError("");
    try {
      await createHrmPost({ body: text });
      setPost("");
      toast.success("Post added to Announcements.");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not add post");
    } finally {
      setPosting(false);
    }
  }

  function patchAnnouncement(updated: HrmHomeAnnouncement) {
    setFeed((curr) => {
      if (!curr) return curr;
      return {
        ...curr,
        announcements: curr.announcements.map((a) => (a.id === updated.id ? updated : a)),
      };
    });
  }

  async function onLike(id: string) {
    setReactingId(id);
    setError("");
    try {
      patchAnnouncement(await likeHrmPost(id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save like");
    } finally {
      setReactingId("");
    }
  }

  async function onComment(id: string, e: FormEvent) {
    e.preventDefault();
    const text = (commentDrafts[id] || "").trim();
    if (!text) {
      setError("Write a comment before posting it.");
      return;
    }
    setReactingId(id);
    setError("");
    try {
      patchAnnouncement(await commentHrmPost(id, text));
      setCommentDrafts((prev) => ({ ...prev, [id]: "" }));
      setCommentOpen((prev) => ({ ...prev, [id]: true }));
      toast.success("Comment saved.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save comment");
    } finally {
      setReactingId("");
    }
  }

  async function submitHoliday(e: FormEvent) {
    e.preventDefault();
    if (!holidayDate || !holidayReason.trim()) {
      setHolidayError("Date and reason are required");
      return;
    }
    setHolidaySaving(true);
    setHolidayError("");
    try {
      await createHrmHoliday({ date: holidayDate, reason: holidayReason.trim() });
      toast.success("Holiday saved for the organization.");
      setHolidayOpen(false);
      setHolidayDate("");
      setHolidayReason("");
      await load();
    } catch (err) {
      setHolidayError(err instanceof Error ? err.message : "Could not save holiday");
    } finally {
      setHolidaySaving(false);
    }
  }

  const pending = feed?.inboxPendingCount ?? 0;
  const onLeave = feed?.onLeaveToday ?? [];
  const remote = feed?.workingRemotely ?? [];
  const birthdaysToday = feed?.birthdaysToday ?? [];
  const upcomingBirthdays = feed?.upcomingBirthdays ?? [];
  const announcements = feed?.announcements ?? [];
  const tabs = feed?.departments?.length
    ? feed.departments
    : [
        { id: "org", name: "Organization" },
        { id: "eng", name: "Engineering > OpEx, R&D" },
      ];

  return (
    <div className="hrm-home">
      {error && <p className="error">{error}</p>}
      <div className="hrm-home-grid">
        <div className="hrm-home-col">
          <h2 className="hrm-home-kicker">Quick Access</h2>

          <section className="hrm-home-card">
            <div className="hrm-home-card-head">
              <h3>Inbox</h3>
            </div>
            <div className="hrm-home-card-body hrm-home-card-body--split">
              <InboxArt />
              <div>
                {pending === 0 ? (
                  <>
                    <p className="hrm-home-lead">Good job!</p>
                    <p className="hrm-home-muted">You have no pending actions</p>
                  </>
                ) : (
                  <>
                    <p className="hrm-home-lead">
                      {pending} pending action{pending === 1 ? "" : "s"}
                    </p>
                    <Link to="/hrm/inbox">Open inbox</Link>
                  </>
                )}
              </div>
            </div>
          </section>

          <section className="hrm-home-card">
            <div className="hrm-home-card-head">
              <h3>Holidays</h3>
              <div className="hrm-home-holiday-actions">
                {canManageHolidays && (
                  <button
                    type="button"
                    className="hrm-home-linkbtn"
                    onClick={() => setHolidayOpen(true)}
                  >
                    Add holiday
                  </button>
                )}
                <Link to="/hrm/holidays" className="hrm-home-viewall">
                  View All
                </Link>
              </div>
            </div>
            {holiday ? (
              <div className="hrm-home-holiday">
                <button
                  type="button"
                  className="hrm-home-chevron"
                  aria-label="Previous holiday"
                  disabled={holidays.length < 2}
                  onClick={() =>
                    setHolidayIdx((i) => (i - 1 + holidays.length) % holidays.length)
                  }
                >
                  ‹
                </button>
                <div className="hrm-home-holiday-copy">
                  <p className="hrm-home-holiday-name">{holiday.name}</p>
                  <p className="hrm-home-muted">
                    {formatHolidayDate(holiday.date)}
                    {holiday.day ? ` · ${holiday.day}` : ""}
                  </p>
                  {holiday.reason && holiday.reason !== holiday.name && (
                    <p className="hrm-home-muted">{holiday.reason}</p>
                  )}
                </div>
                <HolidayArt />
                <button
                  type="button"
                  className="hrm-home-chevron"
                  aria-label="Next holiday"
                  disabled={holidays.length < 2}
                  onClick={() => setHolidayIdx((i) => (i + 1) % holidays.length)}
                >
                  ›
                </button>
              </div>
            ) : (
              <p className="hrm-home-muted">{loading ? "Loading…" : "No upcoming holidays"}</p>
            )}
          </section>

          <section className="hrm-home-card">
            <div className="hrm-home-card-head">
              <h3>On Leave Today</h3>
            </div>
            {onLeave.length === 0 ? (
              <div className="hrm-home-card-body hrm-home-card-body--split">
                <div>
                  <p className="hrm-home-lead">Everyone is working today!</p>
                  <p className="hrm-home-muted">No one is on leave today.</p>
                </div>
                <LeaveArt />
              </div>
            ) : (
              <div className="hrm-home-people">
                {onLeave.map((p, i) => (
                  <div key={`${p.name}-${i}`} className="hrm-home-person">
                    <Avatar person={p} index={i} />
                    <span>{p.name}</span>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="hrm-home-card">
            <div className="hrm-home-card-head">
              <h3>Working Remotely</h3>
            </div>
            {remote.length === 0 ? (
              <p className="hrm-home-muted">No one is working remotely today.</p>
            ) : (
              <div className="hrm-home-remote">
                {remote.map((p, i) => (
                  <div key={`${p.name}-${i}`} className="hrm-home-remote-item">
                    <Avatar person={p} index={i} />
                    <span>{p.name.split(" ")[0]}</span>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="hrm-home-card hrm-home-card--time">
            <div className="hrm-home-time-head">
              <h3>Time Today - {dateLabel}</h3>
              <Link to="/hrm/attendance">View All</Link>
            </div>
            <p className="hrm-home-time-label">CURRENT TIME</p>
            <p className="hrm-home-time-clock">{clock}</p>
            <div className="hrm-home-time-actions">
              <button
                type="button"
                disabled={busy || !canClockIn}
                onClick={() => void punch("in")}
              >
                {canClockIn ? "Web Clock-In" : "Clocked in"}
              </button>
              <div className="hrm-home-other">
                <button
                  type="button"
                  className="hrm-home-other-btn"
                  onClick={() => setOtherOpen((v) => !v)}
                >
                  Other ▾
                </button>
                {otherOpen && (
                  <ul className="hrm-home-other-menu">
                    <li>
                      <button
                        type="button"
                        disabled={busy || !canClockOut}
                        onClick={() => void punch("out")}
                      >
                        Web Clock-Out
                      </button>
                    </li>
                    <li>
                      <button
                        type="button"
                        onClick={() => {
                          setOtherOpen(false);
                          setWfhOpen(true);
                        }}
                      >
                        Work from home
                      </button>
                    </li>
                    <li>
                      <Link to="/hrm/leave" onClick={() => setOtherOpen(false)}>
                        Request leave
                      </Link>
                    </li>
                  </ul>
                )}
              </div>
            </div>
          </section>

          <section className="hrm-home-card">
            <div className="hrm-home-card-head">
              <h3>Leave Balances</h3>
              <Link to="/hrm/leave">Request Leave</Link>
            </div>
            {balances.length === 0 ? (
              <p className="hrm-home-muted">{loading ? "Loading…" : "No leave balances yet."}</p>
            ) : (
              <div className="hrm-home-rings">
                {balances.map((b, i) => (
                  <BalanceRing
                    key={b.id || b.leaveType}
                    value={b.available}
                    label={b.leaveType}
                    color={RING_COLORS[i % RING_COLORS.length]}
                  />
                ))}
              </div>
            )}
          </section>
        </div>

        <div className="hrm-home-col hrm-home-col--feed">
          <div className="hrm-home-spaces" role="tablist" aria-label="HRM spaces">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                type="button"
                role="tab"
                aria-selected={space === tab.id}
                className={space === tab.id ? "is-active" : ""}
                onClick={() => setSpace(tab.id)}
              >
                {tab.name}
              </button>
            ))}
          </div>

          <section className="hrm-home-card">
            <div className="hrm-home-composer-tabs">
              <span className="is-active">Post</span>
              <span>Praise</span>
            </div>
            <form className="hrm-home-composer" onSubmit={publishPost}>
              <textarea
                rows={3}
                placeholder="Write your post here and mention your peers"
                value={post}
                onChange={(e) => setPost(e.target.value)}
              />
              <div className="hrm-home-composer-actions">
                <button type="submit" disabled={posting || !post.trim()}>
                  {posting ? "Posting…" : "Add post"}
                </button>
              </div>
            </form>
          </section>

          <section className="hrm-home-card">
            <div className="hrm-home-card-head">
              <h3>Announcements</h3>
            </div>
            {announcements.length === 0 ? (
              <p className="hrm-home-muted">No announcements yet. Use Add post above.</p>
            ) : (
              <div className="hrm-home-announce-list">
                {announcements.map((item) => {
                  const open = !!expandedIds[item.id];
                  const long = item.body.length > 118;
                  return (
                    <article key={item.id} className="hrm-home-announce">
                      <AssetArt />
                      <div>
                        <h4>{item.title}</h4>
                        <p>
                          {open || !long
                            ? item.body
                            : `${item.body.slice(0, 118)}…`}
                        </p>
                        {long && (
                          <button
                            type="button"
                            className="hrm-home-linkbtn"
                            onClick={() =>
                              setExpandedIds((prev) => ({ ...prev, [item.id]: !prev[item.id] }))
                            }
                          >
                            {open ? "view less" : "view more"}
                          </button>
                        )}
                        <p className="hrm-home-react">
                          <button
                            type="button"
                            className={`hrm-home-react-btn${item.likedByMe ? " is-active" : ""}`}
                            disabled={reactingId === item.id}
                            onClick={() => void onLike(item.id)}
                          >
                            👍 {item.likes ?? 0}
                          </button>
                          <button
                            type="button"
                            className={`hrm-home-react-btn${commentOpen[item.id] ? " is-active" : ""}`}
                            onClick={() =>
                              setCommentOpen((prev) => ({ ...prev, [item.id]: !prev[item.id] }))
                            }
                          >
                            💬 {item.comments ?? item.commentItems?.length ?? 0}
                          </button>
                        </p>
                        {commentOpen[item.id] && (
                          <div className="hrm-home-comments">
                            {(item.commentItems ?? []).length === 0 ? (
                              <p className="hrm-home-muted">No comments yet.</p>
                            ) : (
                              <ul>
                                {(item.commentItems ?? []).map((c) => (
                                  <li key={c.id}>
                                    <strong>{c.userName || c.userId}</strong>
                                    <span>{c.body}</span>
                                  </li>
                                ))}
                              </ul>
                            )}
                            <form
                              className="hrm-home-comment-form"
                              onSubmit={(e) => void onComment(item.id, e)}
                            >
                              <input
                                value={commentDrafts[item.id] || ""}
                                onChange={(e) =>
                                  setCommentDrafts((prev) => ({
                                    ...prev,
                                    [item.id]: e.target.value,
                                  }))
                                }
                                placeholder="Write a comment"
                                maxLength={2000}
                              />
                              <button
                                type="submit"
                                disabled={reactingId === item.id || !(commentDrafts[item.id] || "").trim()}
                              >
                                {reactingId === item.id ? "Saving…" : "Comment"}
                              </button>
                            </form>
                          </div>
                        )}
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </section>

          <section className="hrm-home-card">
            <div className="hrm-home-celeb-tabs">
              <span className="is-active">
                {birthdaysToday.length} Birthday{birthdaysToday.length === 1 ? "" : "s"}
              </span>
              <span>{feed?.workAnniversaryCount ?? 0} Work Anniversaries</span>
              <span>{feed?.newJoineeCount ?? 0} New joinees</span>
            </div>
            <h4 className="hrm-home-subhead">Birthdays today</h4>
            {birthdaysToday.length === 0 ? (
              <p className="hrm-home-muted">No birthdays today.</p>
            ) : (
              <div className="hrm-home-people">
                {birthdaysToday.map((p, i) => (
                  <div key={`${p.name}-${i}`} className="hrm-home-person">
                    <Avatar person={p} index={i} />
                    <div>
                      <strong>{p.name}</strong>
                      <p className="hrm-home-muted">Wish</p>
                    </div>
                  </div>
                ))}
              </div>
            )}
            <h4 className="hrm-home-subhead">Upcoming Birthdays</h4>
            {upcomingBirthdays.length === 0 ? (
              <p className="hrm-home-muted">No upcoming birthdays this month.</p>
            ) : (
              <div className="hrm-home-people">
                {upcomingBirthdays.map((p, i) => (
                  <div key={`${p.name}-${i}`} className="hrm-home-person">
                    <Avatar person={p} index={i + 3} />
                    <span>{p.name}</span>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>

      {wfhOpen && (
        <div
          className="hrm-wfh-backdrop"
          role="presentation"
          onClick={() => !wfhSaving && setWfhOpen(false)}
        >
          <div
            className="hrm-wfh-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="home-wfh-title"
            onClick={(e) => e.stopPropagation()}
          >
            <header className="hrm-wfh-head">
              <h2 id="home-wfh-title">Request work from home</h2>
              <button
                type="button"
                className="hrm-wfh-close"
                aria-label="Close"
                disabled={wfhSaving}
                onClick={() => setWfhOpen(false)}
              >
                ×
              </button>
            </header>
            <form onSubmit={submitWfh}>
              <div className="hrm-wfh-dates">
                <label>
                  <span>From</span>
                  <input
                    type="date"
                    value={wfhFrom}
                    onChange={(e) => setWfhFrom(e.target.value)}
                    required
                  />
                </label>
                <span className="hrm-wfh-days">
                  {wfhDays} day{wfhDays === 1 ? "" : "s"}
                </span>
                <label>
                  <span>To</span>
                  <input
                    type="date"
                    value={wfhTo}
                    onChange={(e) => setWfhTo(e.target.value)}
                    required
                  />
                </label>
              </div>
              <label className="hrm-wfh-field">
                <span>Note</span>
                <textarea
                  rows={4}
                  placeholder="Type here"
                  value={wfhNote}
                  onChange={(e) => setWfhNote(e.target.value)}
                />
              </label>
              <label className="hrm-wfh-field">
                <span>Notify</span>
                <input
                  type="text"
                  placeholder="Search employee"
                  value={wfhNotify}
                  onChange={(e) => setWfhNotify(e.target.value)}
                />
              </label>
              {wfhError && <p className="error">{wfhError}</p>}
              <div className="hrm-wfh-actions">
                <button type="button" className="btn" disabled={wfhSaving} onClick={() => setWfhOpen(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn primary hrm-request-btn" disabled={wfhSaving}>
                  {wfhSaving ? "Requesting…" : "Request"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {canManageHolidays && (
        <HolidayModal
          open={holidayOpen}
          saving={holidaySaving}
          date={holidayDate}
          reason={holidayReason}
          error={holidayError}
          onDate={setHolidayDate}
          onReason={setHolidayReason}
          onClose={() => !holidaySaving && setHolidayOpen(false)}
          onSubmit={submitHoliday}
        />
      )}
    </div>
  );
}
