export const patientNavGroups = [
  {
    title: "Patient Portal",
    items: [
      { to: "/patient", label: "Dashboard", hint: "Overview & next visit", end: true },
      { to: "/patient/booking", label: "Booking", hint: "Book & manage appointments" },
      { to: "/patient/status", label: "Check Status", hint: "Live queue & wait time" },
      { to: "/patient/reports", label: "Reports", hint: "Lab & prescriptions" },
      { to: "/patient/profile", label: "Profile", hint: "Personal & history" },
    ],
  },
];

export const patientPageTitles: Record<string, string> = {
  "/patient": "Dashboard",
  "/patient/booking": "Booking",
  "/patient/status": "Queue Status",
  "/patient/reports": "Reports",
  "/patient/profile": "Profile",
};

export const patientKnownPaths = new Set([
  "/patient",
  "/patient/booking",
  "/patient/status",
  "/patient/reports",
  "/patient/profile",
  "/patient/logout",
]);
