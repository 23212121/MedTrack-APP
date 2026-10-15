import { FormEvent, useEffect, useRef, useState } from "react";
import { api, type LabBookingChatMessage } from "../api";

type Props = {
  bookingId: string;
  title?: string;
  senderName: string;
  onClose?: () => void;
};

export default function LabBookingChat({ bookingId, title, senderName, onClose }: Props) {
  const [messages, setMessages] = useState<LabBookingChatMessage[]>([]);
  const [text, setText] = useState("");
  const [error, setError] = useState("");
  const [sending, setSending] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  async function load() {
    try {
      const res = await api.labBookingChats(bookingId);
      setMessages(res.messages || []);
      setError("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load chat");
    }
  }

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 3000);
    return () => window.clearInterval(timer);
  }, [bookingId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages.length]);

  async function onSend(e: FormEvent) {
    e.preventDefault();
    if (!text.trim() || sending) return;
    setSending(true);
    setError("");
    try {
      await api.postLabBookingChat(bookingId, text.trim(), senderName);
      setText("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Send failed");
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="panel stack lab-booking-chat">
      <div className="row" style={{ justifyContent: "space-between", alignItems: "center" }}>
        <h3 style={{ margin: 0 }}>{title || "Chat"}</h3>
        {onClose ? (
          <button type="button" className="secondary" onClick={onClose}>
            Close
          </button>
        ) : null}
      </div>
      {error ? <div className="msg error">{error}</div> : null}
      <div className="lab-chat-messages" style={{ maxHeight: 260, overflowY: "auto" }}>
        {messages.length === 0 ? (
          <p className="muted">No messages yet. Say hello to coordinate the sample collection.</p>
        ) : (
          messages.map((m) => (
            <div key={m.id} className="lab-chat-bubble" style={{ marginBottom: "0.6rem" }}>
              <strong>
                {m.senderName || m.senderType}
                {m.createdAt
                  ? ` · ${new Date(m.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`
                  : ""}
              </strong>
              <div>{m.message}</div>
            </div>
          ))
        )}
        <div ref={bottomRef} />
      </div>
      <form className="row" onSubmit={(e) => void onSend(e)}>
        <label style={{ flex: 1 }}>
          Message
          <input
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Type a message"
            autoComplete="off"
          />
        </label>
        <button type="submit" disabled={sending || !text.trim()}>
          {sending ? "Sending…" : "Send"}
        </button>
      </form>
    </div>
  );
}
