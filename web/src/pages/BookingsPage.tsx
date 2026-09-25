import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import {
  AllCommunityModule,
  ColDef,
  ICellRendererParams,
  ModuleRegistry,
  themeQuartz,
} from "ag-grid-community";
import { AgGridReact } from "ag-grid-react";
import {
  api,
  Appointment,
  Booking,
  CreateBookingBody,
  DoctorAvailableDay,
  HospitalDoctor,
  HospitalRegistrationSummary,
} from "../api";
import { session } from "../dl/MedTrackSession";
import { useT } from "../i18n";
import { toast } from "../toast";
import VoiceBooking from "../voice/VoiceBooking";

ModuleRegistry.registerModules([AllCommunityModule]);

const bookingsTheme = themeQuartz.withParams({
  accentColor: "#0e7c86",
  backgroundColor: "#ffffff",
  borderColor: "#c5d4db",
  browserColorScheme: "light",
  chromeBackgroundColor: "#f4f8f9",
  foregroundColor: "#14212b",
  headerBackgroundColor: "#f4f8f9",
  headerFontSize: 13,
  headerFontWeight: 600,
  headerTextColor: "#5a6b76",
  fontFamily: "DM Sans, Segoe UI, sans-serif",
  fontSize: 14,
  oddRowBackgroundColor: "#f8fbfb",
  rowHoverColor: "#e8f1f2",
  spacing: 6,
  wrapperBorderRadius: 10,
});

function StatusCell(params: ICellRendererParams<Booking>) {
  const status = params.data?.status;
  if (!status) return "—";
  return <span className={`badge ${status}`}>{status}</span>;
}

function genderLabel(gender: string | undefined, t: (key: string) => string) {
  if (!gender) return "—";
  if (gender === "FEMALE") return t("Female");
  if (gender === "MALE") return t("Male");
  if (gender === "OTHER") return t("Other");
  return t("Prefer not to say");
}

function todayYmd() {
  const d = new Date();
  const off = d.getTimezoneOffset();
  return new Date(d.getTime() - off * 60_000).toISOString().slice(0, 10);
}

function addDaysYmd(ymd: string, days: number) {
  const d = new Date(`${ymd}T00:00:00`);
  d.setDate(d.getDate() + days);
  const off = d.getTimezoneOffset();
  return new Date(d.getTime() - off * 60_000).toISOString().slice(0, 10);
}

function defaultTimeLocal() {
  const d = new Date();
  d.setMinutes(0, 0, 0);
  d.setHours(d.getHours() + 1);
  const off = d.getTimezoneOffset();
  return new Date(d.getTime() - off * 60_000).toISOString().slice(11, 16);
}

/** Combine YYYY-MM-DD + HH:mm (local) into ISO-8601 Instant for JPA. */
function toInstant(date: string, time: string) {
  return new Date(`${date}T${time}:00`).toISOString();
}

function doctorLabel(d: HospitalDoctor) {
  const dept = d.department?.trim() || d.specialization?.trim();
  return dept ? `${d.doctorName} — ${dept}` : d.doctorName;
}

const emptyForm = {
  patientName: "",
  patientPhone: "",
  patientEmail: "",
  patientAge: "",
  gender: "",
  address: "",
  reason: "",
  appointmentDate: todayYmd(),
  appointmentTime: defaultTimeLocal(),
  doctorId: "",
  doctorName: "",
  hospitalName: "",
  hospitalId: "" as string | number | "",
  consultationFee: "",
};

/** Patient portal booking — start with every field empty (no session prefill). */
const blankPublicForm = {
  ...emptyForm,
  appointmentDate: "",
  appointmentTime: "",
};

type BookingsPageProps = {
  /** Patient self-booking — no login, hide staff list */
  publicMode?: boolean;
  /** Limit doctors to this hospital (from share link) */
  hospitalIdFilter?: string;
  /** Pre-fill logged-in patient phone */
  patientPhone?: string;
  /** Pre-fill logged-in patient name */
  patientName?: string;
  /** Called after a successful book (e.g. refresh upcoming list on portal) */
  onBooked?: () => void;
};

function mapDoctor(d: HospitalDoctor): HospitalDoctor {
  return {
    doctorId: d.doctorId,
    doctorName: d.doctorName,
    firstName: d.firstName,
    middleName: d.middleName,
    lastName: d.lastName,
    specialization: d.specialization,
    department: d.department,
    hospitalId: d.hospitalId,
    hospitalName: d.hospitalName,
    clinicName: d.clinicName,
    consultationFee: d.consultationFee,
  };
}

function appointmentToBooking(a: Appointment): Booking {
  return {
    id: a.id,
    hospitalId: a.hospitalId ?? 0,
    hospitalName: a.hospitalName,
    doctorId: a.doctorId,
    doctorName: a.doctorName,
    department: a.department,
    patientName: a.patientName,
    patientPhone: a.patientPhone || a.phoneNumber || "",
    patientAge: a.patientAge,
    gender: a.gender,
    address: a.address,
    reason: a.reason,
    appointmentDate: a.appointmentDate,
    appointmentTime: a.appointmentTime,
    tokenNumber: a.tokenNumber,
    status: a.status,
    consultationFee: a.consultationFee,
    currency: a.currency,
    createdAt: a.createdDate,
  };
}

export default function BookingsPage({
  publicMode = false,
  hospitalIdFilter,
  patientPhone,
  patientName,
  onBooked,
}: BookingsPageProps = {}) {
  const t = useT();
  const navigate = useNavigate();
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [hospitals, setHospitals] = useState<HospitalRegistrationSummary[]>([]);
  const [allDoctors, setAllDoctors] = useState<HospitalDoctor[]>([]);
  /** Optional hospital filter — empty means show all doctors */
  const [selectedHospitalId, setSelectedHospitalId] = useState(
    hospitalIdFilter ? String(hospitalIdFilter) : "",
  );
  const [selectedDepartment, setSelectedDepartment] = useState("");
  const [form, setForm] = useState(publicMode ? blankPublicForm : emptyForm);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [loadingDoctors, setLoadingDoctors] = useState(false);
  const [loadingHospitals, setLoadingHospitals] = useState(false);
  const [availFrom, setAvailFrom] = useState(todayYmd());
  const [availTo, setAvailTo] = useState(addDaysYmd(todayYmd(), 14));
  const [availDays, setAvailDays] = useState<DoctorAvailableDay[]>([]);
  const [loadingAvail, setLoadingAvail] = useState(false);
  const [availNote, setAvailNote] = useState("");
  const [dateWarning, setDateWarning] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [voiceList, setVoiceList] = useState<Booking[] | null>(null);
  const [voiceListNote, setVoiceListNote] = useState("");
  const formRef = useRef<HTMLFormElement>(null);

  const hospitalDoctors = useMemo(() => {
    if (!selectedHospitalId) return allDoctors;
    return allDoctors.filter(
      (d) => String(d.hospitalId) === String(selectedHospitalId),
    );
  }, [allDoctors, selectedHospitalId]);

  const departmentOptions = useMemo(() => {
    const names = hospitalDoctors
      .map((d) => d.department?.trim())
      .filter((name): name is string => !!name);
    return Array.from(new Set(names)).sort((a, b) => a.localeCompare(b));
  }, [hospitalDoctors]);

  const doctors = useMemo(() => {
    if (!selectedDepartment) return hospitalDoctors;
    return hospitalDoctors.filter(
      (d) => (d.department?.trim() || "") === selectedDepartment,
    );
  }, [hospitalDoctors, selectedDepartment]);

  const loggedInDoctorId = session.isDoctor() ? session.getUserId() : "";
  const loggedInDoctorName = useMemo(() => {
    if (!loggedInDoctorId) return "";
    const found = hospitalDoctors.find((d) => d.doctorId === loggedInDoctorId);
    return found?.doctorName || session.getUsername() || loggedInDoctorId;
  }, [hospitalDoctors, loggedInDoctorId]);

  const visibleBookings = voiceList ?? bookings;

  const columnDefs = useMemo<ColDef<Booking>[]>(
    () => [
      {
        headerName: t("Token"),
        field: "tokenNumber",
        width: 100,
        filter: "agNumberColumnFilter",
        filterParams: {
          filterOptions: ["equals", "lessThan", "greaterThan"],
          maxNumConditions: 1,
          debounceMs: 200,
          buttons: ["reset"],
        },
        valueFormatter: (p) => (p.value == null ? "—" : String(p.value)),
      },
      {
        headerName: t("Patient"),
        field: "patientName",
        flex: 1,
        minWidth: 180,
      },
      {
        headerName: t("Phone"),
        field: "patientPhone",
        minWidth: 130,
      },
      {
        headerName: t("Age"),
        field: "patientAge",
        width: 90,
        filter: "agNumberColumnFilter",
        filterParams: {
          filterOptions: ["equals", "lessThan", "greaterThan"],
          maxNumConditions: 1,
          debounceMs: 200,
          buttons: ["reset"],
        },
        valueFormatter: (p) => (p.value == null ? "—" : String(p.value)),
      },
      {
        headerName: t("Gender"),
        field: "gender",
        width: 120,
        valueFormatter: (p) => genderLabel(p.value as string | undefined, t),
        filterValueGetter: (p) => genderLabel(p.data?.gender, t),
      },
      {
        headerName: t("Hospital"),
        field: "hospitalName",
        minWidth: 150,
        valueFormatter: (p) => (p.value ? String(p.value) : "—"),
      },
      {
        headerName: t("Department"),
        field: "department",
        minWidth: 140,
        valueFormatter: (p) => (p.value ? t(String(p.value)) : "—"),
        filterValueGetter: (p) =>
          p.data?.department ? t(p.data.department) : "",
      },
      {
        headerName: t("Doctor ID"),
        field: "doctorId",
        minWidth: 130,
      },
      {
        headerName: t("Date"),
        field: "appointmentDate",
        minWidth: 120,
      },
      {
        headerName: t("Time"),
        field: "appointmentTime",
        minWidth: 110,
        valueFormatter: (p) =>
          p.value ? new Date(String(p.value)).toLocaleTimeString() : "—",
        filterValueGetter: (p) =>
          p.data?.appointmentTime
            ? new Date(p.data.appointmentTime).toLocaleTimeString()
            : "",
      },
      {
        headerName: t("Reason"),
        field: "reason",
        minWidth: 140,
        valueFormatter: (p) => (p.value ? String(p.value) : "—"),
      },
      {
        headerName: t("Fee"),
        colId: "fee",
        minWidth: 110,
        filter: "agNumberColumnFilter",
        filterParams: {
          filterOptions: ["equals", "lessThan", "greaterThan"],
          maxNumConditions: 1,
          debounceMs: 200,
          buttons: ["reset"],
        },
        valueGetter: (p) => p.data?.consultationFee,
        valueFormatter: (p) => {
          const row = p.data;
          if (!row || row.consultationFee == null) return "—";
          return `${row.currency ?? "INR"} ${row.consultationFee}`;
        },
      },
      {
        headerName: t("Status"),
        field: "status",
        minWidth: 120,
        cellRenderer: StatusCell,
      },
      {
        headerName: t("Address"),
        field: "address",
        minWidth: 160,
        valueFormatter: (p) => (p.value ? String(p.value) : "—"),
      },
    ],
    [t],
  );

  const defaultColDef = useMemo<ColDef<Booking>>(
    () => ({
      sortable: true,
      resizable: true,
      filter: "agTextColumnFilter",
      floatingFilter: false,
      suppressHeaderMenuButton: true,
      suppressHeaderFilterButton: false,
      filterParams: {
        filterOptions: ["contains"],
        debounceMs: 200,
        maxNumConditions: 1,
        buttons: ["reset"],
      },
    }),
    [],
  );

  async function load() {
    if (publicMode) return;
    setError("");
    setVoiceList(null);
    setVoiceListNote("");
    try {
      const data = await api.bookings();
      setBookings(data.bookings);
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Failed to load bookings — is booking-service running?"
      );
    }
  }

  async function handleShowBookings(query: {
    doctorId: string;
    doctorName: string;
    from: string;
    to: string;
  }) {
    setError("");
    const data = await api.appointments({ doctorId: query.doctorId });
    const rows = (data.appointments || [])
      .filter(
        (a) =>
          a.appointmentDate >= query.from && a.appointmentDate <= query.to,
      )
      .map(appointmentToBooking)
      .sort((a, b) => {
        const byDate = a.appointmentDate.localeCompare(b.appointmentDate);
        if (byDate) return byDate;
        return String(a.appointmentTime).localeCompare(String(b.appointmentTime));
      });
    setVoiceList(rows);
    const rangeLabel =
      query.from === query.to ? query.from : `${query.from} → ${query.to}`;
    setVoiceListNote(`${query.doctorName || query.doctorId} · ${rangeLabel}`);
    return {
      count: rows.length,
      patients: rows.map((r) => r.patientName).filter(Boolean),
    };
  }

  async function loadHospitals() {
    setLoadingHospitals(true);
    try {
      const data = await api.hospitals();
      const rows = (data.hospitals ?? []).slice().sort((a, b) =>
        String(a.hospitalName).localeCompare(String(b.hospitalName)),
      );
      setHospitals(rows);
    } catch {
      setHospitals([]);
    } finally {
      setLoadingHospitals(false);
    }
  }

  async function loadDoctors() {
    setLoadingDoctors(true);
    setError("");
    try {
      const data = await api.doctors();
      const rows = (data.doctors ?? []) as unknown as HospitalDoctor[];
      const hospitalDoctors = rows
        .filter((d) => d.doctorId && d.hospitalId != null)
        .map(mapDoctor);
      setAllDoctors(hospitalDoctors);
      setForm((prev) => {
        if (!prev.doctorId) return prev;
        const pool = selectedHospitalId
          ? hospitalDoctors.filter(
              (d) => String(d.hospitalId) === String(selectedHospitalId),
            )
          : hospitalDoctors;
        const stillThere = pool.some((d) => d.doctorId === prev.doctorId);
        if (stillThere) return prev;
        return {
          ...prev,
          doctorId: "",
          doctorName: "",
          hospitalName: "",
          hospitalId: selectedHospitalId || "",
          consultationFee: "",
        };
      });
    } catch (e) {
      setAllDoctors([]);
      setError(e instanceof Error ? e.message : "Failed to load doctors");
    } finally {
      setLoadingDoctors(false);
    }
  }

  useEffect(() => {
    if (hospitalIdFilter) {
      setSelectedHospitalId(String(hospitalIdFilter));
    }
  }, [hospitalIdFilter]);

  useEffect(() => {
    // Hospital booking desk may prefill patient; patient portal stays blank.
    if (publicMode) return;
    if (patientPhone || patientName) {
      setForm((prev) => ({
        ...prev,
        patientPhone: patientPhone || prev.patientPhone,
        patientName: patientName || prev.patientName,
      }));
    }
  }, [publicMode, patientPhone, patientName]);

  useEffect(() => {
    if (!formOpen || publicMode) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setFormOpen(false);
    };
    window.addEventListener("keydown", onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [formOpen, publicMode]);

  useEffect(() => {
    load();
    void loadHospitals();
    void loadDoctors();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [publicMode]);

  useEffect(() => {
    // When hospital filter changes, clear doctor if it no longer belongs
    setForm((prev) => {
      if (!prev.doctorId) return prev;
      const stillThere = doctors.some((d) => d.doctorId === prev.doctorId);
      if (stillThere) return prev;
      return {
        ...prev,
        doctorId: "",
        doctorName: "",
        clinicName: "",
        hospitalName: "",
        hospitalId: selectedHospitalId || "",
        consultationFee: "",
      };
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedHospitalId, doctors]);

  function setField<K extends keyof typeof emptyForm>(key: K, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  function onHospitalChange(hospitalId: string) {
    setSelectedHospitalId(hospitalId);
    setSelectedDepartment("");
    setForm((prev) => ({
      ...prev,
      doctorId: "",
      doctorName: "",
      hospitalName: "",
      hospitalId: hospitalId || "",
      consultationFee: "",
    }));
  }

  function onDepartmentChange(department: string) {
    setSelectedDepartment(department);
    setForm((prev) => {
      if (!prev.doctorId) return prev;
      const stillThere = hospitalDoctors.some(
        (d) =>
          d.doctorId === prev.doctorId &&
          (!department || (d.department?.trim() || "") === department),
      );
      if (stillThere) return prev;
      return {
        ...prev,
        doctorId: "",
        doctorName: "",
        hospitalName: "",
        hospitalId: selectedHospitalId || "",
        consultationFee: "",
      };
    });
  }

  function onDoctorChange(doctorId: string) {
    const selected =
      doctors.find((d) => d.doctorId === doctorId) ||
      hospitalDoctors.find((d) => d.doctorId === doctorId) ||
      allDoctors.find((d) => d.doctorId === doctorId);
    if (!selected) {
      setForm((prev) => ({
        ...prev,
        doctorId: "",
        doctorName: "",
        clinicName: "",
        hospitalName: "",
        hospitalId: selectedHospitalId || "",
        consultationFee: "",
      }));
      setAvailDays([]);
      setAvailNote("");
      setDateWarning("");
      return;
    }
    setDateWarning("");
    // Selecting a doctor also sets hospital (even if hospital dropdown was skipped)
    if (!selectedHospitalId && selected.hospitalId != null) {
      setSelectedHospitalId(String(selected.hospitalId));
    }
    setForm((prev) => ({
      ...prev,
      doctorId: selected.doctorId,
      doctorName: selected.doctorName,
      hospitalName: selected.hospitalName || selected.clinicName || "",
      hospitalId: selected.hospitalId,
      consultationFee:
        selected.consultationFee != null
          ? String(selected.consultationFee)
          : prev.consultationFee,
    }));
  }

  async function loadDoctorAvailability(doctorId: string, from: string, to: string) {
    if (!doctorId || !from || !to) {
      setAvailDays([]);
      setAvailNote("");
      return;
    }
    if (to < from) {
      setAvailNote("To date must be on or after From date");
      setAvailDays([]);
      return;
    }
    setLoadingAvail(true);
    setAvailNote("");
    try {
      const data = await api.availableDays(doctorId, from, to);
      const days = data.days || [];
      setAvailDays(days);
      setForm((prev) => {
        if (!prev.appointmentDate) return prev;
        const picked = days.find((d) => d.date === prev.appointmentDate);
        if (picked && !picked.available) {
          setDateWarning(
            unavailableMessage(
              prev.doctorName,
              prev.appointmentDate,
              picked.summary,
              (data.availableDates || []).filter((d) => d !== prev.appointmentDate),
            ),
          );
          return { ...prev, appointmentDate: "" };
        }
        return prev;
      });
      if (!data.hasWeeklySchedule) {
        setAvailNote(
          "No weekly schedule set for this doctor yet — availability may show as Off."
        );
      } else if ((data.availableDates || []).length === 0) {
        setAvailNote("Doctor is not available in the selected date range.");
      } else {
        setAvailNote(
          `${data.availableDates.length} available day(s) in range. Click a day to book.`
        );
      }
    } catch (e) {
      setAvailDays([]);
      setAvailNote(e instanceof Error ? e.message : "Failed to load availability");
    } finally {
      setLoadingAvail(false);
    }
  }

  useEffect(() => {
    if (!form.doctorId) {
      setAvailDays([]);
      setAvailNote("");
      return;
    }
    void loadDoctorAvailability(form.doctorId, availFrom, availTo);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form.doctorId, availFrom, availTo]);

  function doctorOptionLabel(d: HospitalDoctor) {
    const base = doctorLabel(d);
    if (selectedHospitalId) return base;
    const hosp = d.hospitalName?.trim() || (d.hospitalId != null ? `#${d.hospitalId}` : "");
    return hosp ? `${base} (${hosp})` : base;
  }

  function formatSuggestDates(dates: string[], max = 5) {
    const slice = dates.slice(0, max);
    if (slice.length === 0) return "";
    const extra = dates.length > max ? ` (+${dates.length - max} more)` : "";
    return slice.join(", ") + extra;
  }

  function unavailableMessage(
    doctorName: string,
    date: string,
    daySummary: string | undefined,
    suggestDates: string[],
  ) {
    const name = doctorName || "Doctor";
    const why = daySummary ? ` (${daySummary})` : "";
    const suggest = formatSuggestDates(suggestDates);
    if (suggest) {
      return `${name} is not available on ${date}${why}. Please choose an available date: ${suggest}.`;
    }
    return `${name} is not available on ${date}${why}. Please check availability dates above and pick another date.`;
  }

  async function checkAppointmentDateAvailable(
    doctorId: string,
    doctorName: string,
    appointmentDate: string,
  ): Promise<{ ok: boolean; message: string; suggestDates: string[] }> {
    if (!doctorId || !appointmentDate) {
      return { ok: true, message: "", suggestDates: [] };
    }

    // Prefer already-loaded range; otherwise fetch a window around the chosen date
    let days = availDays;
    let suggestDates = days.filter((d) => d.available).map((d) => d.date);

    const inLoadedRange =
      days.length > 0 &&
      appointmentDate >= (days[0]?.date || "") &&
      appointmentDate <= (days[days.length - 1]?.date || "");

    if (!inLoadedRange) {
      const from = appointmentDate < todayYmd() ? todayYmd() : appointmentDate;
      const to = addDaysYmd(from, 21);
      try {
        const data = await api.availableDays(doctorId, from, to);
        days = data.days || [];
        suggestDates = data.availableDates || [];
      } catch {
        return {
          ok: false,
          message: "Could not verify doctor availability. Please try again.",
          suggestDates: [],
        };
      }
    }

    const day = days.find((d) => d.date === appointmentDate);
    if (day?.available) {
      return { ok: true, message: "", suggestDates };
    }

    // Also suggest from the filter panel range if different
    if (suggestDates.length === 0) {
      suggestDates = availDays.filter((d) => d.available).map((d) => d.date);
    }

    return {
      ok: false,
      message: unavailableMessage(
        doctorName,
        appointmentDate,
        day?.summary,
        suggestDates.filter((d) => d !== appointmentDate),
      ),
      suggestDates,
    };
  }

  async function onAppointmentDateChange(date: string): Promise<{
    ok: boolean;
    message?: string;
  }> {
    setField("appointmentDate", date);
    setDateWarning("");
    setError("");
    if (!form.doctorId || !date) return { ok: true };
    const result = await checkAppointmentDateAvailable(
      form.doctorId,
      form.doctorName,
      date,
    );
    if (!result.ok) {
      setDateWarning(result.message);
      setField("appointmentDate", "");
      return { ok: false, message: result.message };
    }
    return { ok: true };
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form.doctorId) {
      setError("Select a doctor");
      return;
    }

    setSaving(true);
    setError("");
    setDateWarning("");
    try {
      const availability = await checkAppointmentDateAvailable(
        form.doctorId,
        form.doctorName,
        form.appointmentDate,
      );
      if (!availability.ok) {
        setDateWarning(availability.message);
        setError(availability.message);
        setSaving(false);
        return;
      }

      const loggedInPatientId = session.isPatient() ? session.getPatientId() : "";
      const body: CreateBookingBody = {
        doctorId: form.doctorId,
        hospitalId:
          form.hospitalId !== "" ? Number(form.hospitalId) : undefined,
        patientId: loggedInPatientId || undefined,
        patientName: form.patientName.trim(),
        patientPhone: form.patientPhone.trim(),
        patientEmail: form.patientEmail.trim() || undefined,
        patientAge: form.patientAge ? Number(form.patientAge) : undefined,
        gender: form.gender || undefined,
        address: form.address.trim() || undefined,
        reason: form.reason.trim() || undefined,
        appointmentDate: form.appointmentDate,
        appointmentTime: toInstant(form.appointmentDate, form.appointmentTime),
        bookedBy: publicMode ? "PATIENT" : "HOSPITAL",
        createdBy: publicMode
          ? "PATIENT"
          : session.getUsername() || "HOSPITAL",
      };
      const res = await api.createBooking(body);
      toast.success(
        `${res.message}. Token # ${res.booking.tokenNumber} for ${res.booking.patientName}. SMS confirmation queued${
          form.patientEmail.trim()
            ? `; email will be sent to ${form.patientEmail.trim()} if SMTP is configured`
            : ""
        }.`,
      );
      onBooked?.();
      if (publicMode && session.isPatient() && !onBooked) {
        navigate("/patient/status");
        return;
      }
      if (publicMode) {
        setForm(blankPublicForm);
        if (!hospitalIdFilter) setSelectedHospitalId("");
        setSelectedDepartment("");
        setAvailDays([]);
        setAvailNote("");
        setDateWarning("");
      } else {
        setForm({
          ...emptyForm,
          appointmentDate: form.appointmentDate,
          appointmentTime: form.appointmentTime,
          doctorId: form.doctorId,
          doctorName: form.doctorName,
          hospitalName: form.hospitalName,
          hospitalId: form.hospitalId,
          consultationFee: form.consultationFee,
        });
        setFormOpen(false);
      }
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    } finally {
      setSaving(false);
    }
  }

  const bookingForm = (
      <form
        ref={formRef}
        className="stack"
        onSubmit={onSubmit}
      >
        <VoiceBooking
          draft={form}
          doctors={hospitalDoctors}
          publicMode={publicMode}
          deskMode={!publicMode}
          loggedInDoctorId={loggedInDoctorId}
          loggedInDoctorName={loggedInDoctorName}
          onPatch={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
          onSelectDoctor={(doctorId) => {
            const selected = hospitalDoctors.find((d) => d.doctorId === doctorId);
            setSelectedDepartment(selected?.department?.trim() || "");
            onDoctorChange(doctorId);
          }}
          onSelectDate={(date) => onAppointmentDateChange(date)}
          onConfirmBook={() => formRef.current?.requestSubmit()}
          onNeedBookForm={publicMode ? undefined : () => setFormOpen(true)}
          onShowBookings={publicMode ? undefined : handleShowBookings}
          onAfterShowBookings={publicMode ? undefined : () => setFormOpen(false)}
        />

        <div className="booking-hospital-doctor-row">
          <label className="booking-doctor-field">
            <span>
              {t("Hospital")}{" "}
              <span style={{ color: "var(--muted)", fontWeight: 500 }}>{t("(optional)")}</span>
            </span>
            <select
              value={selectedHospitalId}
              onChange={(e) => onHospitalChange(e.target.value)}
              disabled={loadingHospitals}
            >
              <option value="">
                {loadingHospitals
                  ? t("Loading hospitals…")
                  : t("All hospitals — or pick one to filter doctors")}
              </option>
              {hospitals.map((h) => (
                <option key={h.id ?? h.hospitalId} value={String(h.id ?? h.hospitalId)}>
                  {h.hospitalName}
                  {h.city ? ` — ${h.city}` : ""} (ID {h.id ?? h.hospitalId})
                </option>
              ))}
            </select>
          </label>

          <label className="booking-doctor-field">
            <span>
              {t("Department")}{" "}
              <span style={{ color: "var(--muted)", fontWeight: 500 }}>{t("(optional)")}</span>
            </span>
            <select
              value={selectedDepartment}
              onChange={(e) => onDepartmentChange(e.target.value)}
              disabled={loadingDoctors}
            >
              <option value="">
                {departmentOptions.length === 0
                  ? t("All departments")
                  : t("All departments — or pick one to filter doctors")}
              </option>
              {departmentOptions.map((name) => (
                <option key={name} value={name}>
                  {t(name)}
                </option>
              ))}
            </select>
          </label>

          <label className="booking-doctor-field">
            <span>
              {t("Doctor")}
              <span className="required-mark" aria-hidden="true">
                *
              </span>
            </span>
            <select
              value={form.doctorId}
              onChange={(e) => onDoctorChange(e.target.value)}
              required
              disabled={loadingDoctors || doctors.length === 0}
            >
              <option value="">
                {loadingDoctors
                  ? t("Loading doctors…")
                  : doctors.length === 0
                    ? selectedDepartment
                      ? t("No doctors in this department")
                      : selectedHospitalId
                        ? t("No doctors for this hospital")
                        : t("No doctors available")
                    : t("Select doctor")}
              </option>
              {doctors.map((d) => (
                <option key={d.doctorId} value={d.doctorId}>
                  {doctorOptionLabel(d)}
                </option>
              ))}
            </select>
          </label>
        </div>

        {form.doctorId && (
          <div className="doctor-avail-panel">
            <h3 className="doctor-avail-title">{t("Doctor availability")}</h3>
            <p className="lead" style={{ marginBottom: "0.65rem" }}>
              {t("Choose From / To dates to see when {name} is available.", {
                name: form.doctorName || t("this doctor"),
              })}
            </p>
            <div className="grid-2">
              <label>
                {t("From date")}
                <input
                  type="date"
                  value={availFrom}
                  min={todayYmd()}
                  onChange={(e) => setAvailFrom(e.target.value)}
                />
              </label>
              <label>
                {t("To date")}
                <input
                  type="date"
                  value={availTo}
                  min={availFrom || todayYmd()}
                  onChange={(e) => setAvailTo(e.target.value)}
                />
              </label>
            </div>
            {loadingAvail && (
              <p className="lead" style={{ margin: "0.55rem 0 0" }}>
                {t("Checking availability…")}
              </p>
            )}
            {availNote && !loadingAvail && (
              <p className="lead" style={{ margin: "0.55rem 0 0" }}>
                {availNote}
              </p>
            )}
            {!loadingAvail && availDays.length > 0 && (
              <div className="doctor-avail-list">
                {availDays.map((d) => (
                  <button
                    key={d.date}
                    type="button"
                    className={
                      d.available
                        ? form.appointmentDate === d.date
                          ? "doctor-avail-day is-available is-selected"
                          : "doctor-avail-day is-available"
                        : "doctor-avail-day is-unavailable"
                    }
                    disabled={!d.available}
                    onClick={() => {
                      if (!d.available) return;
                      void onAppointmentDateChange(d.date);
                      if (d.windows?.[0]?.start) {
                        setField("appointmentTime", d.windows[0].start.slice(0, 5));
                      }
                    }}
                    title={d.summary}
                  >
                    <strong>{d.date}</strong>
                    <span>{d.dayName.slice(0, 3)}</span>
                    <em>{d.summary}</em>
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        <div className="grid-2">
          <label>
            <span>
              {t("Patient name")}
              <span className="required-mark" aria-hidden="true">*</span>
            </span>
            <input
              value={form.patientName}
              onChange={(e) => setField("patientName", e.target.value)}
              required
            />
          </label>
          <label>
            <span>
              {t("Patient phone")}
              <span className="required-mark" aria-hidden="true">*</span>
            </span>
            <input
              value={form.patientPhone}
              onChange={(e) => setField("patientPhone", e.target.value)}
              placeholder="9876543210"
              required
            />
          </label>
          <label>
            <span>
              {t("Patient email")}
              {publicMode ? (
                <span style={{ color: "var(--muted)", fontWeight: 500 }}>
                  {t(" (for booking confirmation)")}
                </span>
              ) : null}
            </span>
            <input
              type="email"
              value={form.patientEmail}
              onChange={(e) => setField("patientEmail", e.target.value)}
              placeholder="azherkhan061@gmail.com"
              required={publicMode}
            />
          </label>
          <label>
            {t("Age")}
            <input
              type="number"
              min={1}
              max={120}
              value={form.patientAge}
              onChange={(e) => setField("patientAge", e.target.value)}
            />
          </label>
          <label>
            {t("Gender")}
            <select
              value={form.gender}
              onChange={(e) => setField("gender", e.target.value)}
            >
              <option value="">—</option>
              <option value="FEMALE">{t("Female")}</option>
              <option value="MALE">{t("Male")}</option>
              <option value="OTHER">{t("Other")}</option>
              <option value="UNKNOWN">{t("Prefer not to say")}</option>
            </select>
          </label>
        </div>

        <label>
          {t("Address")}
          <input
            value={form.address}
            onChange={(e) => setField("address", e.target.value)}
          />
        </label>
        <label>
          {t("Reason / symptoms")}
          <textarea
            rows={2}
            value={form.reason}
            onChange={(e) => setField("reason", e.target.value)}
          />
        </label>

        {form.doctorId && form.consultationFee && (
          <p className="lead" style={{ margin: 0 }}>
            Consultation fee (from doctor profile): INR {form.consultationFee}
            {form.hospitalName ? ` · ${form.hospitalName}` : ""}
          </p>
        )}

        <div className="grid-2">
          <label>
            <span>
              {t("Appointment date")}
              <span className="required-mark" aria-hidden="true">*</span>
            </span>
            <input
              type="date"
              value={form.appointmentDate}
              min={todayYmd()}
              onChange={(e) => void onAppointmentDateChange(e.target.value)}
              required
              aria-invalid={!!dateWarning}
            />
            {availDays.some((d) => !d.available) && (
              <span className="muted" style={{ display: "block", marginTop: "0.35rem" }}>
                {t("Grey / disabled days above cannot be booked (doctor is busy).")}
              </span>
            )}
          </label>
          <label>
            <span>
              {t("Appointment time")}
              <span className="required-mark" aria-hidden="true">*</span>
            </span>
            <input
              type="time"
              value={form.appointmentTime}
              onChange={(e) => setField("appointmentTime", e.target.value)}
              required
            />
          </label>
        </div>

        {dateWarning && (
          <div className="msg error" role="alert">
            {dateWarning}
            {availDays.some((d) => d.available) && (
              <div className="doctor-avail-suggest">
                <span>{t("Suggested available dates:")}</span>
                <div className="doctor-avail-suggest-chips">
                  {availDays
                    .filter((d) => d.available)
                    .slice(0, 8)
                    .map((d) => (
                      <button
                        key={d.date}
                        type="button"
                        className="doctor-avail-chip"
                        onClick={() => {
                          void onAppointmentDateChange(d.date);
                          if (d.windows?.[0]?.start) {
                            setField("appointmentTime", d.windows[0].start.slice(0, 5));
                          }
                        }}
                      >
                        {d.date}
                      </button>
                    ))}
                </div>
              </div>
            )}
          </div>
        )}

        <button type="submit" disabled={saving || !form.doctorId || !!dateWarning}>
          {saving ? t("Saving…") : publicMode ? t("Book appointment") : t("Save booking")}
        </button>
      </form>
  );

  return (
    <section className={publicMode ? "patient-booking-form" : undefined}>
      {publicMode && error ? <div className="msg error">{error}</div> : null}
      {publicMode ? bookingForm : null}

      {!publicMode && (
        <>
          {error && !formOpen ? <div className="msg error">{error}</div> : null}
          <div className="panel bookings-toolbar">
            <div>
              <h2>{t("Bookings ({count})", { count: visibleBookings.length })}</h2>
              {voiceListNote ? (
                <p className="voice-list-note">
                  {t("Showing bookings for {note}", { note: voiceListNote })}
                </p>
              ) : null}
            </div>
            <div className="bookings-toolbar-actions">
              <button type="button" className="secondary" onClick={() => void load()}>
                {voiceList ? t("Show all bookings") : t("Refresh list")}
              </button>
              <button
                type="button"
                onClick={() => {
                  setError("");
                  setDateWarning("");
                  setFormOpen(true);
                }}
              >
                {t("Book appointment")}
              </button>
            </div>
            <div className="bookings-toolbar-voice">
                <VoiceBooking
                  draft={form}
                  doctors={hospitalDoctors}
                  deskMode
                  loggedInDoctorId={loggedInDoctorId}
                  loggedInDoctorName={loggedInDoctorName}
                  onPatch={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
                  onSelectDoctor={(doctorId) => {
                    const selected = hospitalDoctors.find((d) => d.doctorId === doctorId);
                    setSelectedDepartment(selected?.department?.trim() || "");
                    onDoctorChange(doctorId);
                  }}
                  onSelectDate={(date) => onAppointmentDateChange(date)}
                  onConfirmBook={() => {
                    setFormOpen(true);
                    window.setTimeout(() => formRef.current?.requestSubmit(), 50);
                  }}
                  onNeedBookForm={() => setFormOpen(true)}
                  onShowBookings={handleShowBookings}
                  onAfterShowBookings={() => setFormOpen(false)}
                />
              </div>
          </div>
          <div className="panel bookings-grid-panel">
            <div className="bookings-grid">
              <AgGridReact<Booking>
                theme={bookingsTheme}
                rowData={visibleBookings}
                columnDefs={columnDefs}
                defaultColDef={defaultColDef}
                getRowId={(p) => p.data.id}
                rowHeight={46}
                headerHeight={44}
                columnMenu="new"
                overlayNoRowsTemplate={
                  voiceList
                    ? t("No bookings for this doctor on that date.")
                    : "No bookings yet. Click Book appointment to create one."
                }
                pagination
                paginationPageSize={20}
                paginationPageSizeSelector={[10, 20, 50]}
                suppressCellFocus
              />
            </div>
          </div>
          {formOpen &&
            createPortal(
              <div
                className="booking-sheet-backdrop"
                role="presentation"
                onMouseDown={(e) => {
                  if (e.target === e.currentTarget) setFormOpen(false);
                }}
              >
                <div
                  className="booking-sheet panel"
                  role="dialog"
                  aria-modal="true"
                  aria-labelledby="booking-sheet-title"
                  onMouseDown={(e) => e.stopPropagation()}
                >
                  <header className="booking-sheet-head">
                    <div>
                      <p className="modal-eyebrow">{t("New booking")}</p>
                      <h2 id="booking-sheet-title">{t("Book appointment")}</h2>
                    </div>
                    <button
                      type="button"
                      className="modal-close"
                      aria-label={t("Close")}
                      onClick={() => setFormOpen(false)}
                    >
                      ×
                    </button>
                  </header>
                  {error ? <div className="msg error">{error}</div> : null}
                  {bookingForm}
                </div>
              </div>,
              document.body,
            )}
        </>
      )}
    </section>
  );
}
