export const medicalNavGroups = [
  {
    title: "Medical store",
    items: [
      { to: "/medical", label: "Dashboard", hint: "Order counts", end: true },
      { to: "/medical/emergency", label: "Check Emergency Service", hint: "Beds, doctors, book a bed" },
      { to: "/medical/orders/check", label: "Check order", hint: "Accept, pending, amount" },
      { to: "/medical/orders", label: "All orders", hint: "Search & track" },
      { to: "/medical/orders/new", label: "New orders", hint: "Accept or reject" },
      { to: "/medical/orders/in-process", label: "In process", hint: "Quote & send amount" },
      { to: "/medical/orders/waiting", label: "Waiting approval", hint: "Patient review" },
      { to: "/medical/orders/ready", label: "Medicine ready", hint: "Pickup / delivery" },
      { to: "/medical/orders/completed", label: "Completed", hint: "History" },
      { to: "/medical/orders/canceled", label: "Canceled", hint: "Closed orders" },
      { to: "/medical/notifications", label: "Notifications", hint: "Order alerts" },
    ],
  },
  {
    title: "Account",
    items: [{ to: "/logout", label: "Logout", hint: "End session" }],
  },
];

export const medicalPageTitles: Record<string, string> = {
  "/medical": "Medical dashboard",
  "/medical/emergency": "Check Emergency Service",
  "/medical/orders/check": "Check order",
  "/medical/orders": "Medicine orders",
  "/medical/orders/new": "New orders",
  "/medical/orders/in-process": "In process",
  "/medical/orders/waiting": "Waiting approval",
  "/medical/orders/ready": "Medicine ready",
  "/medical/orders/completed": "Completed orders",
  "/medical/orders/canceled": "Canceled orders",
  "/medical/notifications": "Notifications",
};

export const medicalKnownPaths = new Set([
  "/medical",
  "/medical/emergency",
  "/medical/orders/check",
  "/medical/orders",
  "/medical/orders/new",
  "/medical/orders/in-process",
  "/medical/orders/waiting",
  "/medical/orders/ready",
  "/medical/orders/completed",
  "/medical/orders/canceled",
  "/medical/notifications",
  "/logout",
]);
