import { useEffect, useRef, useState } from "react";
import { INDIA_STATE_CITIES, INDIA_STATES } from "../data/indiaLocations";
import {
  isSpeechRecognitionSupported,
  speak,
  startListening,
  stopSpeaking,
  type SpeechRecognitionHandle,
} from "./speechRecognition";

export type EmergencyVoiceDraft = {
  state: string;
  city: string;
  hospitalName: string;
  patientName: string;
  patientPhone: string;
};

type HospitalOption = { hospitalId: number; hospitalName: string; city?: string; state?: string };

type Props = {
  draft: EmergencyVoiceDraft;
  hospitals: HospitalOption[];
  onPatch: (patch: Partial<EmergencyVoiceDraft>) => void;
  onSelectHospital: (hospitalId: number) => void;
  onBookHeard?: (bedNumber: string) => void;
};

function matchState(text: string): string {
  const lower = text.toLowerCase();
  return INDIA_STATES.find((s) => lower.includes(s.toLowerCase())) || "";
}

function matchCity(text: string, state?: string): string {
  const lower = text.toLowerCase();
  const cities = state && INDIA_STATE_CITIES[state]
    ? INDIA_STATE_CITIES[state]
    : Object.values(INDIA_STATE_CITIES).flat();
  return cities.find((c) => lower.includes(c.toLowerCase())) || "";
}

function matchHospital(text: string, hospitals: HospitalOption[]): HospitalOption | null {
  const lower = text.toLowerCase().replace(/\bhospital\b/g, " ").replace(/\s+/g, " ").trim();
  if (!lower) return null;
  return (
    hospitals.find((h) => h.hospitalName.toLowerCase() === lower) ||
    hospitals.find((h) => lower.includes(h.hospitalName.toLowerCase())) ||
    hospitals.find((h) => h.hospitalName.toLowerCase().includes(lower)) ||
    null
  );
}

function extractPhone(text: string): string {
  const digits = text.replace(/\D/g, "");
  if (digits.length >= 10) return digits.slice(-10);
  return "";
}

function extractBed(text: string): string {
  const match = text.toUpperCase().match(/\b(ER|ICU)[-\s]?(\d{1,3})\b/);
  if (match) return `${match[1]}-${match[2].padStart(2, "0")}`;
  const num = text.toUpperCase().match(/\bBED\s*(?:NUMBER\s*)?(\d{1,3})\b/);
  if (num) return `ER-${num[1].padStart(2, "0")}`;
  return "";
}

function extractName(text: string): string {
  const cleaned = text
    .replace(/patient( name)?/i, " ")
    .replace(/my name is/i, " ")
    .replace(/name is/i, " ")
    .replace(/book( a)? bed.*/i, " ")
    .trim();
  return cleaned.replace(/\s+/g, " ");
}

export default function VoiceEmergency({
  draft,
  hospitals,
  onPatch,
  onSelectHospital,
  onBookHeard,
}: Props) {
  const supported = isSpeechRecognitionSupported();
  const [active, setActive] = useState(false);
  const [listening, setListening] = useState(false);
  const [heard, setHeard] = useState("");
  const [prompt, setPrompt] = useState("Say state, city, hospital, patient name, or book bed ER-01.");
  const recRef = useRef<SpeechRecognitionHandle | null>(null);
  const draftRef = useRef(draft);
  const hospitalsRef = useRef(hospitals);

  useEffect(() => {
    draftRef.current = draft;
  }, [draft]);
  useEffect(() => {
    hospitalsRef.current = hospitals;
  }, [hospitals]);

  useEffect(() => {
    return () => {
      recRef.current?.stop();
      stopSpeaking();
    };
  }, []);

  function listen() {
    recRef.current?.stop();
    setListening(true);
    recRef.current = startListening({
      lang: "en-IN",
      onInterim: setHeard,
      onResult: (text) => {
        setHeard(text);
        void apply(text);
      },
      onError: (message) => {
        setListening(false);
        setPrompt(message);
      },
      onEnd: () => setListening(false),
    });
  }

  async function apply(raw: string) {
    const text = raw.trim();
    if (!text) return;
    const current = draftRef.current;
    const patch: Partial<EmergencyVoiceDraft> = {};

    const state = matchState(text);
    if (state) patch.state = state;
    const city = matchCity(text, patch.state || current.state);
    if (city) patch.city = city;

    const hospital = matchHospital(text, hospitalsRef.current);
    if (hospital) {
      patch.hospitalName = hospital.hospitalName;
      onSelectHospital(hospital.hospitalId);
    }

    const phone = extractPhone(text);
    if (phone) patch.patientPhone = phone;

    const bed = extractBed(text);
    const wantsBook = /\bbook\b|\breserve\b/.test(text.toLowerCase());

    if (/\bpatient\b|\bname\b/.test(text.toLowerCase()) && !phone && !bed) {
      const name = extractName(text);
      if (name.length >= 2) patch.patientName = name;
    } else if (!state && !city && !hospital && !phone && !bed && text.split(" ").length <= 4) {
      patch.patientName = extractName(text);
    }

    if (Object.keys(patch).length) onPatch(patch);

    if (bed && wantsBook) {
      onBookHeard?.(bed);
      setPrompt(`Booking bed ${bed}.`);
      await speak(`Booking bed ${bed}`);
      return;
    }

    const next = { ...current, ...patch };
    let ask = "Say the missing details, or book a bed number.";
    if (!next.state) ask = "Please say the state.";
    else if (!next.city) ask = "Please say the city.";
    else if (!next.hospitalName) ask = "Please say the hospital name.";
    else if (!next.patientName) ask = "Please say the patient name.";
    else if (!next.patientPhone) ask = "Please say the patient phone number.";
    else ask = "Say book bed followed by the bed number, for example book bed ER-01.";
    setPrompt(ask);
    await speak(ask);
    if (active) listen();
  }

  if (!supported) {
    return <p className="muted">Voice fill needs Chrome or Edge with microphone access.</p>;
  }

  return (
    <div className="voice-emergency">
      <button
        type="button"
        className={active ? "" : "secondary"}
        onClick={() => {
          if (active) {
            setActive(false);
            recRef.current?.stop();
            stopSpeaking();
            setListening(false);
            return;
          }
          setActive(true);
          const start = "Please say the state, city, hospital, and patient details.";
          setPrompt(start);
          void speak(start).then(() => listen());
        }}
      >
        {active ? (listening ? "Listening…" : "Voice on") : "Fill by voice"}
      </button>
      {active ? (
        <p className="muted voice-emergency-prompt">
          {prompt}
          {heard ? ` Heard: “${heard}”` : ""}
        </p>
      ) : null}
    </div>
  );
}
