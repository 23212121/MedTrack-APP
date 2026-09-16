import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";

const AUTO_HIDE_MS = 2800;

type ToastApi = {
  success: (message: string) => void;
};

const ToastContext = createContext<ToastApi | null>(null);

let toastBridge: ToastApi | null = null;

/** Call from any save/update success path. Toast auto-hides; no click needed. */
export const toast: ToastApi = {
  success(message: string) {
    const text = message.trim();
    if (!text) return;
    toastBridge?.success(text);
  },
};

export function useToast(): ToastApi {
  return useContext(ToastContext) ?? toast;
}

type ToastItem = { id: number; message: string };

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(0);
  const timers = useRef<Map<number, number>>(new Map());

  const dismiss = useCallback((id: number) => {
    const timer = timers.current.get(id);
    if (timer != null) {
      window.clearTimeout(timer);
      timers.current.delete(id);
    }
    setItems((prev) => prev.filter((item) => item.id !== id));
  }, []);

  const success = useCallback(
    (message: string) => {
      const id = ++nextId.current;
      setItems((prev) => [...prev.slice(-2), { id, message }]);
      const timer = window.setTimeout(() => dismiss(id), AUTO_HIDE_MS);
      timers.current.set(id, timer);
    },
    [dismiss],
  );

  useEffect(() => {
    toastBridge = { success };
    return () => {
      if (toastBridge?.success === success) toastBridge = null;
      timers.current.forEach((timer) => window.clearTimeout(timer));
      timers.current.clear();
    };
  }, [success]);

  return (
    <ToastContext.Provider value={{ success }}>
      {children}
      <div className="app-toast-host" aria-live="polite" aria-atomic="true">
        {items.map((item) => (
          <div key={item.id} className="app-toast app-toast--success" role="status">
            {item.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
