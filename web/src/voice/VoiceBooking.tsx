import { useEffect, useRef, useState } from "react";
import { api } from "../api";
import { useI18n } from "../i18n";
import {
  applyParsedCommand,
  nextMissingField,
  patientToPatch,
  pickPatientMatch,
  promptFor,
  matchDoctor,
  type ConversationField,
  type VoiceDoctor,
  type VoiceDraft,
} from "./bookingConversation";
import { parseVoiceCommand } from "./voiceCommandParser";
import {
  isSpeechRecognitionSupported,
  speak,
  startListening,
  stopSpeaking,
  type SpeechRecognitionHandle,
} from "./speechRecognition";

export type ShowBookingsQuery = {
  doctorId: string;
  doctorName: string;
  from: string;
  to: string;
};

export type ShowBookingsResult = {
  count: number;
  patients: string[];
};

type VoiceBookingProps = {
  draft: VoiceDraft;
  doctors: VoiceDoctor[];
  publicMode?: boolean;
  deskMode?: boolean;
  loggedInDoctorId?: string;
  loggedInDoctorName?: string;
  onPatch: (patch: Partial<VoiceDraft>) => void;
  onSelectDoctor: (doctorId: string) => void;
  onSelectDate: (date: string) => Promise<{ ok: boolean; message?: string }>;
  onConfirmBook: () => void;
  onNeedBookForm?: () => void;
  onShowBookings?: (query: ShowBookingsQuery) => Promise<ShowBookingsResult>;
  onAfterShowBookings?: () => void;
};

export default function VoiceBooking({
  draft,
  doctors,
  publicMode = false,
  deskMode = false,
  loggedInDoctorId = "",
  loggedInDoctorName = "",
  onPatch,
  onSelectDoctor,
  onSelectDate,
  onConfirmBook,
  onNeedBookForm,
  onShowBookings,
  onAfterShowBookings,
}: VoiceBookingProps) {
  const { t, speechLocale } = useI18n();
  const supported = isSpeechRecognitionSupported();
  const [active, setActive] = useState(false);
  const [listening, setListening] = useState(false);
  const [heard, setHeard] = useState("");
  const [prompt, setPrompt] = useState(() =>
    t(
      deskMode
        ? "Say show booking to hear the patient list, or book appointment for a patient."
        : promptFor("idle"),
    ),
  );
  const recRef = useRef<SpeechRecognitionHandle | null>(null);
  const activeRef = useRef(false);
  const processingRef = useRef(false);
  const expectingRef = useRef<ConversationField>("patientName");
  const searchedNameRef = useRef("");
  const draftRef = useRef(draft);
  const doctorsRef = useRef(doctors);
  const publicModeRef = useRef(publicMode);
  const deskModeRef = useRef(deskMode);
  const loggedInDoctorIdRef = useRef(loggedInDoctorId);
  const loggedInDoctorNameRef = useRef(loggedInDoctorName);
  const showDoctorIdRef = useRef("");
  const showDoctorNameRef = useRef("");
  const showFromRef = useRef("");

  useEffect(() => {
    draftRef.current = draft;
  }, [draft]);
  useEffect(() => {
    doctorsRef.current = doctors;
  }, [doctors]);
  useEffect(() => {
    publicModeRef.current = publicMode;
  }, [publicMode]);
  useEffect(() => {
    deskModeRef.current = deskMode;
  }, [deskMode]);
  useEffect(() => {
    loggedInDoctorIdRef.current = loggedInDoctorId;
  }, [loggedInDoctorId]);
  useEffect(() => {
    loggedInDoctorNameRef.current = loggedInDoctorName;
  }, [loggedInDoctorName]);

  useEffect(() => {
    return () => {
      activeRef.current = false;
      recRef.current?.stop();
      stopSpeaking();
    };
  }, []);

  function setAssistant(text: string) {
    setPrompt(text);
  }

  function stopSession() {
    activeRef.current = false;
    setActive(false);
    setListening(false);
    recRef.current?.stop();
    recRef.current = null;
    stopSpeaking();
  }

  async function say(key: string, vars?: Record<string, string | number>) {
    const line = t(key, vars);
    setAssistant(line);
    await speak(line, speechLocale);
  }

  async function askAndListen(next: ConversationField) {
    expectingRef.current = next;
    await say(promptFor(next));
    if (activeRef.current) listenOnce();
  }

  function listenOnce() {
    if (!activeRef.current) return;
    recRef.current?.stop();
    setListening(true);
    recRef.current = startListening({
      lang: speechLocale,
      onInterim: (text) => setHeard(text),
      onResult: (text) => {
        setHeard(text);
        void handleUtterance(text);
      },
      onError: (message) => {
        setListening(false);
        if (!activeRef.current) return;
        setAssistant(message);
        window.setTimeout(() => {
          if (activeRef.current && !processingRef.current) listenOnce();
        }, 600);
      },
      onEnd: () => {
        setListening(false);
      },
    });
  }

  function resolveShowDoctor(query?: string): { id: string; name: string } | "ask" | "missing" {
    if (query?.trim()) {
      const found = matchDoctor(query, doctorsRef.current);
      if (found) return { id: found.doctorId, name: found.doctorName };
      return "missing";
    }
    if (showDoctorIdRef.current) {
      return { id: showDoctorIdRef.current, name: showDoctorNameRef.current || showDoctorIdRef.current };
    }
    if (loggedInDoctorIdRef.current) {
      const d = doctorsRef.current.find((x) => x.doctorId === loggedInDoctorIdRef.current);
      return {
        id: loggedInDoctorIdRef.current,
        name: d?.doctorName || loggedInDoctorNameRef.current || loggedInDoctorIdRef.current,
      };
    }
    const formDoctor = draftRef.current.doctorId;
    if (formDoctor) {
      const d = doctorsRef.current.find((x) => x.doctorId === formDoctor);
      return {
        id: formDoctor,
        name: d?.doctorName || draftRef.current.doctorName || formDoctor,
      };
    }
    return "ask";
  }

  async function completeShowBookings(
    doctorId: string,
    doctorName: string,
    from: string,
    to: string,
  ) {
    if (!onShowBookings) {
      await say("I can show bookings on the staff bookings list.");
      await askAndListen(nextMissingField(draftRef.current, publicModeRef.current));
      return;
    }
    await say("Loading the patient list.");
    try {
      const result = await onShowBookings({ doctorId, doctorName, from, to });
      const extra = result.count > 8 ? ` ${t("and more.")}` : "";
      const names = (result.patients || []).slice(0, 8).join(", ");
      if (result.count === 0) {
        await say("No bookings for {name} on that date.", { name: doctorName || doctorId });
      } else if (from === to) {
        await say("{count} patients on {date}. {names}", {
          count: result.count,
          date: from,
          names: `${names}${extra}`,
        });
      } else {
        await say("{count} patients from {from} to {to}. {names}", {
          count: result.count,
          from,
          to,
          names: `${names}${extra}`,
        });
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : t("Failed to load bookings.");
      setAssistant(message);
      await speak(message, speechLocale);
    }
    onAfterShowBookings?.();
    stopSession();
  }

  async function handleShowBookings(parsed: {
    doctorQuery?: string;
    dateFrom?: string;
    dateTo?: string;
    openEndedRange?: boolean;
  }) {
    const doctor = resolveShowDoctor(parsed.doctorQuery);
    if (doctor === "missing") {
      await say("I could not find doctor {name}. Please say another doctor name.", {
        name: parsed.doctorQuery || "",
      });
      expectingRef.current = "showDoctor";
      if (activeRef.current) listenOnce();
      return;
    }
    if (doctor === "ask") {
      await askAndListen("showDoctor");
      return;
    }
    showDoctorIdRef.current = doctor.id;
    showDoctorNameRef.current = doctor.name;

    if (expectingRef.current === "showDateTo") {
      const toDate = parsed.dateTo;
      if (!toDate) {
        await askAndListen("showDateTo");
        return;
      }
      const fromDate = showFromRef.current || parsed.dateFrom;
      if (!fromDate) {
        await askAndListen("showDate");
        return;
      }
      showFromRef.current = "";
      const from = fromDate <= toDate ? fromDate : toDate;
      const to = fromDate <= toDate ? toDate : fromDate;
      await completeShowBookings(doctor.id, doctor.name, from, to);
      return;
    }

    let from = parsed.dateFrom || showFromRef.current;
    let to = parsed.dateTo;
    if (parsed.openEndedRange && from && !to) {
      showFromRef.current = from;
      await askAndListen("showDateTo");
      return;
    }
    if (from && !to) to = from;
    if (!from) {
      await askAndListen("showDate");
      return;
    }
    if (to && from > to) {
      const swap = from;
      from = to;
      to = swap;
    }
    showFromRef.current = "";
    await completeShowBookings(doctor.id, doctor.name, from, to || from);
  }

  async function handleUtterance(text: string) {
    if (processingRef.current || !activeRef.current) return;
    processingRef.current = true;
    recRef.current?.stop();
    setListening(false);

    try {
      const parsed = parseVoiceCommand(text, expectingRef.current);
      if (parsed.intent === "STOP") {
        setAssistant(t("Voice booking stopped."));
        stopSession();
        return;
      }

      if (parsed.intent === "SHOW_BOOKINGS") {
        await handleShowBookings(parsed);
        return;
      }

      if (parsed.intent === "CONFIRM_BOOK") {
        const missing = nextMissingField(draftRef.current, publicModeRef.current);
        if (missing === "confirm") {
          await say("Booking the appointment now.");
          stopSession();
          onConfirmBook();
          return;
        }
        onNeedBookForm?.();
        await askAndListen(missing);
        return;
      }

      const hasContent = Boolean(
        parsed.patientName ||
          parsed.phone ||
          parsed.age ||
          parsed.gender ||
          parsed.email ||
          parsed.doctorQuery ||
          parsed.date ||
          parsed.time ||
          parsed.address ||
          parsed.reason,
      );
      if (!hasContent) {
        if (deskModeRef.current && expectingRef.current === "idle") {
          await say(
            "Say show booking to hear the patient list, or book appointment for a patient.",
          );
          if (activeRef.current) listenOnce();
          return;
        }
        await askAndListen(
          expectingRef.current === "idle"
            ? nextMissingField(draftRef.current, publicModeRef.current)
            : expectingRef.current,
        );
        return;
      }

      onNeedBookForm?.();

      const applied = applyParsedCommand(parsed, doctorsRef.current);
      if (Object.keys(applied.patch).length) onPatch(applied.patch);
      if (applied.doctorId) onSelectDoctor(applied.doctorId);
      if (applied.patch.appointmentDate) {
        const result = await onSelectDate(applied.patch.appointmentDate);
        if (!result.ok) {
          expectingRef.current = "date";
          const line = t(
            result.message ||
              "That date is not available. Please say another appointment date.",
          );
          setAssistant(line);
          await speak(line, speechLocale);
          if (activeRef.current) listenOnce();
          return;
        }
      }

      if (applied.message) {
        setAssistant(t(applied.message, { name: parsed.doctorQuery || "" }));
        await speak(t(applied.message, { name: parsed.doctorQuery || "" }), speechLocale);
        if (activeRef.current) listenOnce();
        return;
      }

      const merged: VoiceDraft = { ...draftRef.current, ...applied.patch };
      draftRef.current = merged;

      const name = merged.patientName.trim();
      if (
        name.length >= 2 &&
        searchedNameRef.current.toLowerCase() !== name.toLowerCase()
      ) {
        searchedNameRef.current = name;
        try {
          const res = await api.searchPatients(name);
          const hit = pickPatientMatch(name, res.patients || []);
          if (hit) {
            const fromPatient = patientToPatch(hit, merged);
            if (Object.keys(fromPatient).length) onPatch(fromPatient);
            draftRef.current = { ...merged, ...fromPatient };
            const foundLine = t("{name} was found in patient records.", {
              name: hit.name || name,
            });
            setAssistant(foundLine);
            await speak(foundLine, speechLocale);
          } else {
            const newLine = t("{name} is not in the system yet.", { name });
            setAssistant(newLine);
            await speak(newLine, speechLocale);
          }
        } catch {
          /* search is optional — continue collecting fields */
        }
      }

      const next = nextMissingField(draftRef.current, publicModeRef.current);
      await askAndListen(next);
    } finally {
      processingRef.current = false;
    }
  }

  async function startSession() {
    if (!supported) return;
    stopSpeaking();
    searchedNameRef.current = "";
    showDoctorIdRef.current = "";
    showDoctorNameRef.current = "";
    showFromRef.current = "";
    activeRef.current = true;
    setActive(true);
    setHeard("");
    if (deskMode) {
      expectingRef.current = "idle";
      await say(
        "Say show booking to hear the patient list, or book appointment for a patient.",
      );
      if (activeRef.current) listenOnce();
      return;
    }
    const first = nextMissingField(draft, publicMode);
    await askAndListen(first);
  }

  function toggle() {
    if (active) {
      setAssistant(t("Voice booking stopped."));
      stopSession();
      return;
    }
    void startSession();
  }

  return (
    <div className="voice-booking">
      <div className="voice-booking-row">
        <button
          type="button"
          className={active ? "voice-booking-btn is-active" : "voice-booking-btn"}
          onClick={toggle}
          disabled={!supported}
        >
          {active
            ? listening
              ? t("Listening… tap to stop")
              : t("Stop voice booking")
            : t("Speak to Book")}
        </button>
        {!supported && (
          <span className="voice-booking-note">
            {t("Voice booking needs Chrome or Edge with microphone access.")}
          </span>
        )}
      </div>
      {(active || heard) && (
        <div className="voice-booking-status" aria-live="polite">
          {heard ? (
            <p>
              <strong>{t("Heard")}:</strong> {heard}
            </p>
          ) : null}
          <p>
            <strong>{t("Assistant")}:</strong> {prompt}
          </p>
        </div>
      )}
    </div>
  );
}
