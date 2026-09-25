import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  api,
  type AppointmentPayment,
  type EmergencyBed,
  type EmergencyDoctor,
  type EmergencyHospital,
  type EmergencyHospitalDetail,
} from "../../api";
import AppointmentPaymentModal from "../../components/AppointmentPaymentModal";
import { IndiaStateCityFields } from "../../components/IndiaStateCityFields";
import { session } from "../../dl/MedTrackSession";
import { toast } from "../../toast";
import VoiceEmergency from "../../voice/VoiceEmergency";

function BedArt({ booked }: { booked: boolean }) {
  return (
    <svg className="emergency-bed-art" viewBox="0 0 160 110" aria-hidden="true">
      <rect x="8" y="48" width="144" height="36" rx="8" fill={booked ? "#c9d6da" : "#d7eef1"} />
      <rect x="18" y="28" width="92" height="28" rx="10" fill={booked ? "#9fb3b8" : "#8ec8cf"} />
      <rect x="108" y="18" width="28" height="38" rx="6" fill={booked ? "#7f9499" : "#0e7c86"} />
      <circle cx="34" cy="90" r="8" fill="#3d5a61" />
      <circle cx="126" cy="90" r="8" fill="#3d5a61" />
      <path d="M24 42h70" stroke="#fff" strokeWidth="4" strokeLinecap="round" opacity="0.7" />
    </svg>
  );
}

export default function EmergencyServicePage() {
  const [state, setState] = useState("");
  const [city, setCity] = useState("");
  const [query, setQuery] = useState("");
  const [patientName, setPatientName] = useState(session.getPatientName() || "");
  const [patientPhone, setPatientPhone] = useState(session.getPatientPhone() || "");
  const [hospitals, setHospitals] = useState<EmergencyHospital[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<EmergencyHospitalDetail | null>(null);
  const [detailBed, setDetailBed] = useState<EmergencyBed | null>(null);
  const [payment, setPayment] = useState<AppointmentPayment | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [bookingId, setBookingId] = useState("");

  async function loadHospitals(nextState = state, nextCity = city, nextQuery = query) {
    setLoading(true);
    setError("");
    try {
      const res = await api.emergencyHospitals({
        state: nextState || undefined,
        city: nextCity || undefined,
        q: nextQuery || undefined,
      });
      setHospitals(res.hospitals || []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load hospitals");
    } finally {
      setLoading(false);
    }
  }

  async function openHospital(hospitalId: number) {
    setSelectedId(hospitalId);
    setError("");
    try {
      const data = await api.emergencyHospital(hospitalId);
      setDetail(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load hospital");
    }
  }

  useEffect(() => {
    let cancelled = false;
    api
      .emergencyContext()
      .then((ctx) => {
        if (cancelled) return;
        const nextState = ctx.state || "";
        const nextCity = ctx.city || "";
        setState(nextState);
        setCity(nextCity);
        if (ctx.patientName) setPatientName(ctx.patientName);
        if (ctx.patientPhone) setPatientPhone(ctx.patientPhone);
        void loadHospitals(nextState, nextCity, "");
      })
      .catch(() => {
        void loadHospitals("", "", "");
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function onSearch(e?: FormEvent) {
    e?.preventDefault();
    setSelectedId(null);
    setDetail(null);
    await loadHospitals(state, city, query);
  }

  async function bookBed(bed: EmergencyBed) {
    if (!patientName.trim()) {
      setError("Enter the patient name before booking a bed.");
      return;
    }
    setError("");
    try {
      const res = await api.bookEmergencyBed(bed.id, {
        patientName: patientName.trim(),
        patientPhone: patientPhone.trim() || undefined,
        patientId: session.getPatientId() || undefined,
      });
      toast.success(res.message || `Bed ${bed.bedNumber} booked`);
      setBookingId(res.booking.id);
      if (selectedId) await openHospital(selectedId);
      setDetailBed((current) =>
        current && current.id === bed.id ? { ...res.bed, booking: res.booking } : current,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not book bed");
    }
  }

  async function payBooking(id: string) {
    setError("");
    try {
      const pay = await api.payEmergencyBooking(id);
      setPayment(pay);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not open payment");
    }
  }

  async function attachSlip(id: string, file: File | null) {
    if (!file) return;
    const form = new FormData();
    form.append("file", file);
    try {
      const res = await api.attachEmergencySlip(id, form);
      toast.success(res.message || "Payment slip attached");
      if (selectedId) await openHospital(selectedId);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not attach slip");
    }
  }

  async function onVoiceBook(bedNumber: string) {
    if (!detail) {
      setError("Select a hospital first, then book a bed by voice.");
      return;
    }
    const bed = detail.beds.find(
      (b) => b.bedNumber.replace(/\s/g, "").toUpperCase() === bedNumber.replace(/\s/g, "").toUpperCase(),
    );
    if (!bed) {
      setError(`Bed ${bedNumber} was not found at this hospital.`);
      return;
    }
    await bookBed(bed);
  }

  const stats = useMemo(() => {
    if (detail) {
      return {
        total: detail.totalBeds,
        booked: detail.bookedBeds,
        available: detail.availableBeds,
      };
    }
    return {
      total: hospitals.reduce((n, h) => n + (h.totalBeds || 0), 0),
      booked: hospitals.reduce((n, h) => n + (h.bookedBeds || 0), 0),
      available: hospitals.reduce((n, h) => n + (h.availableBeds || 0), 0),
    };
  }, [detail, hospitals]);

  const doctors: EmergencyDoctor[] = detail?.doctors || [];
  const selectedHospitalName =
    detail?.hospitalName || hospitals.find((h) => h.hospitalId === selectedId)?.hospitalName || "";

  return (
    <section className="panel emergency-page" aria-label="Check Emergency Service">
      <div className="emergency-page-head">
        <div>
          <p className="muted">Emergency</p>
          <h1>Check Emergency Service</h1>
          <p className="lead">
            Search hospitals by state and city, see emergency beds and doctors, then book a bed for the
            patient. Payment by QR is optional; you can attach a slip if you want.
          </p>
        </div>
        <VoiceEmergency
          draft={{
            state,
            city,
            hospitalName: selectedHospitalName,
            patientName,
            patientPhone,
          }}
          hospitals={hospitals}
          onPatch={(patch) => {
            if (patch.state != null) setState(patch.state);
            if (patch.city != null) setCity(patch.city);
            if (patch.patientName != null) setPatientName(patch.patientName);
            if (patch.patientPhone != null) setPatientPhone(patch.patientPhone);
            if (patch.state != null || patch.city != null) {
              void loadHospitals(patch.state ?? state, patch.city ?? city, query);
            }
          }}
          onSelectHospital={(id) => void openHospital(id)}
          onBookHeard={(bedNumber) => void onVoiceBook(bedNumber)}
        />
      </div>

      {error ? <div className="msg error">{error}</div> : null}

      <form className="emergency-search" onSubmit={(e) => void onSearch(e)}>
        <IndiaStateCityFields
          state={state}
          city={city}
          onStateChange={(next) => {
            setState(next);
            setCity("");
          }}
          onCityChange={setCity}
          allowAll
        />
        <label>
          Hospital
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search hospital name or ID"
          />
        </label>
        <div className="row">
          <label>
            Patient name
            <input
              value={patientName}
              onChange={(e) => setPatientName(e.target.value)}
              placeholder="Required to book a bed"
            />
          </label>
          <label>
            Patient phone
            <input
              value={patientPhone}
              onChange={(e) => setPatientPhone(e.target.value)}
              placeholder="10-digit mobile"
            />
          </label>
        </div>
        <button type="submit" disabled={loading}>
          {loading ? "Searching…" : "Search hospitals"}
        </button>
      </form>

      <div className="emergency-stats">
        <div className="stat">
          <strong>{stats.total}</strong>
          <span>Total beds</span>
        </div>
        <div className="stat">
          <strong>{stats.booked}</strong>
          <span>Booked beds</span>
        </div>
        <div className="stat">
          <strong>{stats.available}</strong>
          <span>Available beds</span>
        </div>
        <div className="stat">
          <strong>{doctors.filter((d) => d.availableNow !== false).length || doctors.length}</strong>
          <span>Emergency doctors</span>
        </div>
      </div>

      {!detail ? (
        <div className="emergency-hospital-list">
          {hospitals.length === 0 && !loading ? (
            <p className="muted">No hospitals found for this city. Try another state or city.</p>
          ) : null}
          {hospitals.map((h) => (
            <button
              key={h.hospitalId}
              type="button"
              className="emergency-hospital-card"
              onClick={() => void openHospital(h.hospitalId)}
            >
              <h3>{h.hospitalName}</h3>
              <p>
                {[h.city, h.state].filter(Boolean).join(", ") || "Location not set"} · ID {h.hospitalId}
              </p>
              <p>
                Beds {h.availableBeds}/{h.totalBeds} available · {h.bookedBeds} booked
              </p>
            </button>
          ))}
        </div>
      ) : (
        <div className="emergency-hospital-detail">
          <div className="emergency-detail-bar">
            <div>
              <h2>{detail.hospitalName}</h2>
              <p className="muted">
                {[detail.city, detail.state].filter(Boolean).join(", ")}
                {detail.primaryContact ? ` · ${detail.primaryContact}` : ""}
              </p>
            </div>
            <button
              type="button"
              className="secondary"
              onClick={() => {
                setDetail(null);
                setSelectedId(null);
                setDetailBed(null);
              }}
            >
              Back to hospitals
            </button>
          </div>

          <h3>Emergency doctors</h3>
          {doctors.length === 0 ? (
            <p className="muted">No doctors listed for this hospital yet.</p>
          ) : (
            <ul className="emergency-doctor-list">
              {doctors.map((d) => (
                <li key={d.doctorId}>
                  <strong>{d.doctorName}</strong>
                  <span>
                    {[d.department, d.specialization].filter(Boolean).join(" · ") || "On call"}
                    {d.availableNow === false ? " · not rostered today" : " · available"}
                  </span>
                </li>
              ))}
            </ul>
          )}

          <h3>Emergency beds</h3>
          <div className="emergency-beds">
            {detail.beds.map((bed) => {
              const booked = bed.status === "BOOKED";
              return (
                <article
                  key={bed.id}
                  className={`emergency-bed-card${booked ? " is-booked" : ""}`}
                >
                  <div className="emergency-bed-visual">
                    <BedArt booked={booked} />
                    <button
                      type="button"
                      className="emergency-more-link"
                      onClick={() => setDetailBed(bed)}
                    >
                      More detail
                    </button>
                  </div>
                  <div className="emergency-bed-meta">
                    <strong>{bed.bedNumber}</strong>
                    <span>
                      {bed.ward || "Emergency"} · ₹{Number(bed.fees || 0).toFixed(0)}
                    </span>
                    <span className={booked ? "emergency-pill is-booked" : "emergency-pill"}>
                      {booked ? "Booked" : "Available"}
                    </span>
                    {booked && bed.booking?.patientName ? (
                      <span className="muted">Patient: {bed.booking.patientName}</span>
                    ) : null}
                    {booked ? (
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => bed.booking && void payBooking(bed.booking.id)}
                      >
                        Pay online
                      </button>
                    ) : (
                      <button type="button" onClick={() => void bookBed(bed)}>
                        Book bed
                      </button>
                    )}
                  </div>
                </article>
              );
            })}
          </div>
        </div>
      )}

      {detailBed ? (
        <EmergencyBedModal
          bed={detailBed}
          doctors={doctors}
          hospitalName={detail?.hospitalName || ""}
          hospitalContact={detail?.primaryContact || ""}
          patientName={patientName}
          onClose={() => setDetailBed(null)}
          onBook={() => void bookBed(detailBed)}
          onPay={(id) => void payBooking(id)}
          onAttachSlip={(id, file) => void attachSlip(id, file)}
        />
      ) : null}

      {payment ? (
        <AppointmentPaymentModal
          payment={payment}
          title="Pay emergency bed fee"
          doctorName={detail?.hospitalName}
          onPaid={() => {
            toast.success("Payment confirmed");
            setPayment(null);
            if (selectedId) void openHospital(selectedId);
          }}
          onClose={() => setPayment(null)}
        />
      ) : null}
      {bookingId ? <span className="sr-only">Last booking {bookingId}</span> : null}
    </section>
  );
}

function EmergencyBedModal({
  bed,
  doctors,
  hospitalName,
  hospitalContact,
  patientName,
  onClose,
  onBook,
  onPay,
  onAttachSlip,
}: {
  bed: EmergencyBed;
  doctors: EmergencyDoctor[];
  hospitalName: string;
  hospitalContact: string;
  patientName: string;
  onClose: () => void;
  onBook: () => void;
  onPay: (bookingId: string) => void;
  onAttachSlip: (bookingId: string, file: File | null) => void;
}) {
  const booked = bed.status === "BOOKED";
  const booking = bed.booking;
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <div
        className="modal panel emergency-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="emergency-bed-title"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <p className="modal-eyebrow">{hospitalName}</p>
            <h2 id="emergency-bed-title">Bed {bed.bedNumber}</h2>
          </div>
          <button type="button" className="secondary" onClick={onClose}>
            Close
          </button>
        </div>
        <dl className="emergency-modal-dl">
          <div>
            <dt>Ward</dt>
            <dd>{bed.ward || "Emergency"}</dd>
          </div>
          <div>
            <dt>Bed fee</dt>
            <dd>₹{Number(bed.fees || 0).toFixed(0)}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd>{booked ? "Booked" : "Available"}</dd>
          </div>
          <div>
            <dt>Hospital contact</dt>
            <dd>{hospitalContact || "—"}</dd>
          </div>
          {booking ? (
            <>
              <div>
                <dt>Patient</dt>
                <dd>{booking.patientName}</dd>
              </div>
              <div>
                <dt>Payment</dt>
                <dd>{booking.paymentStatus || "UNPAID"}</dd>
              </div>
            </>
          ) : (
            <div>
              <dt>Booking for</dt>
              <dd>{patientName || "Enter patient name on the page"}</dd>
            </div>
          )}
        </dl>
        <h3>Emergency doctors</h3>
        {doctors.length === 0 ? (
          <p className="muted">No emergency doctors listed.</p>
        ) : (
          <ul className="emergency-doctor-list">
            {doctors.map((d) => (
              <li key={d.doctorId}>
                <strong>{d.doctorName}</strong>
                <span>{[d.department, d.mobileNumber].filter(Boolean).join(" · ")}</span>
              </li>
            ))}
          </ul>
        )}
        <p className="muted">{bed.notes || "Emergency admission. Fee is charged per bed."}</p>
        <div className="modal-actions">
          {!booked ? (
            <button type="button" onClick={onBook}>
              Book this bed
            </button>
          ) : (
            <button type="button" onClick={() => booking && onPay(booking.id)}>
              Online payment
            </button>
          )}
          {booking ? (
            <label className="secondary emergency-slip">
              Attach payment slip (optional)
              <input
                type="file"
                accept="image/*,.pdf"
                hidden
                onChange={(e) => onAttachSlip(booking.id, e.target.files?.[0] || null)}
              />
            </label>
          ) : null}
        </div>
      </div>
    </div>
  );
}
