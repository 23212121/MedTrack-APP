import { NavLink, Outlet } from "react-router-dom";
import { session } from "../../dl/MedTrackSession";

const tabs = [
  { to: "/hrm", label: "Attendance", end: true },
  { to: "/hrm/leave", label: "Leave" },
  { to: "/hrm/inbox", label: "Inbox" },
  { to: "/hrm/performance", label: "Performance" },
  { to: "/hrm/apps", label: "Apps" },
];

export default function HrmShell() {
  const initials = (session.getUsername() || "AK")
    .split(/\s+/)
    .map((p) => p[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();

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
            end={tab.end}
            className={({ isActive }) =>
              isActive ? "hrm-tab is-active" : "hrm-tab"
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
