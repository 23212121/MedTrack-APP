import { useCallback, useEffect, useMemo, useState } from "react";
import AmbulanceRouteMap from "../../components/AmbulanceRouteMap";
import { api, type AmbulanceQuote, type AmbulanceRequest } from "../../api";
import { session } from "../../dl/MedTrackSession";
import { toast } from "../../toast";

function fmt(iso?: string) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
  } catch {
    return iso;
  }
}

export default function PatientAmbulancePage() {
  const hospitalId = session.getHospitalId() || "";
  const [requests, setRequests] = useState<AmbulanceRequest[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [activeId, setActiveId] = useState("");
  const [patientLat, setPatientLat] = useState<number | null>(null);
  const [patientLng, setPatientLng] = useState<number | null>(null);
  const [quote, setQuote] = useState<AmbulanceQuote | null>(null);
  const [locating, setLocating] = useState(false);

  async function load() {
    try {
      const res = await api.myAmbulanceRequests();
      setRequests(res.requests || []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load ambulance requests");
    }
  }

  const refreshQuote = useCallback(
    async (lat: number, lng: number) => {
      try {
        const q = await api.ambulanceQuote({
          latitude: lat,
          longitude: lng,
          hospitalId: hospitalId || undefined,
        });
        setQuote(q);
      } catch (e) {
        setQuote(null);
        setError(e instanceof Error ? e.message : "Could not estimate ambulance fare");
      }
    },
    [hospitalId],
  );

  function captureLocation(onDone?: (lat: number, lng: number) => void) {
    setError("");
    if (!navigator.geolocation) {
      setError("This browser cannot share your location.");
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const lat = pos.coords.latitude;
        const lng = pos.coords.longitude;
        setPatientLat(lat);
        setPatientLng(lng);
        setLocating(false);
        void refreshQuote(lat, lng);
        onDone?.(lat, lng);
      },
      (err) => {
        setLocating(false);
        setError(err.message || "Allow location access to request an ambulance.");
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
    );
  }

  useEffect(() => {
    void load();
    captureLocation();
    const timer = window.setInterval(() => void load(), 5000);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!activeId || !navigator.geolocation) return;
    const watch = navigator.geolocation.watchPosition(
      (pos) => {
        const lat = pos.coords.latitude;
        const lng = pos.coords.longitude;
        setPatientLat(lat);
        setPatientLng(lng);
        void api
          .updateAmbulancePatientLocation(activeId, lat, lng)
          .then(() => load())
          .catch(() => undefined);
      },
      () => undefined,
      { enableHighAccuracy: true, maximumAge: 10000, timeout: 15000 },
    );
    return () => navigator.geolocation.clearWatch(watch);
  }, [activeId]);

  function requestAmbulance() {
    const send = (lat: number, lng: number) => {
      setBusy(true);
      void (async () => {
        try {
          const res = await api.requestAmbulance({
            latitude: lat,
            longitude: lng,
            hospitalId: hospitalId || undefined,
          });
          toast.success(res.message || "Ambulance requested");
          setActiveId(res.request.id);
          await load();
        } catch (e) {
          setError(e instanceof Error ? e.message : "Request failed");
        } finally {
          setBusy(false);
        }
      })();
    };
    if (patientLat != null && patientLng != null) {
      send(patientLat, patientLng);
      return;
    }
    captureLocation(send);
  }

  const open = requests.find(
    (r) => r.status && !["COMPLETED", "CANCELLED"].includes(r.status),
  );

  const mapView = useMemo(() => {
    const patient =
      open?.patientLatitude != null && open?.patientLongitude != null
        ? { latitude: open.patientLatitude, longitude: open.patientLongitude }
        : patientLat != null && patientLng != null
          ? { latitude: patientLat, longitude: patientLng }
          : null;
    if (!patient) return null;

    const ambLat = open?.ambulanceLatitude ?? quote?.ambulanceLatitude;
    const ambLng = open?.ambulanceLongitude ?? quote?.ambulanceLongitude;
    if (ambLat == null || ambLng == null) return null;

    const km =
      open?.distanceKm ??
      open?.offeredDistanceKm ??
      quote?.distanceKm ??
      0;
    const ratePerKm = open?.ratePerKm ?? quote?.ratePerKm ?? 0;
    if (!km || !ratePerKm) return null;

    return {
      patient,
      ambulance: { latitude: ambLat, longitude: ambLng },
      km,
      ratePerKm,
      estimatedMinutes: open?.estimatedMinutes ?? quote?.estimatedMinutes,
      driverName: open?.driverName ?? quote?.driverName,
    };
  }, [open, patientLat, patientLng, quote]);

  const displayRate = open?.ratePerKm ?? quote?.ratePerKm;
  const displayKm = open?.distanceKm ?? open?.offeredDistanceKm ?? quote?.distanceKm;
  const displayPrice = open?.estimatedPrice ?? quote?.estimatedPrice;
  const displayMinutes = open?.estimatedMinutes ?? quote?.estimatedMinutes;

  return (
    <section className="stack">
      <h1>Request ambulance</h1>
      <p className="lead">
        Your GPS is sent when you request help. The map shows distance to the nearest available
        ambulance, estimated arrival time, and fare (₹ per km from your hospital settings).
      </p>
      {error ? <div className="msg error">{error}</div> : null}
      <div className="panel stack">
        <div className="row patient-lab-address-actions">
          <button type="button" className="secondary" onClick={() => captureLocation()} disabled={locating}>
            {locating ? "Updating location…" : "Refresh my location"}
          </button>
          <button type="button" disabled={busy || Boolean(open)} onClick={requestAmbulance}>
            {busy ? "Sending request…" : open ? "Request already open" : "Request ambulance"}
          </button>
        </div>

        {displayRate != null ? (
          <p className="muted" style={{ margin: 0 }}>
            Ambulance rate: <strong>₹{displayRate}/km</strong>
            {displayKm != null ? (
              <>
                {" "}
                · Distance <strong>{displayKm} km</strong>
              </>
            ) : null}
            {displayPrice != null ? (
              <>
                {" "}
                · Est. fare <strong>₹{displayPrice}</strong>
              </>
            ) : null}
            {displayMinutes != null ? (
              <>
                {" "}
                · Est. time <strong>~{displayMinutes} min</strong>
              </>
            ) : null}
          </p>
        ) : null}

        {mapView ? (
          <AmbulanceRouteMap
            patient={mapView.patient}
            ambulance={mapView.ambulance}
            patientLabel="Your pickup location"
            ambulanceLabel={mapView.driverName ? `Ambulance · ${mapView.driverName}` : "Ambulance"}
            km={mapView.km}
            ratePerKm={mapView.ratePerKm}
            estimatedMinutes={mapView.estimatedMinutes}
          />
        ) : quote?.ambulanceAvailable === false ? (
          <p className="msg error">{quote.message || "No ambulance with live GPS is available nearby."}</p>
        ) : patientLat != null ? (
          <p className="muted">Waiting for a nearby ambulance GPS signal to draw the route…</p>
        ) : null}

        {open ? (
          <div className="stack">
            <p>
              <strong>{open.requestNumber}</strong> · {open.status}
            </p>
            {open.driverName ? (
              <p>
                Driver {open.driverName}
                {open.driverMobile ? ` · ${open.driverMobile}` : ""}
              </p>
            ) : (
              <p className="muted">Notifying nearest available ambulances…</p>
            )}
            {open.mapsUrl ? (
              <a href={open.mapsUrl} target="_blank" rel="noreferrer">
                Open pickup in Google Maps
              </a>
            ) : null}
            <button
              type="button"
              className="secondary"
              onClick={() =>
                void api.cancelAmbulanceRequest(open.id).then(() => {
                  setActiveId("");
                  return load();
                })
              }
            >
              Cancel request
            </button>
          </div>
        ) : null}
      </div>
      <div className="table-scroll panel">
        <table className="table">
          <thead>
            <tr>
              <th>Request</th>
              <th>Status</th>
              <th>Distance</th>
              <th>Fare</th>
              <th>Driver</th>
              <th>When</th>
            </tr>
          </thead>
          <tbody>
            {requests.length === 0 ? (
              <tr>
                <td colSpan={6}>No ambulance requests yet.</td>
              </tr>
            ) : (
              requests.map((r) => (
                <tr key={r.id}>
                  <td>{r.requestNumber}</td>
                  <td>{r.status}</td>
                  <td>{r.distanceKm != null ? `${r.distanceKm} km` : "—"}</td>
                  <td>
                    {r.estimatedPrice != null
                      ? `₹${r.estimatedPrice}${r.ratePerKm ? ` (@ ₹${r.ratePerKm}/km)` : ""}`
                      : "—"}
                  </td>
                  <td>{r.driverName || "—"}</td>
                  <td>{fmt(r.createdAt)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
