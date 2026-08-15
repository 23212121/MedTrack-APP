import { BookingSummary } from "../api";

function formatTime(iso?: string) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleTimeString(undefined, { timeStyle: "short" });
  } catch {
    return iso;
  }
}

function formatDateTime(iso?: string) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString(undefined, {
      dateStyle: "medium",
      timeStyle: "short",
    });
  } catch {
    return iso;
  }
}

function formatDate(iso?: string) {
  if (!iso) return "—";
  try {
    return new Date(`${iso}T12:00:00`).toLocaleDateString(undefined, {
      weekday: "short",
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  } catch {
    return iso;
  }
}

function appointmentStatusLabel(status?: string) {
  switch ((status || "").toUpperCase()) {
    case "BOOKED":
      return "Booked";
    case "CHECKED_IN":
      return "Checked in";
    case "CANCELLED":
      return "Cancelled";
    case "COMPLETED":
      return "Completed";
    default:
      return status || "—";
  }
}

function statusClass(status?: string) {
  return `patient-appt-status patient-appt-status--${(status || "unknown").toLowerCase()}`;
}

function bookedByText(appt: BookingSummary) {
  return (
    appt.bookedByLabel ||
    (appt.bookedBy === "HOSPITAL"
      ? `Hospital reception · ${appt.hospitalName || "—"}`
      : appt.bookedBy === "PATIENT"
        ? "Self (patient portal)"
        : appt.bookedBy || "—")
  );
}

function formatFee(fee?: number, currency?: string) {
  if (fee == null) return "—";
  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency: currency || "INR",
      maximumFractionDigits: 0,
    }).format(fee);
  } catch {
    return `${currency || "INR"} ${fee}`;
  }
}

type DetailItem = {
  label: string;
  value: string;
  wide?: boolean;
};

function buildDetails(appt: BookingSummary): DetailItem[] {
  const items: DetailItem[] = [
    {
      label: "Token number",
      value: appt.tokenNumber != null ? `# ${appt.tokenNumber}` : "Assigning…",
    },
    { label: "Appointment date", value: formatDate(appt.appointmentDate) },
    { label: "Scheduled time", value: formatTime(appt.appointmentTime) },
    {
      label: "Status",
      value: appointmentStatusLabel(appt.appointmentStatus || appt.status),
    },
    { label: "Doctor", value: appt.doctorName || "—" },
    { label: "Hospital", value: appt.hospitalName || "—" },
  ];

  if (appt.department) {
    items.push({ label: "Department", value: appt.department });
  }
  if (appt.clinicName) {
    items.push({ label: "Clinic", value: appt.clinicName });
  }
  if (appt.branch) {
    items.push({ label: "Branch", value: appt.branch });
  }

  items.push({
    label: "Consultation fee",
    value: formatFee(appt.consultationFee, appt.currency),
  });

  if (appt.patientName) {
    items.push({ label: "Patient name", value: appt.patientName });
  }
  if (appt.patientId) {
    items.push({ label: "Patient ID (User ID)", value: appt.patientId });
  }
  if (appt.patientPhone) {
    items.push({ label: "Phone", value: appt.patientPhone });
  }
  if (appt.patientAge != null) {
    items.push({ label: "Age", value: String(appt.patientAge) });
  }
  if (appt.gender) {
    items.push({ label: "Gender", value: appt.gender });
  }
  if (appt.address) {
    items.push({ label: "Address", value: appt.address, wide: true });
  }
  if (appt.reason) {
    items.push({ label: "Reason for visit", value: appt.reason, wide: true });
  }

  items.push({ label: "Booked by", value: bookedByText(appt), wide: true });

  const createdByName = appt.createdByName || appt.createdBy;
  if (createdByName) {
    items.push({ label: "Created by", value: createdByName });
  }
  if (appt.bookedAt) {
    items.push({ label: "Booked on", value: formatDateTime(appt.bookedAt) });
  } else if (appt.createdAt) {
    items.push({ label: "Booked on", value: formatDateTime(appt.createdAt) });
  }

  return items;
}

type Props = {
  appt: BookingSummary;
  compactHeader?: boolean;
};

export default function PatientBookingDetails({ appt, compactHeader }: Props) {
  const details = buildDetails(appt);
  const status = appt.appointmentStatus || appt.status;

  return (
    <div className="patient-booking-details">
      {!compactHeader && (
        <div className="patient-appt-status-row">
          <strong>{appt.doctorName || appt.doctorId}</strong>
          <span className={statusClass(status)}>
            {appointmentStatusLabel(status)}
          </span>
          {appt.tokenNumber != null && (
            <span className="patient-appt-token-tag">Token # {appt.tokenNumber}</span>
          )}
        </div>
      )}
      <div className="patient-queue-meta patient-booking-details-grid">
        {details.map((item) => (
          <div
            key={item.label}
            className={item.wide ? "patient-booking-detail--wide" : undefined}
          >
            <strong>{item.label}</strong>
            <span>{item.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
