export type NavItem = { to: string; label: string; end?: boolean; hint: string };
export type NavGroup = { title: string; items: NavItem[] };

/** Sidebar menus — add items here and dashboard tiles update automatically. */
export const navGroups: NavGroup[] = [
  {
    title: "Overview",
    items: [
      { to: "/", label: "Dashboard", end: true, hint: "Clinic snapshot" },
    ],
  },
  {
    title: "HRM",
    items: [
      {
        to: "/hrm",
        label: "HRM",
        hint: "Attendance, Leave, Inbox",
      },
    ],
  },
  {
    title: "Clinic ops",
    items: [
      { to: "/bookings", label: "Bookings", hint: "Appointments" },
      { to: "/schedules", label: "Schedules", hint: "Working hours" },
      { to: "/availability", label: "Availability", hint: "Busy / blocked time" },
      { to: "/check-in", label: "Check-in", hint: "Tokens & alerts" },
      { to: "/doctor", label: "Doctor queue", hint: "Live consults" },
      { to: "/queue", label: "Patient queue", hint: "Wait times" },
      {
        to: "/patient-documents",
        label: "Patient documents",
        hint: "Upload test files",
      },
      {
        to: "/check-document",
        label: "Check document",
        hint: "Your hospital uploads",
      },
      {
        to: "/doctor-portal",
        label: "Patient list",
        hint: "Doctor portal · tokens",
      },
    ],
  },
  {
    title: "Insights",
    items: [
      { to: "/chart", label: "Busy chart", hint: "Day timeline" },
      { to: "/fees", label: "Fees", hint: "Overtime charges" },
      { to: "/notifications", label: "Notifications", hint: "SMS & email" },
    ],
  },
  {
    title: "People",
    items: [
      {
        to: "/doctor-register",
        label: "Doctor register",
        hint: "Self-registration",
      },
      { to: "/logout", label: "Logout", hint: "End session" },
    ],
  },
];

/** Paths that should not appear as dashboard tiles. */
const DASHBOARD_EXCLUDED = new Set(["/", "/login", "/logout"]);

export type DashboardTile = { to: string; title: string; text: string; group: string };

/** Dynamic tiles from sidebar nav — grows/shrinks when menus are added or removed. */
export function getDashboardTiles(): DashboardTile[] {
  return navGroups.flatMap((group) =>
    group.items
      .filter((item) => !DASHBOARD_EXCLUDED.has(item.to))
      .map((item) => ({
        to: item.to,
        title: item.label,
        text: item.hint,
        group: group.title,
      })),
  );
}

/** Nested HRM screens — under HRM shell tabs, not separate sidebar entries. */
const EXTRA_HRM_PATHS = [
  "/hrm/leave",
  "/hrm/inbox",
  "/hrm/performance",
  "/hrm/apps",
];

export const knownPaths = new Set([
  ...navGroups.flatMap((g) => g.items.map((i) => i.to)),
  ...EXTRA_HRM_PATHS,
]);

export const pageTitles: Record<string, string> = {
  ...Object.fromEntries(
    navGroups.flatMap((g) => g.items.map((i) => [i.to, i.label])),
  ),
  "/hrm": "HRM",
  "/hrm/leave": "HRM · Leave",
  "/hrm/inbox": "HRM · Inbox",
  "/hrm/performance": "HRM · Performance",
  "/hrm/apps": "HRM · Apps",
};
