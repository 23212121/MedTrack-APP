import { FormEvent, useEffect, useRef, useState } from "react";
import { api, type CareChatMessage, type CareChatThread } from "../api";
import { markCareChatRead } from "../careChatUnread";
import { FileIconInput } from "./NativeFileInput";

type Props = {
  appointmentId: string;
  title: string;
  subtitle?: string;
  senderType: "HOSPITAL" | "DOCTOR" | "PATIENT";
  senderName: string;
  phone?: string;
  onClose: () => void;
};

const ALLOWED = ".pdf,.png,.jpg,.jpeg,.docx";

function initials(name?: string) {
  const parts = (name || "?").trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

function formatTime(iso?: string) {
  if (!iso) return "";
  try {
    return new Date(iso).toLocaleTimeString(undefined, {
      hour: "numeric",
      minute: "2-digit",
    });
  } catch {
    return "";
  }
}

function isImageName(name?: string) {
  return /\.(png|jpe?g)$/i.test(name || "");
}

function uniqueMessages(messages: CareChatMessage[] | undefined): CareChatMessage[] {
  const seen = new Set<string>();
  const out: CareChatMessage[] = [];
  for (const msg of messages || []) {
    const key = msg.id || `${msg.createdAt || ""}|${msg.senderType}|${msg.message}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(msg);
  }
  return out;
}

function applyThread(data: CareChatThread): CareChatThread {
  return { ...data, messages: uniqueMessages(data.messages) };
}

function showMessageText(msg: CareChatMessage, hasFile: boolean) {
  if (!msg.message) return false;
  // Attachment captions that merely repeat the filename are hidden; the file link is enough.
  if (hasFile && msg.message === msg.documentName) return false;
  return true;
}

export default function CareChatWindow({
  appointmentId,
  title,
  subtitle,
  senderType,
  senderName,
  phone,
  onClose,
}: Props) {
  const [thread, setThread] = useState<CareChatThread | null>(null);
  const [text, setText] = useState("");
  const [error, setError] = useState("");
  const [sending, setSending] = useState(false);
  const [cameraOpen, setCameraOpen] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const sendingRef = useRef(false);

  function setThreadSafe(data: CareChatThread) {
    setThread(applyThread(data));
  }

  async function load() {
    try {
      const data = await api.careChatThread(appointmentId, phone);
      setThreadSafe(data);
      setError("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load chat");
    }
  }

  useEffect(() => {
    markCareChatRead(appointmentId, senderType);
    void load();
    const timer = window.setInterval(() => void load(), 2500);
    return () => {
      markCareChatRead(appointmentId, senderType);
      window.clearInterval(timer);
    };
  }, [appointmentId, phone, senderType]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [thread?.messages.length]);

  useEffect(() => () => stopCamera(), []);

  function stopCamera() {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    setCameraOpen(false);
  }

  async function openCamera() {
    setError("");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "environment" },
        audio: false,
      });
      streamRef.current = stream;
      setCameraOpen(true);
      requestAnimationFrame(() => {
        if (videoRef.current) videoRef.current.srcObject = stream;
      });
    } catch {
      setError("Camera not available. Use the attach button to send a photo.");
    }
  }

  async function capturePhoto() {
    const video = videoRef.current;
    if (!video) return;
    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth || 1280;
    canvas.height = video.videoHeight || 720;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.9));
    stopCamera();
    if (!blob) {
      setError("Could not capture photo");
      return;
    }
    const file = new File([blob], `chat-photo-${Date.now()}.jpg`, { type: "image/jpeg" });
    await sendFile(file);
  }

  async function onSend(e: FormEvent) {
    e.preventDefault();
    if (!text.trim() || sendingRef.current) return;
    sendingRef.current = true;
    setSending(true);
    try {
      const data = await api.sendCareChat(
        appointmentId,
        {
          message: text.trim(),
          senderType,
          senderName,
        },
        phone,
      );
      setThreadSafe(data);
      setText("");
      setError("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Send failed");
    } finally {
      sendingRef.current = false;
      setSending(false);
    }
  }

  async function sendFile(file: File) {
    if (sendingRef.current) return;
    sendingRef.current = true;
    setSending(true);
    try {
      const data = await api.attachCareChat(
        appointmentId,
        file,
        {
          senderType,
          senderName,
          message: text.trim() || undefined,
        },
        phone,
      );
      setThreadSafe(data);
      setText("");
      setError("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Attachment failed");
    } finally {
      sendingRef.current = false;
      setSending(false);
    }
  }

  function onPickFile(files: File[]) {
    const file = files[0];
    if (file) void sendFile(file);
  }

  function isMine(msg: CareChatMessage) {
    return msg.senderType === senderType;
  }

  function fileHref(msg: CareChatMessage) {
    if (!msg.documentUrl) return "";
    return api.careChatFileUrl(msg.documentUrl, phone);
  }

  return (
    <div className="wa-backdrop" onClick={onClose} role="presentation">
      <div className="wa-window" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <header className="wa-header">
          <div className="wa-header-left">
            <div className="wa-avatar">{initials(thread?.patientName || title)}</div>
            <div>
              <h2>{title}</h2>
              <p>{subtitle || "Hospital, doctor, and patient"}</p>
            </div>
          </div>
          <button type="button" className="wa-close" onClick={onClose} aria-label="Close">
            ×
          </button>
        </header>
        {error && <div className="msg error wa-error">{error}</div>}
        <div className="wa-thread">
          {(thread?.messages || []).length === 0 ? (
            <p className="wa-empty">No messages yet. Start the conversation.</p>
          ) : (
            uniqueMessages(thread?.messages).map((msg) => {
              const href = fileHref(msg);
              const image = Boolean(href && isImageName(msg.documentName));
              return (
                <div key={msg.id} className={isMine(msg) ? "wa-row wa-row--out" : "wa-row wa-row--in"}>
                  <div className={isMine(msg) ? "wa-bubble wa-bubble--out" : "wa-bubble wa-bubble--in"}>
                    <strong className="wa-sender">{msg.senderName || msg.senderType}</strong>
                    {image && (
                      <a className="wa-attach-open" href={href} target="_blank" rel="noreferrer">
                        <img className="wa-preview" src={href} alt={msg.documentName || "Image"} />
                      </a>
                    )}
                    {href && !image && (
                      <a className="wa-attach-link" href={href} target="_blank" rel="noreferrer">
                        {msg.documentName || "Download file"}
                      </a>
                    )}
                    {showMessageText(msg, Boolean(href)) ? <p>{msg.message}</p> : null}
                    <time>{formatTime(msg.createdAt)}</time>
                  </div>
                </div>
              );
            })
          )}
          <div ref={bottomRef} />
        </div>
        {cameraOpen && (
          <div className="wa-camera">
            <video ref={videoRef} autoPlay playsInline muted />
            <div className="wa-camera-actions">
              <button type="button" className="wa-camera-cancel" onClick={stopCamera}>
                Cancel
              </button>
              <button type="button" onClick={() => void capturePhoto()}>
                Capture
              </button>
            </div>
          </div>
        )}
        <form className="wa-compose" onSubmit={(e) => void onSend(e)}>
          <FileIconInput
            icon="📎"
            ariaLabel="Attach file"
            accept={ALLOWED}
            disabled={sending}
            onPick={onPickFile}
          />
          <button
            type="button"
            className="wa-icon"
            aria-label="Take photo"
            disabled={sending}
            onClick={() => void openCamera()}
          >
            📷
          </button>
          <input
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Type a message"
            maxLength={2000}
          />
          <button type="submit" disabled={sending || !text.trim()} aria-label="Send">
            {sending ? "…" : "➤"}
          </button>
        </form>
      </div>
    </div>
  );
}
