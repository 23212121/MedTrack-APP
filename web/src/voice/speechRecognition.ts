export type SpeechRecognitionHandle = {
  stop: () => void;
};

type ListenOptions = {
  lang?: string;
  onInterim?: (text: string) => void;
  onResult: (text: string) => void;
  onError?: (message: string) => void;
  onEnd?: () => void;
};

type BrowserSpeechRecognition = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  start: () => void;
  stop: () => void;
  abort: () => void;
  onresult: ((event: {
    resultIndex: number;
    results: ArrayLike<{
      isFinal: boolean;
      0: { transcript: string };
    }>;
  }) => void) | null;
  onerror: ((event: { error?: string }) => void) | null;
  onend: (() => void) | null;
};

function RecognitionCtor(): (new () => BrowserSpeechRecognition) | null {
  const w = window as unknown as {
    SpeechRecognition?: new () => BrowserSpeechRecognition;
    webkitSpeechRecognition?: new () => BrowserSpeechRecognition;
  };
  return w.SpeechRecognition || w.webkitSpeechRecognition || null;
}

export function isSpeechRecognitionSupported() {
  return typeof window !== "undefined" && !!RecognitionCtor();
}

export function startListening(options: ListenOptions): SpeechRecognitionHandle {
  const Ctor = RecognitionCtor();
  if (!Ctor) {
    options.onError?.("Speech recognition is not supported in this browser. Use Chrome or Edge.");
    return { stop() {} };
  }

  const rec = new Ctor();
  rec.lang = options.lang || "en-IN";
  rec.continuous = false;
  rec.interimResults = true;
  rec.maxAlternatives = 1;

  rec.onresult = (event) => {
    let interim = "";
    let finalText = "";
    for (let i = event.resultIndex; i < event.results.length; i += 1) {
      const piece = event.results[i][0]?.transcript || "";
      if (event.results[i].isFinal) finalText += piece;
      else interim += piece;
    }
    const heard = (finalText || interim).trim();
    if (heard) options.onInterim?.(heard);
    if (finalText.trim()) options.onResult(finalText.trim());
  };

  rec.onerror = (event) => {
    const err = event.error || "speech-error";
    if (err === "no-speech") {
      options.onError?.("No speech heard. Try again.");
      return;
    }
    if (err === "aborted") return;
    if (err === "not-allowed") {
      options.onError?.(
        "Microphone permission was blocked. Allow the mic for this site, then try again.",
      );
      return;
    }
    options.onError?.(err);
  };

  rec.onend = () => options.onEnd?.();

  try {
    rec.start();
  } catch {
    options.onError?.("Could not start the microphone.");
  }

  return {
    stop() {
      try {
        rec.abort();
      } catch {
        /* ignore */
      }
    },
  };
}

export function speak(text: string, lang = "en-IN"): Promise<void> {
  return new Promise((resolve) => {
    if (typeof window === "undefined" || !window.speechSynthesis) {
      resolve();
      return;
    }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = lang;
    utterance.rate = lang.startsWith("hi") ? 0.95 : 1;
    utterance.onend = () => resolve();
    utterance.onerror = () => resolve();
    window.speechSynthesis.speak(utterance);
  });
}

export function stopSpeaking() {
  if (typeof window !== "undefined") window.speechSynthesis?.cancel();
}
