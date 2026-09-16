export const patientNavGroups = [
  {
    title: "Patient Portal",
    items: [
      { to: "/patient", label: "Dashboard", hint: "Overview & next visit", end: true },
      { to: "/patient/booking", label: "Booking", hint: "Book & manage appointments" },
      { to: "/patient/status", label: "Check Status", hint: "Live queue & wait time" },
      { to: "/patient/chat", label: "Chat", hint: "Message hospital or doctor" },
      { to: "/patient/reports", label: "Reports", hint: "Lab & prescriptions" },
      { to: "/patient/medicine-orders", label: "Medicine orders", hint: "Upload RX & track" },
      { to: "/patient/profile", label: "Profile", hint: "Personal & history" },
    ],
  },
];

export const patientPageTitles: Record<string, string> = {
  "/patient": "Dashboard",
  "/patient/booking": "Booking",
  "/patient/status": "Queue Status",
  "/patient/chat": "Chat",
  "/patient/reports": "Reports",
  "/patient/medicine-orders": "Medicine orders",
  "/patient/profile": "Profile",
};

export const patientKnownPaths = new Set([
  "/patient",
  "/patient/booking",
  "/patient/status",
  "/patient/chat",
  "/patient/reports",
  "/patient/medicine-orders",
  "/patient/profile",
  "/patient/logout",
]);
