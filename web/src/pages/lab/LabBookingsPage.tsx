import { useEffect, useMemo, useState } from "react";
import LabBookingChat from "../../components/LabBookingChat";
import LabRouteMap from "../../components/LabRouteMap";
import { api, type LabBooking, type LabBookingNotification, type LabTechnician } from "../../api";
import { session } from "../../dl/MedTrackSession";
import {
  distanceKm,
  forwardGeocode,
  LAB_DISTANCE_RATE_PER_KM,
  parseGpsFromAddress,
} from "../../geocode";
import { toast } from "../../toast";

function fmt(iso?: string) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
  } catch {
    return iso;
  }
}

function toLocalInputValue(d = new Date()) {
  const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

export default function LabBookingsPage() {
  const [bookings, setBookings] = useState<LabBooking[]>([]);
  const [notifications, setNotifications] = useState<LabBookingNotification[]>([]);
  const [error, setError] = useState("");
  const [visitTimes, setVisitTimes] = useState<Record<string, string>>({});
  const [chatId, setChatId] = useState("");
  const [busyId, setBusyId] = useState("");
  const [lab, setLab] = useState<LabTechnician | null>(null);
  const [labPoint, setLabPoint] = useState<{ latitude: number; longitude: number } | null>(null);
  const [routeId, setRouteId] = useState("");

  async function load() {
    try {
      const [list, notifs] = await Promise.all([
        api.labBookings(),
        api.labBookingNotifications().catch(() => ({ notifications: [] as LabBookingNotification[] })),
      ]);
      setBookings(list.bookings || []);
      setNotifications(notifs.notifications || []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load lab bookings");
    }
  }

  useEffect(() => {
    void load();
    api
      .labMe()
      .then((res) => setLab(res.lab))
      .catch(() => setLab(null));
    const timer = window.setInterval(() => void load(), 8000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!lab) return;
    const query = [lab.address, lab.city, lab.state].filter(Boolean).join(", ");
    if (!query) {
      setLabPoint(null);
      return;
    }
    let cancelled = false;
    void forwardGeocode(query).then((point) => {
      if (!cancelled) setLabPoint(point);
    });
    return () => {
      cancelled = true;
    };
  }, [lab]);

  function patientPoint(b: LabBooking) {
    if (b.patientLatitude != null && b.patientLongitude != null) {
      return { latitude: Number(b.patientLatitude), longitude: Number(b.patientLongitude) };
    }
    return parseGpsFromAddress(b.collectionAddress);
  }

  function tripFor(b: LabBooking) {
    if (b.tripDistanceKm != null && b.tripCharge != null) {
      return { km: b.tripDistanceKm, charge: b.tripCharge };
    }
    const patient = patientPoint(b);
    if (!labPoint || !patient) return null;
    const km = distanceKm(labPoint, patient);
    return { km, charge: Math.round(km * LAB_DISTANCE_RATE_PER_KM * 100) / 100 };
  }

  const pending = useMemo(
    () => bookings.filter((b) => b.status === "PENDING"),
    [bookings],
  );
  const mine = useMemo(
    () => bookings.filter((b) => b.status !== "PENDING"),
    [bookings],
  );

  async function accept(b: LabBooking) {
    setError("");
    setBusyId(b.id);
    try {
      let visitTime: string | undefined;
      let distance: number | undefined;
      if (b.mode === "HOME_COLLECTION") {
        const local = visitTimes[b.id] || toLocalInputValue();
        visitTime = new Date(local).toISOString();
        distance = tripFor(b)?.km;
      }
      const res = await api.acceptLabBooking(b.id, {
        visitTime,
        distanceKm: distance,
      });
      toast.success(res.message || "Accepted");
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Accept failed");
    } finally {
      setBusyId("");
    }
  }

  async function reachedHome(id: string) {
    setBusyId(id);
    try {
      const res = await api.labBookingReachedHome(id);
      toast.success(res.message || "Marked reached");
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Update failed");
    } finally {
      setBusyId("");
    }
  }

  async function complete(id: string) {
    setBusyId(id);
    try {
      const res = await api.completeLabBooking(id);
      toast.success(res.message || "Completed");
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Complete failed");
    } finally {
      setBusyId("");
    }
  }

  const chatBooking = mine.find((b) => b.id === chatId) || pending.find((b) => b.id === chatId);
  const routeBooking = bookings.find((b) => b.id === routeId) || null;
  const routePatient = routeBooking ? patientPoint(routeBooking) : null;
  const routeTrip = routeBooking ? tripFor(routeBooking) : null;

  return (
    <section className="stack">
      <h1>Lab bookings</h1>
      <p className="lead">
        Accept patient lab requests. For home collection provide your arrival time. For in-lab visits
        the system assigns a token (10 minutes per patient ahead). Chat with the patient after accept.
      </p>
      {error ? <div className="msg error">{error}</div> : null}

      {notifications.length > 0 ? (
        <div className="panel stack">
          <h2>Notifications</h2>
          <ul>
            {notifications.slice(0, 8).map((n) => (
              <li key={n.id}>
                <strong>{n.title}</strong> — {n.message}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {routeBooking && labPoint && routePatient && routeTrip ? (
        <div className="panel stack">
          <h2>Route · {routeBooking.bookingNumber}</h2>
          <p style={{ whiteSpace: "pre-line", margin: 0 }}>{routeBooking.collectionAddress}</p>
          <LabRouteMap
            lab={labPoint}
            patient={routePatient}
            labLabel={lab?.labName || lab?.address || "Lab"}
            patientLabel={routeBooking.patientName || "Patient"}
            km={routeTrip.km}
          />
        </div>
      ) : null}

      {chatBooking?.canChat || (chatBooking && chatBooking.status !== "PENDING") ? (
        <LabBookingChat
          bookingId={chatBooking.id}
          title={`Chat · ${chatBooking.patientName || chatBooking.bookingNumber}`}
          senderName={session.getUsername() || "Lab"}
          onClose={() => setChatId("")}
        />
      ) : null}

      <div className="panel stack">
        <h2>Pending requests ({pending.length})</h2>
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th>Booking</th>
                <th>Patient</th>
                <th>Map address</th>
                <th>Distance / amount</th>
                <th>Mode</th>
                <th>Preferred</th>
                <th>Accept</th>
              </tr>
            </thead>
            <tbody>
              {pending.length === 0 ? (
                <tr>
                  <td colSpan={7}>No open requests.</td>
                </tr>
              ) : (
                pending.map((b) => (
                  <tr key={b.id}>
                    <td>{b.bookingNumber}</td>
                    <td>
                      <div>{b.patientName}</div>
                      <div className="muted">{b.patientPhone}</div>
                    </td>
                    <td style={{ whiteSpace: "pre-line", maxWidth: 280 }}>
                      {b.collectionAddress || "—"}
                    </td>
                    <td>
                      {(() => {
                        const trip = tripFor(b);
                        if (!trip) return "—";
                        return (
                          <>
                            <div>{trip.km} km</div>
                            <div>₹{trip.charge}</div>
                            <button
                              type="button"
                              className="secondary"
                              onClick={() => setRouteId(b.id)}
                            >
                              Show route
                            </button>
                          </>
                        );
                      })()}
                    </td>
                    <td>{b.modeLabel || b.mode}</td>
                    <td>{fmt(b.preferredAt)}</td>
                    <td>
                      {b.mode === "HOME_COLLECTION" ? (
                        <label>
                          Visit time
                          <input
                            type="datetime-local"
                            value={visitTimes[b.id] || toLocalInputValue()}
                            onChange={(e) =>
                              setVisitTimes((prev) => ({ ...prev, [b.id]: e.target.value }))
                            }
                          />
                        </label>
                      ) : (
                        <span className="muted">Token auto-assigned</span>
                      )}
                      <button
                        type="button"
                        disabled={busyId === b.id}
                        onClick={() => void accept(b)}
                      >
                        {busyId === b.id ? "…" : "Accept"}
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      <div className="panel stack">
        <h2>My bookings ({mine.length})</h2>
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th>Booking</th>
                <th>Patient</th>
                <th>Status</th>
                <th>Time / token</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {mine.length === 0 ? (
                <tr>
                  <td colSpan={5}>No accepted bookings yet.</td>
                </tr>
              ) : (
                mine.map((b) => (
                  <tr key={b.id}>
                    <td>{b.bookingNumber}</td>
                    <td>
                      <div>{b.patientName}</div>
                      <div className="muted">{b.patientPhone}</div>
                    </td>
                    <td>{b.status}</td>
                    <td>
                      <div>{fmt(b.visitTime || b.expectedVisitAt)}</div>
                      {b.tokenNumber != null ? <div>Token #{b.tokenNumber}</div> : null}
                    </td>
                    <td className="stack">
                      {b.status !== "CANCELED" && b.status !== "COMPLETED" ? (
                        <>
                          <button
                            type="button"
                            className="secondary"
                            onClick={() => setChatId(b.id)}
                          >
                            Chat
                          </button>
                          {b.mode === "HOME_COLLECTION" &&
                          (b.status === "VISIT_SCHEDULED" || b.status === "ACCEPTED") ? (
                            <button
                              type="button"
                              className="secondary"
                              disabled={busyId === b.id}
                              onClick={() => void reachedHome(b.id)}
                            >
                              Reached home
                            </button>
                          ) : null}
                          <button
                            type="button"
                            disabled={busyId === b.id}
                            onClick={() => void complete(b.id)}
                          >
                            Complete
                          </button>
                        </>
                      ) : null}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}
