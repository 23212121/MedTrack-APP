export const labNavGroups = [
  {
    title: "Lab technician",
    items: [
      { to: "/lab", label: "Dashboard", hint: "Upload overview", end: true },
      { to: "/lab/patients", label: "Patient reports", hint: "Enter phone or Aadhaar, upload files" },
      { to: "/lab/uploads", label: "Uploaded reports", hint: "Files sent to patients" },
    ],
  },
  {
    title: "Account",
    items: [{ to: "/logout", label: "Logout", hint: "End session" }],
  },
];

export const labPageTitles: Record<string, string> = {
  "/lab": "Lab dashboard",
  "/lab/patients": "Patient reports",
  "/lab/uploads": "Uploaded reports",
};

export const labKnownPaths = new Set(["/lab", "/lab/patients", "/lab/uploads", "/logout"]);
