import { useEffect, useMemo, useState } from "react";
import { api, type PatientChatThread } from "../../api";
import { useCareChatUnread } from "../../careChatUnread";
import CareChatWindow from "../../components/CareChatWindow";
import ChatLink from "../../components/ChatLink";
import { getPatientPhone } from "../../auth";
import { session } from "../../dl/MedTrackSession";

export default function PatientChatPage() {
  const phone = getPatientPhone();
  const [rows, setRows] = useState<PatientChatThread[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [open, setOpen] = useState<PatientChatThread | null>(null);

  async function load() {
    if (!phone) return;
    setLoading(true);
    try {
      const data = await api.patientChatThreads(phone);
      setRows(data.threads || []);
      setError("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load chats");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [phone]);

  const preloaded = useMemo(() => {
    const map: Record<string, string[] | undefined> = {};
    for (const row of rows) map[row.appointmentId] = row.otherMessageAts;
    return map;
  }, [rows]);
  const fallbackCounts = useMemo(() => {
    const map: Record<string, number | undefined> = {};
    for (const row of rows) map[row.appointmentId] = row.unreadCount ?? row.chatCount;
    return map;
  }, [rows]);
  const unread = useCareChatUnread(
    rows.map((row) => row.appointmentId),
    "PATIENT",
    preloaded,
    fallbackCounts,
  );

  return (
    <section className="stack">
      <h1>Chat</h1>
      <p className="lead">Message the hospital or doctor about your appointment.</p>
      {error && <div className="msg error">{error}</div>}
      {loading ? (
        <p className="muted">Loading…</p>
      ) : rows.length === 0 ? (
        <p className="muted">No appointments to chat about yet.</p>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Token</th>
                <th>Patient</th>
                <th>Hospital</th>
                <th>Doctor</th>
                <th>Date</th>
                <th>Chat</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.appointmentId}>
                  <td>{row.tokenNumber ?? "—"}</td>
                  <td>{row.patientName}</td>
                  <td>{row.hospitalName || "Hospital"}</td>
                  <td>{row.doctorName || "Doctor"}</td>
                  <td>{row.appointmentDate || "—"}</td>
                  <td>
                    <ChatLink unread={unread[row.appointmentId] ?? 0} onClick={() => setOpen(row)} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {open && phone && (
        <CareChatWindow
          appointmentId={open.appointmentId}
          title={`${open.hospitalName || "Hospital"} · ${open.patientName}`}
          subtitle={`Token ${open.tokenNumber ?? "—"} · chat with hospital and doctor`}
          senderType="PATIENT"
          senderName={session.getPatientName() || open.patientName}
          phone={phone}
          onClose={() => setOpen(null)}
        />
      )}
    </section>
  );
}
