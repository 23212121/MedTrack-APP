import { useEffect, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { getHrmRights } from "../../api";
import { session } from "../../dl/MedTrackSession";

const TAB_DEFS = [
  { code: "HRM_HOME", to: "/hrm", label: "Home", end: true },
  { code: "HRM_ATTENDANCE", to: "/hrm/attendance", label: "Attendance" },
  { code: "HRM_LEAVE", to: "/hrm/leave", label: "Leave" },
  { code: "HRM_INBOX", to: "/hrm/inbox", label: "Inbox" },
  { code: "HRM_PERFORMANCE", to: "/hrm/performance", label: "Performance" },
  { code: "HRM_APPS", to: "/hrm/apps", label: "Apps" },
  { code: "HRM_HOME", to: "/hrm/holidays", label: "Holiday" },
  { code: "HRM_APPROVER", to: "/hrm/approver", label: "Approver", alignRight: true },
] as const;

const DEFAULT_CODES = new Set(
  TAB_DEFS.filter((t) => t.code !== "HRM_APPROVER").map((t) => t.code),
);

export default function HrmShell() {
  const [allowed, setAllowed] = useState<Set<string>>(DEFAULT_CODES);

  useEffect(() => {
    let cancelled = false;
    void getHrmRights()
      .then((data) => {
        if (cancelled) return;
        const next = new Set((data.rights || []).map((c) => c.toUpperCase()));
        setAllowed(next.size ? next : DEFAULT_CODES);
      })
      .catch(() => {
        if (!cancelled) setAllowed(DEFAULT_CODES);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const initials = (session.getUsername() || "AK")
    .split(/\s+/)
    .map((p) => p[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();

  const tabs = TAB_DEFS.filter((tab) => allowed.has(tab.code));

  return (
    <div className="hrm-shell">
      <header className="hrm-topbar hrm-topbar--no-search">
        <div className="hrm-brand">MedTrack Clinic</div>
        <div className="hrm-top-actions">
          <button type="button" className="hrm-bell" aria-label="Notifications">
            <span aria-hidden="true">🔔</span>
            <span className="hrm-bell-badge">10</span>
          </button>
          <div className="hrm-avatar" title={session.getUsername() || "User"}>
            {initials || "AK"}
          </div>
        </div>
      </header>

      <nav className="hrm-tabs" aria-label="HRM modules">
        {tabs.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            end={tab.to === "/hrm"}
            className={({ isActive }) =>
              [
                "hrm-tab",
                "alignRight" in tab && tab.alignRight ? "hrm-tab--right" : "",
                isActive ? "is-active" : "",
              ]
                .filter(Boolean)
                .join(" ")
            }
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>

      <div className="hrm-body">
        <Outlet />
      </div>
    </div>
  );
}
