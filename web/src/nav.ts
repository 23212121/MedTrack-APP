export type NavItem = { to: string; label: string; end?: boolean; hint: string };
export type NavGroup = { title: string; items: NavItem[] };

/** Sidebar menus — add items here and dashboard tiles update automatically. */
export const navGroups: NavGroup[] = [
  {
    title: "Overview",
    items: [
      { to: "/", label: "Dashboard", end: true, hint: "Clinic snapshot" },
      {
        to: "/system-status",
        label: "System status",
        hint: "Actuator · services UP/DOWN",
      },
    ],
  },
  {
    title: "HRM",
    items: [
      {
        to: "/hrm",
        label: "HRM",
        hint: "Home, attendance, leave",
      },
    ],
  },
  {
    title: "Clinic ops",
    items: [
      {
        to: "/emergency",
        label: "Check Emergency Service",
        hint: "Beds, doctors, book & pay",
      },
      { to: "/bookings", label: "Bookings", hint: "Appointments" },
      { to: "/schedules", label: "Schedules", hint: "Working hours" },
      { to: "/availability", label: "Availability", hint: "Busy / blocked time" },
      { to: "/check-in", label: "Check-in", hint: "Tokens & alerts" },
      { to: "/doctor", label: "Doctor queue", hint: "Live consults" },
      { to: "/queue", label: "Patient queue", hint: "Wait times" },
      {
        to: "/patient-documents",
        label: "Patient documents",
        hint: "Upload files",
      },
      {
        to: "/check-document",
        label: "Check document",
        hint: "Your hospital uploads",
      },
      {
        to: "/patient-list",
        label: "Patient list",
        hint: "Hospital bookings · chat",
      },
      {
        to: "/medicine-orders",
        label: "Medicine orders",
        hint: "Track RX orders",
      },
      {
        to: "/medical-stores",
        label: "Medical stores",
        hint: "Register pharmacies",
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

/** Sidebar for USER (doctor) login — only this doctor's hospital bookings. */
export const doctorNavGroups: NavGroup[] = [
  {
    title: "Doctor portal",
    items: [
      { to: "/doctor-portal", label: "Dashboard", end: true, hint: "Open a section" },
      { to: "/doctor-portal/emergency", label: "Check Emergency Service", hint: "Beds, doctors, book & pay" },
      { to: "/doctor-portal/queue", label: "Check Patient", end: true, hint: "Patient queue" },
      { to: "/doctor-portal/time-slots", label: "Time slots", hint: "Your weekly windows" },
      { to: "/doctor-portal/medicine-orders", label: "Medicine orders", hint: "Book RX for patients" },
    ],
  },
  {
    title: "HRM",
    items: [
      { to: "/hrm", label: "HRM", hint: "Home, leave, attendance" },
    ],
  },
  {
    title: "Account",
    items: [
      { to: "/logout", label: "Logout", hint: "End session" },
    ],
  },
];

/** Paths that should not appear as dashboard tiles. */
const DASHBOARD_EXCLUDED = new Set(["/", "/login", "/logout"]);
const DOCTOR_DASHBOARD_EXCLUDED = new Set(["/doctor-portal", "/login", "/logout"]);

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

/** Doctor dashboard tiles — same cards as hospital home, from doctor left nav. */
export function getDoctorDashboardTiles(): DashboardTile[] {
  return doctorNavGroups.flatMap((group) =>
    group.items
      .filter((item) => !DOCTOR_DASHBOARD_EXCLUDED.has(item.to))
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
  "/hrm/attendance",
  "/hrm/leave",
  "/hrm/inbox",
  "/hrm/performance",
  "/hrm/apps",
  "/hrm/approver",
  "/hrm/holidays",
];

export const knownPaths = new Set([
  ...navGroups.flatMap((g) => g.items.map((i) => i.to)),
  ...doctorNavGroups.flatMap((g) => g.items.map((i) => i.to)),
  ...EXTRA_HRM_PATHS,
]);

export const pageTitles: Record<string, string> = {
  ...Object.fromEntries(
    navGroups.flatMap((g) => g.items.map((i) => [i.to, i.label])),
  ),
  "/hrm": "HRM",
  "/hrm/attendance": "HRM · Attendance",
  "/hrm/leave": "HRM · Leave",
  "/hrm/inbox": "HRM · Inbox",
  "/hrm/performance": "HRM · Performance",
  "/hrm/apps": "HRM · Apps",
  "/hrm/approver": "HRM · Approver",
  "/hrm/holidays": "HRM · Holiday",
  "/patient-list": "Patient list",
  "/emergency": "Check Emergency Service",
  "/doctor-portal": "Doctor dashboard",
  "/doctor-portal/queue": "Check Patient",
  "/doctor-portal/emergency": "Check Emergency Service",
  "/doctor-portal/time-slots": "Time slots",
  "/doctor-portal/medicine-orders": "Medicine orders",
  "/medicine-orders": "Medicine orders",
  "/medical-stores": "Medical stores",
};
