export const patientNavGroups = [
  {
    title: "Patient Portal",
    items: [
      { to: "/patient", label: "Dashboard", hint: "Overview & next visit", end: true },
      { to: "/patient/emergency", label: "Check Emergency Service", hint: "Beds, doctors, book a bed" },
      { to: "/patient/booking", label: "Booking", hint: "Book & manage appointments" },
      { to: "/patient/status", label: "Check Status", hint: "Live queue & wait time" },
      { to: "/patient/chat", label: "Chat", hint: "Message hospital or doctor" },
      { to: "/patient/reports", label: "Reports", hint: "Lab & prescriptions" },
      { to: "/patient/medicine-orders", label: "Medicine orders", hint: "Upload RX & track" },
      { to: "/patient/notifications", label: "Notifications", hint: "Order quotes & updates" },
      { to: "/patient/profile", label: "Profile", hint: "Personal & history" },
    ],
  },
];

export const patientPageTitles: Record<string, string> = {
  "/patient": "Dashboard",
  "/patient/emergency": "Check Emergency Service",
  "/patient/booking": "Booking",
  "/patient/status": "Queue Status",
  "/patient/chat": "Chat",
  "/patient/reports": "Reports",
  "/patient/medicine-orders": "Medicine orders",
  "/patient/notifications": "Notifications",
  "/patient/profile": "Profile",
};

export const patientKnownPaths = new Set([
  "/patient",
  "/patient/emergency",
  "/patient/booking",
  "/patient/status",
  "/patient/chat",
  "/patient/reports",
  "/patient/medicine-orders",
  "/patient/notifications",
  "/patient/profile",
  "/patient/logout",
]);
