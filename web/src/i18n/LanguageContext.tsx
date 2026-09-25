import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { hi } from "./hi";

export type Lang = "en" | "hi";

const STORAGE_KEY = "medtrack.lang";

type I18nValue = {
  lang: Lang;
  setLang: (lang: Lang) => void;
  t: (key: string, vars?: Record<string, string | number>) => string;
  speechLocale: string;
};

const I18nContext = createContext<I18nValue | null>(null);

function readStoredLang(): Lang {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    if (value === "hi" || value === "en") return value;
  } catch {
    /* ignore */
  }
  return "en";
}

function applyDocumentLang(lang: Lang) {
  if (typeof document === "undefined") return;
  document.documentElement.lang = lang === "hi" ? "hi" : "en";
}

export function interpolate(
  template: string,
  vars?: Record<string, string | number>,
) {
  if (!vars) return template;
  return template.replace(/\{(\w+)\}/g, (_, name: string) =>
    vars[name] != null ? String(vars[name]) : `{${name}}`,
  );
}

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(readStoredLang);

  useEffect(() => {
    applyDocumentLang(lang);
  }, [lang]);

  const setLang = useCallback((next: Lang) => {
    setLangState(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      /* ignore */
    }
    applyDocumentLang(next);
  }, []);

  const t = useCallback(
    (key: string, vars?: Record<string, string | number>) => {
      const raw = lang === "hi" ? hi[key] ?? key : key;
      return interpolate(raw, vars);
    },
    [lang],
  );

  const value = useMemo<I18nValue>(
    () => ({
      lang,
      setLang,
      t,
      speechLocale: lang === "hi" ? "hi-IN" : "en-IN",
    }),
    [lang, setLang, t],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  const ctx = useContext(I18nContext);
  if (!ctx) {
    return {
      lang: "en" as Lang,
      setLang: (_lang: Lang) => {},
      t: (key: string, vars?: Record<string, string | number>) =>
        interpolate(key, vars),
      speechLocale: "en-IN",
    };
  }
  return ctx;
}

export function useT() {
  return useI18n().t;
}
