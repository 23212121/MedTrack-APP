/** NPCI UPI pay payload. PhonePe rejects URL-encoded VPAs (`%40`) and ECC-L QRs. */
export function buildUpiPay(opts: {
  vpa: string;
  payeeName?: string;
  amount: number;
  note?: string;
}): string {
  const pa = (opts.vpa || "").trim().replace(/\s+/g, "");
  const pn = sanitizeUpiName(opts.payeeName);
  const am = Math.max(0, Number(opts.amount) || 0).toFixed(2);
  const tn = (opts.note || "MedTrack").replace(/[^A-Za-z0-9]/g, "").slice(0, 32) || "MedTrack";
  return (
    "upi://pay?pa=" +
    pa +
    "&pn=" +
    pn.replace(/ /g, "%20") +
    "&am=" +
    am +
    "&cu=INR&mc=0000&mode=02&tn=" +
    tn
  );
}

export function sanitizeUpiName(name?: string): string {
  const s = (name || "MedTrack")
    .replace(/[^A-Za-z0-9 ]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 34);
  return s || "MedTrack";
}

export function upiQueryFromIntent(uri: string): string {
  const i = uri.indexOf("?");
  return i >= 0 ? uri.slice(i + 1) : "";
}

/** PhonePe shows "Unable to verify the QR Code" for unregistered / dummy handles. */
export function isUnverifiableVpa(vpa?: string): boolean {
  const s = (vpa || "").trim().toLowerCase();
  if (!s || !s.includes("@") || s.startsWith("@")) return true;
  const handle = s.slice(s.lastIndexOf("@") + 1);
  return !handle || handle === "upi";
}
