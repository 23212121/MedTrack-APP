export type VoiceField =
  | "patientName"
  | "phone"
  | "age"
  | "gender"
  | "email"
  | "doctor"
  | "date"
  | "time"
  | "address"
  | "reason";

export type VoiceExpecting = VoiceField | "confirm" | "idle" | "showDoctor" | "showDate" | "showDateTo";

export type ParsedVoiceCommand = {
  intent?: "BOOK_APPOINTMENT" | "CONFIRM_BOOK" | "CANCEL" | "STOP" | "SHOW_BOOKINGS";
  field?: VoiceField;
  patientName?: string;
  phone?: string;
  age?: string;
  gender?: string;
  email?: string;
  doctorQuery?: string;
  date?: string;
  dateFrom?: string;
  dateTo?: string;
  openEndedRange?: boolean;
  time?: string;
  address?: string;
  reason?: string;
  raw: string;
};

const DIGIT_WORDS: Record<string, string> = {
  zero: "0",
  oh: "0",
  o: "0",
  one: "1",
  two: "2",
  three: "3",
  four: "4",
  five: "5",
  six: "6",
  seven: "7",
  eight: "8",
  nine: "9",
};

const MONTHS: Record<string, number> = {
  jan: 0,
  january: 0,
  feb: 1,
  february: 1,
  mar: 2,
  march: 2,
  apr: 3,
  april: 3,
  may: 4,
  jun: 5,
  june: 5,
  jul: 6,
  july: 6,
  aug: 7,
  august: 7,
  sep: 8,
  sept: 8,
  september: 8,
  oct: 9,
  october: 9,
  nov: 10,
  november: 10,
  dec: 11,
  december: 11,
  जनवरी: 0,
  फरवरी: 1,
  मार्च: 2,
  अप्रैल: 3,
  मई: 4,
  जून: 5,
  जुलाई: 6,
  अगस्त: 7,
  सितंबर: 8,
  सितम्बर: 8,
  अक्टूबर: 9,
  नवंबर: 10,
  नवम्बर: 10,
  दिसंबर: 11,
  दिसम्बर: 11,
};

function pad2(n: number) {
  return String(n).padStart(2, "0");
}

function toYmd(d: Date) {
  const off = d.getTimezoneOffset();
  return new Date(d.getTime() - off * 60_000).toISOString().slice(0, 10);
}

function addDays(days: number) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return toYmd(d);
}

function normalize(raw: string) {
  return raw
    .replace(/[.,!?]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function digitsFromWords(text: string) {
  const parts = text.toLowerCase().split(/[\s-]+/);
  let out = "";
  for (const p of parts) {
    if (DIGIT_WORDS[p] != null) out += DIGIT_WORDS[p];
  }
  return out;
}

export function extractPhone(text: string): string | undefined {
  const compact = text.replace(/[^\d]/g, "");
  let digits = compact;
  if (digits.length < 10) {
    const spoken = digitsFromWords(text);
    if (spoken.length >= 10) digits = spoken;
  }
  if (digits.length >= 12 && digits.startsWith("91")) digits = digits.slice(-10);
  if (digits.length >= 11 && digits.startsWith("0")) digits = digits.slice(-10);
  if (digits.length >= 10) return digits.slice(-10);
  return undefined;
}

export function normalizeGender(text: string): string | undefined {
  const s = text.trim().toLowerCase();
  if (!s) return undefined;
  if (/\bfemale\b|\bwoman\b|\bgirl\b|\blady\b/.test(s)) return "FEMALE";
  if (/\bmale\b|\bman\b|\bboy\b/.test(s)) return "MALE";
  if (/\bother\b|\bnon binary\b|\bnonbinary\b/.test(s)) return "OTHER";
  if (/\bprefer not\b|\bunknown\b|\bskip\b/.test(s)) return "UNKNOWN";
  return undefined;
}

function extractAge(text: string): string | undefined {
  const years = text.match(/\b(\d{1,3})\s*(?:years?\s*old|yrs?|year)\b/i);
  if (years) {
    const n = Number(years[1]);
    if (n >= 1 && n <= 120) return String(n);
  }
  const only = text.match(/^\s*(\d{1,3})\s*$/);
  if (only) {
    const n = Number(only[1]);
    if (n >= 1 && n <= 120) return String(n);
  }
  const ageIs = text.match(/\bage(?:\s+is)?\s+(\d{1,3})\b/i);
  if (ageIs) {
    const n = Number(ageIs[1]);
    if (n >= 1 && n <= 120) return String(n);
  }
  return undefined;
}

function extractEmail(text: string): string | undefined {
  const spoken = text
    .toLowerCase()
    .replace(/\s+at\s+/g, "@")
    .replace(/\s+dot\s+/g, ".")
    .replace(/\s+/g, "");
  const m = spoken.match(/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i);
  return m?.[0];
}

export function extractDate(text: string): string | undefined {
  const s = text.toLowerCase();
  if (/\btoday\b|आज/.test(s)) return toYmd(new Date());
  if (/\bday after tomorrow\b|परसों/.test(s)) return addDays(2);
  if (/\btomorrow\b|\bकल\b/.test(s)) return addDays(1);

  const iso = s.match(/\b(\d{4})-(\d{2})-(\d{2})\b/);
  if (iso) return `${iso[1]}-${iso[2]}-${iso[3]}`;

  const dmy = s.match(/\b(\d{1,2})[\/\- ]([a-z\u0900-\u097F]{3,12})[\/\- ](\d{2,4})\b/);
  if (dmy && monthIndex(dmy[2]) != null) {
    const year = dmy[3].length === 2 ? 2000 + Number(dmy[3]) : Number(dmy[3]);
    const d = new Date(year, monthIndex(dmy[2])!, Number(dmy[1]));
    if (!Number.isNaN(d.getTime())) return toYmd(d);
  }

  const mdy = s.match(/\b([a-z\u0900-\u097F]{3,12})\s+(\d{1,2})(?:st|nd|rd|th)?(?:[,\s]+(\d{2,4}))?\b/);
  if (mdy && monthIndex(mdy[1]) != null) {
    const year = mdy[3]
      ? mdy[3].length === 2
        ? 2000 + Number(mdy[3])
        : Number(mdy[3])
      : new Date().getFullYear();
    const d = new Date(year, monthIndex(mdy[1])!, Number(mdy[2]));
    if (!Number.isNaN(d.getTime())) return toYmd(d);
  }

  const dmo = s.match(/\b(\d{1,2})(?:st|nd|rd|th)?\s+([a-z\u0900-\u097F]{3,12})(?:[,\s]+(\d{2,4}))?\b/);
  if (dmo && monthIndex(dmo[2]) != null) {
    const year = dmo[3]
      ? dmo[3].length === 2
        ? 2000 + Number(dmo[3])
        : Number(dmo[3])
      : new Date().getFullYear();
    const d = new Date(year, monthIndex(dmo[2])!, Number(dmo[1]));
    if (!Number.isNaN(d.getTime())) return toYmd(d);
  }

  return undefined;
}

function monthIndex(raw: string): number | undefined {
  return MONTHS[raw.toLowerCase()];
}

/** Single day (from=to) or an inclusive from–to range. */
export function extractDateRange(text: string): {
  from?: string;
  to?: string;
  openEnded?: boolean;
} {
  const s = normalize(text);
  const lower = s.toLowerCase();

  const between = lower.match(
    /\bbetween\s+(\d{1,2})(?:st|nd|rd|th)?\s+(?:and|to)\s+(\d{1,2})(?:st|nd|rd|th)?\s+([a-z\u0900-\u097F]{3,12})(?:[,\s]+(\d{2,4}))?\b/,
  );
  if (between && monthIndex(between[3]) != null) {
    const year = between[4]
      ? between[4].length === 2
        ? 2000 + Number(between[4])
        : Number(between[4])
      : new Date().getFullYear();
    const month = monthIndex(between[3])!;
    const from = toYmd(new Date(year, month, Number(between[1])));
    const to = toYmd(new Date(year, month, Number(between[2])));
    return from <= to ? { from, to } : { from: to, to: from };
  }

  const parts = s.split(/\s+(?:to|through|till|until|तक)\s+/i);
  if (parts.length === 2) {
    const left = parts[0]
      .replace(/^\s*(?:from|between)\s+/i, "")
      .replace(/\s+से\s*$/i, "")
      .trim();
    const right = parts[1].replace(/\s+तक\s*$/i, "").trim();
    let from = extractDate(left);
    let to = extractDate(right);
    if (!from && to) {
      const day = left.match(/(\d{1,2})(?:st|nd|rd|th)?/);
      const monthMatch = right.match(/([a-z\u0900-\u097F]{3,12})/i);
      const yearMatch = right.match(/(\d{4})/);
      if (day && monthMatch && monthIndex(monthMatch[1]) != null) {
        const year = yearMatch ? Number(yearMatch[1]) : new Date().getFullYear();
        from = toYmd(new Date(year, monthIndex(monthMatch[1])!, Number(day[1])));
      }
    }
    if (from && to) {
      return from <= to ? { from, to } : { from: to, to: from };
    }
    if (from && !to) return { from, openEnded: true };
    if (to && !from) return { from: to, to };
  }

  const fromOnly =
    /^\s*(?:from|starting)\s+/i.test(s) || /(?:^|\s)से(?:\s|$)/.test(s);
  const single = extractDate(s.replace(/^\s*(?:from|on|for|starting)\s+/i, ""));
  if (single) {
    if (fromOnly && !/\b(to|till|until|through)\b|तक/.test(lower)) {
      return { from: single, openEnded: true };
    }
    return { from: single, to: single };
  }
  return {};
}

function dayOnlyDate(text: string): string | undefined {
  const onlyDay = normalize(text).match(/^\s*(\d{1,2})(?:st|nd|rd|th)?\s*$/);
  if (!onlyDay) return undefined;
  const n = Number(onlyDay[1]);
  if (n < 1 || n > 31) return undefined;
  const now = new Date();
  const d = new Date(now.getFullYear(), now.getMonth(), n);
  if (Number.isNaN(d.getTime())) return undefined;
  return toYmd(d);
}

export function isBookAppointmentIntent(text: string): boolean {
  const s = text.toLowerCase();
  if (/\b(show|list|display|see|view)\s+(my\s+)?(bookings?|appointments?)\b/.test(s)) {
    return false;
  }
  if (/\b(show|list)\s+booking\b/.test(s)) return false;
  return (
    /\b(book(?:ing)?(?:\s+an?)?(?:\s+the)?\s+appointments?)\b/.test(s) ||
    /\b(new|create|make)\s+(a\s+)?(booking|appointment)\b/.test(s) ||
    /\bbook\s+(?:an?\s+)?(?:appointment\s+)?for\b/.test(s) ||
    /अपॉइंटमेंट\s*बुक/.test(text) ||
    /बुक\s*(करो|करें|करना)/.test(text)
  );
}

export function isShowBookingIntent(text: string): boolean {
  const s = text.toLowerCase();
  if (isBookAppointmentIntent(text)) return false;
  return (
    /\b(show|list|display|open|see|view)\s+(my\s+)?(bookings?|appointments?|patients?|patient\s+list)\b/.test(
      s,
    ) ||
    /\b(show|list)\s+booking\b/.test(s) ||
    /\bpatient\s+list\b/.test(s) ||
    /\bmeri\s+booking\b/.test(s) ||
    /बुकिंग\s*(दिखाओ|दिखा|सूची)/.test(text) ||
    /(दिखाओ|दिखा दो)\s*(बुकिंग|अपॉइंटमेंट|मरीज|मरीज़)/.test(text) ||
    /मरी[जज़]ों?\s*की\s*(लिस्ट|सूची)/.test(text) ||
    /पेशेंट\s*(लिस्ट|सूची)/.test(text)
  );
}

function extractTime(text: string): string | undefined {
  const s = text.toLowerCase();
  if (/\bnoon\b|\bmidday\b/.test(s)) return "12:00";
  if (/\bmidnight\b/.test(s)) return "00:00";

  const m = s.match(/\b(\d{1,2})(?::|\s)?(\d{2})?\s*(a\.?\s*m\.?|p\.?\s*m\.?)?\b/);
  if (!m) return undefined;
  let hour = Number(m[1]);
  const minute = m[2] ? Number(m[2]) : 0;
  const mer = (m[3] || "").replace(/\s|\./g, "");
  if (hour > 23 || minute > 59) return undefined;
  if (mer.startsWith("p") && hour < 12) hour += 12;
  if (mer.startsWith("a") && hour === 12) hour = 0;
  if (!mer && hour <= 7) hour += 12;
  return `${pad2(hour)}:${pad2(minute)}`;
}

function clipName(value: string) {
  return value.split(
    /\s+(?:phone|mobile|number|age|gender|email|doctor|dr\.?|with|on|at|tomorrow|today|date|time)\b/i,
  )[0];
}

function extractName(text: string): string | undefined {
  const bookFor = text.match(
    /(?:book(?:ing)?(?:\s+an?)?(?:\s+the)?\s+appointment|appointment)\s+(?:for\s+)?(.+)$/i,
  );
  if (bookFor) return tidyName(clipName(bookFor[1])) || undefined;
  const bookBare = text.match(/\bbook\s+for\s+(.+)$/i);
  if (bookBare) return tidyName(clipName(bookBare[1])) || undefined;
  const nameIs = text.match(
    /(?:patient(?:\s+name)?|name)\s+(?:is\s+|for\s+)?(.+)$/i,
  );
  if (nameIs) return tidyName(clipName(nameIs[1])) || undefined;
  return undefined;
}

function tidyName(value: string) {
  return value
    .replace(/\bplease\b/gi, "")
    .replace(/\bthank you\b/gi, "")
    .replace(/[.,]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

function clipDoctor(value: string) {
  return value
    .split(
      /\s+(?:on|from|to|today|tomorrow|between|for|booking|appointments?|patients?)\b/i,
    )[0]
    .trim();
}

function extractDoctor(text: string): string | undefined {
  const m = text.match(
    /(?:doctor|dr\.?)\s+([a-z][a-z .'-]{1,40})/i,
  );
  if (m) return clipDoctor(m[1]);
  const withDoc = text.match(/\b(?:with|for)\s+(?:doctor|dr\.?)\s+([a-z][a-z .'-]{1,40})/i);
  return withDoc ? clipDoctor(withDoc[1]) : undefined;
}

function applyDateRange(
  parsed: ParsedVoiceCommand,
  text: string,
  allowBareDay: boolean,
) {
  const range = extractDateRange(text);
  parsed.dateFrom = range.from;
  parsed.dateTo = range.to;
  parsed.openEndedRange = range.openEnded;
  if (range.from && range.to && range.from === range.to) {
    parsed.date = range.from;
  }
  if (!parsed.dateFrom && allowBareDay) {
    const day = dayOnlyDate(text);
    if (day) {
      parsed.dateFrom = day;
      parsed.dateTo = day;
      parsed.date = day;
    }
  }
}

export function parseVoiceCommand(
  raw: string,
  expecting?: VoiceExpecting,
): ParsedVoiceCommand {
  const text = normalize(raw);
  const lower = text.toLowerCase();
  const parsed: ParsedVoiceCommand = { raw: text };

  if (
    /^(stop|cancel|never mind|quit)(?:\s+listening)?$/.test(lower) ||
    /^stop voice$/.test(lower)
  ) {
    parsed.intent = "STOP";
    return parsed;
  }

  const showExpecting =
    expecting === "showDoctor" || expecting === "showDate" || expecting === "showDateTo";
  const bookIntent = isBookAppointmentIntent(text);
  if (!bookIntent && (isShowBookingIntent(text) || showExpecting)) {
    parsed.intent = "SHOW_BOOKINGS";
    if (expecting === "showDateTo") {
      const range = extractDateRange(text);
      parsed.dateTo = range.to || range.from || dayOnlyDate(text);
      const doctorQuery = extractDoctor(text);
      if (doctorQuery) parsed.doctorQuery = doctorQuery;
      return parsed;
    }
    applyDateRange(parsed, text, expecting === "showDate");
    const doctorQuery = extractDoctor(text);
    if (doctorQuery) parsed.doctorQuery = doctorQuery;
    else if (expecting === "showDoctor") {
      parsed.doctorQuery = text.replace(/^(doctor|dr\.?)\s+/i, "").trim();
    }
    return parsed;
  }
  if (bookIntent) parsed.intent = "BOOK_APPOINTMENT";

  const confirm =
    /^(book appointment|confirm(?: booking)?|yes book(?: it)?|submit|save booking)\.?$/.test(
      lower,
    ) ||
    /\b(book(?: the)? appointment|confirm booking)\b/.test(lower);
  if (confirm && expecting === "confirm") {
    parsed.intent = "CONFIRM_BOOK";
    return parsed;
  }
  if (
    confirm &&
    !extractName(text) &&
    (expecting === "confirm" || expecting === "idle")
  ) {
    parsed.intent = "CONFIRM_BOOK";
    return parsed;
  }

  const name = extractName(text);
  if (name) {
    parsed.intent = "BOOK_APPOINTMENT";
    parsed.patientName = name;
    parsed.field = "patientName";
  }

  const phone = extractPhone(text);
  if (phone && (expecting === "phone" || /phone|mobile|number/.test(lower) || !name)) {
    if (expecting === "phone" || /phone|mobile|number/.test(lower) || (!name && phone)) {
      parsed.phone = phone;
      parsed.field = parsed.field || "phone";
    }
  }

  const gender = normalizeGender(text);
  if (gender && (expecting === "gender" || /gender|male|female/.test(lower))) {
    parsed.gender = gender;
    parsed.field = parsed.field || "gender";
  }

  const age = extractAge(text);
  if (age && (expecting === "age" || /\bage\b/.test(lower))) {
    parsed.age = age;
    parsed.field = parsed.field || "age";
  }

  const email = extractEmail(text);
  if (email) {
    parsed.email = email;
    parsed.field = parsed.field || "email";
  }

  const doctorQuery = extractDoctor(text);
  if (doctorQuery && expecting !== "patientName") {
    parsed.doctorQuery = doctorQuery;
    parsed.field = parsed.field || "doctor";
  }

  const date = extractDate(text);
  if (date && (expecting === "date" || /date|today|tomorrow|january|february|march|april|may|june|july|august|september|october|november|december|\b\d{1,2}(st|nd|rd|th)?\b/.test(lower))) {
    parsed.date = date;
    parsed.field = parsed.field || "date";
  }

  const time = extractTime(text);
  if (
    time &&
    (expecting === "time" || /\btime\b|\ba\.?m\.?\b|\bp\.?m\.?\b|\bo'?clock\b/.test(lower))
  ) {
    parsed.time = time;
    parsed.field = parsed.field || "time";
  }

  if (expecting && expecting !== "confirm" && expecting !== "idle") {
    if (expecting === "patientName" && !parsed.patientName) {
      if (
        !confirm &&
        !isShowBookingIntent(text) &&
        !/^(book(?:\s+an?)?\s+appointment)$/i.test(text)
      ) {
        parsed.patientName = tidyName(text);
        parsed.field = "patientName";
        parsed.intent = parsed.intent || "BOOK_APPOINTMENT";
      }
    } else if (expecting === "phone" && !parsed.phone) {
      const maybe = extractPhone(text);
      if (maybe) parsed.phone = maybe;
    } else if (expecting === "age" && !parsed.age) {
      const maybe = extractAge(text) || extractAge(`${text} years`);
      if (maybe) parsed.age = maybe;
    } else if (expecting === "gender" && !parsed.gender) {
      parsed.gender = normalizeGender(text);
    } else if (expecting === "email" && !parsed.email) {
      parsed.email = extractEmail(text);
    } else if (expecting === "doctor" && !parsed.doctorQuery) {
      parsed.doctorQuery = text.replace(/^(doctor|dr\.?)\s+/i, "").trim();
    } else if (expecting === "date" && !parsed.date) {
      parsed.date = extractDate(text);
    } else if (expecting === "time" && !parsed.time) {
      parsed.time = extractTime(text);
    } else if (expecting === "address") {
      parsed.address = text.replace(/^(address(?:\s+is)?)\s+/i, "").trim();
    } else if (expecting === "reason") {
      parsed.reason = text.replace(/^(reason|symptoms?)(?:\s+is)?\s+/i, "").trim();
    }
  }

  if (confirm && parsed.patientName) parsed.intent = "BOOK_APPOINTMENT";
  else if (confirm && !parsed.patientName) parsed.intent = "CONFIRM_BOOK";

  return parsed;
}
