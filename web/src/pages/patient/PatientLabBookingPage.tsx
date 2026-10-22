import { FormEvent, useEffect, useMemo, useState } from "react";
import AddressMapPickerModal from "../../components/AddressMapPickerModal";
import LabBookingChat from "../../components/LabBookingChat";
import { api, type LabBooking, type HospitalRegistrationSummary } from "../../api";
import { buildCollectionAddress } from "../../geocode";
import { session } from "../../dl/MedTrackSession";
import { toast } from "../../toast";

function fmt(iso?: string) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString([], {
      dateStyle: "medium",
      timeStyle: "short",
    });
  } catch {
    return iso;
  }
}

function toLocalInputValue(d = new Date()) {
  const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

export default function PatientLabBookingPage() {
  const [hospitals, setHospitals] = useState<HospitalRegistrationSummary[]>([]);
  const [hospitalId, setHospitalId] = useState(session.getHospitalId() || "");
  const [mode, setMode] = useState<"HOME_COLLECTION" | "IN_LAB">("HOME_COLLECTION");
  const [streetLine, setStreetLine] = useState("");
  const [city, setCity] = useState("");
  const [stateName, setStateName] = useState("");
  const [pinCode, setPinCode] = useState("");
  const [country, setCountry] = useState("India");
  const [latitude, setLatitude] = useState("");
  const [longitude, setLongitude] = useState("");
  const [mapOpen, setMapOpen] = useState(false);
  const [preferredAt, setPreferredAt] = useState(toLocalInputValue());
  const [notes, setNotes] = useState("");
  const [bookings, setBookings] = useState<LabBooking[]>([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [chatId, setChatId] = useState("");
  const [cancelKm, setCancelKm] = useState<Record<string, string>>({});

  async function load() {
    try {
      const res = await api.labBookings();
      setBookings(res.bookings || []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load bookings");
    }
  }

  useEffect(() => {
    void load();
    api
      .hospitals()
      .then((res) => {
        const rows = res.hospitals || [];
        setHospitals(rows);
        if (!hospitalId && rows[0]) {
          setHospitalId(String(rows[0].id ?? rows[0].hospitalId ?? ""));
        }
      })
      .catch(() => setHospitals([]));
    const timer = window.setInterval(() => void load(), 8000);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const patientName = session.getPatientName() || session.getUsername() || "";
  const patientPhone = session.getPatientPhone() || "";

  const collectionAddress = useMemo(
    () =>
      buildCollectionAddress({
        streetLine,
        city,
        state: stateName,
        pinCode,
        country,
        latitude,
        longitude,
      }),
    [streetLine, city, stateName, pinCode, country, latitude, longitude],
  );

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (!hospitalId) {
      setError("Select a hospital.");
      return;
    }
    if (mode === "HOME_COLLECTION" && !collectionAddress.trim()) {
      setError("Home address is required for sample collection. Pick a location on the map.");
      return;
    }
    setSaving(true);
    try {
      const res = await api.createLabBooking({
        hospitalId,
        mode,
        patientName,
        patientPhone,
        patientId: session.getPatientId() || undefined,
        collectionAddress: mode === "HOME_COLLECTION" ? collectionAddress.trim() : undefined,
        preferredAt: preferredAt ? new Date(preferredAt).toISOString() : undefined,
        notes: notes.trim() || undefined,
      });
      toast.success(res.message || "Lab booking created");
      setNotes("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create booking");
    } finally {
      setSaving(false);
    }
  }

  async function onCancel(b: LabBooking) {
    setError("");
    const reached = Boolean(b.techReachedAt);
    let distanceKm: number | undefined;
    if (reached) {
      const raw = cancelKm[b.id] ?? "";
      distanceKm = Number(raw);
      if (!Number.isFinite(distanceKm) || distanceKm < 0) {
        setError("Enter distance (km) traveled by the technician to calculate the cancel charge.");
        return;
      }
    }
    try {
      const res = await api.cancelLabBooking(b.id, {
        reason: "Canceled by patient",
        distanceKm,
      });
      const charge = res.booking?.cancelCharge ?? 0;
      toast.success(
        charge > 0
          ? `Canceled. Distance charge ₹${charge}`
          : res.message || "Canceled with no charge",
      );
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Cancel failed");
    }
  }

  const chatBooking = useMemo(
    () => bookings.find((b) => b.id === chatId) || null,
    [bookings, chatId],
  );

  return (
    <section className="stack">
      <h1>Lab test booking</h1>
      {error ? <div className="msg error">{error}</div> : null}

      <form className="panel stack" onSubmit={(e) => void onCreate(e)}>
        <h2>New booking</h2>
        <label>
          Hospital
          <select
            value={hospitalId}
            onChange={(e) => setHospitalId(e.target.value)}
            required
          >
            <option value="">Select hospital</option>
            {hospitals.map((h) => {
              const id = String(h.id ?? h.hospitalId ?? "");
              return (
                <option key={id} value={id}>
                  {h.hospitalName || `Hospital #${id}`}
                </option>
              );
            })}
          </select>
        </label>
        <label>
          Test mode
          <select
            value={mode}
            onChange={(e) => setMode(e.target.value as "HOME_COLLECTION" | "IN_LAB")}
          >
            <option value="HOME_COLLECTION">Home sample collection</option>
            <option value="IN_LAB">Visit lab for test</option>
          </select>
        </label>
        {mode === "HOME_COLLECTION" ? (
          <div className="stack">
            <div className="row patient-lab-address-actions">
              <button type="button" className="secondary" onClick={() => setMapOpen(true)}>
                Pick address on map
              </button>
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  if (!navigator.geolocation) {
                    setError("Location is not available in this browser.");
                    return;
                  }
                  navigator.geolocation.getCurrentPosition(
                    (pos) => {
                      setLatitude(String(pos.coords.latitude));
                      setLongitude(String(pos.coords.longitude));
                      setMapOpen(true);
                    },
                    (err) =>
                      setError(err.message || "Allow location to open the map at your position."),
                    { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
                  );
                }}
              >
                Use current location
              </button>
            </div>
            <label>
              Street / area
              <input
                value={streetLine}
                onChange={(e) => setStreetLine(e.target.value)}
                required
                placeholder="House no., road, landmark"
              />
            </label>
            <div className="grid-2">
              <label>
                City
                <input value={city} onChange={(e) => setCity(e.target.value)} required />
              </label>
              <label>
                State
                <input value={stateName} onChange={(e) => setStateName(e.target.value)} required />
              </label>
              <label>
                PIN code
                <input value={pinCode} onChange={(e) => setPinCode(e.target.value)} />
              </label>
              <label>
                Country
                <input value={country} onChange={(e) => setCountry(e.target.value)} />
              </label>
              <label>
                Latitude
                <input value={latitude} onChange={(e) => setLatitude(e.target.value)} readOnly />
              </label>
              <label>
                Longitude
                <input value={longitude} onChange={(e) => setLongitude(e.target.value)} readOnly />
              </label>
            </div>
            <label>
              Full address (for lab technician)
              <textarea
                rows={3}
                readOnly
                value={collectionAddress}
                placeholder="Pick a point on the map to fill this address"
              />
            </label>
          </div>
        ) : (
          <p className="muted">
            In-lab visits get a token. Each patient ahead adds 10 minutes (e.g. 5 ahead from 11:00 →
            your time 11:50).
          </p>
        )}
        <label>
          Preferred date & time
          <input
            type="datetime-local"
            value={preferredAt}
            onChange={(e) => setPreferredAt(e.target.value)}
          />
        </label>
        <label>
          Notes / tests
          <input
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="e.g. CBC, fasting sugar"
          />
        </label>
        <button type="submit" disabled={saving}>
          {saving ? "Booking…" : "Book lab test"}
        </button>
      </form>

      <AddressMapPickerModal
        open={mapOpen}
        initialLat={latitude ? Number(latitude) : undefined}
        initialLng={longitude ? Number(longitude) : undefined}
        onClose={() => setMapOpen(false)}
        onConfirm={(picked) => {
          setStreetLine(picked.streetLine || picked.formattedAddress.split(",")[0] || "");
          setCity(picked.city);
          setStateName(picked.state);
          setPinCode(picked.pinCode);
          setCountry(picked.country || "India");
          setLatitude(String(picked.latitude));
          setLongitude(String(picked.longitude));
          setMapOpen(false);
          toast.success("Address updated from map.");
        }}
      />

      {chatBooking ? (
        <LabBookingChat
          bookingId={chatBooking.id}
          title={`Chat · ${chatBooking.bookingNumber || chatBooking.id}`}
          senderName={patientName || "Patient"}
          onClose={() => setChatId("")}
        />
      ) : null}

      <div className="table-scroll panel">
        <table className="table">
          <thead>
            <tr>
              <th>Booking</th>
              <th>Mode</th>
              <th>Status</th>
              <th>Lab / time</th>
              <th>Token</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {bookings.length === 0 ? (
              <tr>
                <td colSpan={6}>No lab bookings yet.</td>
              </tr>
            ) : (
              bookings.map((b) => (
                <tr key={b.id}>
                  <td>
                    <div>{b.bookingNumber}</div>
                    <div className="muted">{fmt(b.createdAt)}</div>
                  </td>
                  <td>{b.modeLabel || b.mode}</td>
                  <td>
                    {b.status}
                    {b.cancelCharge != null && b.status === "CANCELED" ? (
                      <div className="muted">Charge ₹{b.cancelCharge}</div>
                    ) : null}
                    {b.freeCancelRemainingSeconds && b.freeCancelRemainingSeconds > 0 ? (
                      <div className="muted">
                        Free cancel ~{Math.ceil(b.freeCancelRemainingSeconds / 60)} min
                      </div>
                    ) : null}
                  </td>
                  <td>
                    <div>{b.assignedLabName || "Waiting for lab"}</div>
                    <div>{fmt(b.visitTime || b.expectedVisitAt)}</div>
                    {b.techReachedAt ? <div className="muted">Reached home</div> : null}
                  </td>
                  <td>{b.tokenNumber != null ? `#${b.tokenNumber}` : "—"}</td>
                  <td className="stack">
                    {b.canChat ? (
                      <button type="button" className="secondary" onClick={() => setChatId(b.id)}>
                        Chat
                      </button>
                    ) : null}
                    {b.canCancel && b.status !== "CANCELED" && b.status !== "COMPLETED" ? (
                      <>
                        {b.techReachedAt ? (
                          <label>
                            Distance km
                            <input
                              value={cancelKm[b.id] || ""}
                              onChange={(e) =>
                                setCancelKm((prev) => ({ ...prev, [b.id]: e.target.value }))
                              }
                              inputMode="decimal"
                              placeholder="e.g. 4"
                            />
                          </label>
                        ) : null}
                        <button type="button" className="secondary" onClick={() => void onCancel(b)}>
                          Cancel
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
    </section>
  );
}
