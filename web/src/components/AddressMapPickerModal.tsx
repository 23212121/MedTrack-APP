import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import L from "leaflet";
import iconRetina from "leaflet/dist/images/marker-icon-2x.png";
import icon from "leaflet/dist/images/marker-icon.png";
import iconShadow from "leaflet/dist/images/marker-shadow.png";
import "leaflet/dist/leaflet.css";
import { GeocodedAddress, reverseGeocode } from "../geocode";

const DefaultIcon = L.icon({
  iconUrl: icon,
  iconRetinaUrl: iconRetina,
  shadowUrl: iconShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
});
L.Marker.prototype.options.icon = DefaultIcon;

const DEFAULT_CENTER: L.LatLngExpression = [22.7196, 75.8577];

type Props = {
  open: boolean;
  initialLat?: number;
  initialLng?: number;
  onConfirm: (address: GeocodedAddress) => void;
  onClose: () => void;
};

export default function AddressMapPickerModal({
  open,
  initialLat,
  initialLng,
  onConfirm,
  onClose,
}: Props) {
  const mapHostRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<L.Map | null>(null);
  const markerRef = useRef<L.Marker | null>(null);
  const geocodeTimer = useRef<number | null>(null);
  const [loadingGeo, setLoadingGeo] = useState(false);
  const [resolving, setResolving] = useState(false);
  const [error, setError] = useState("");
  const [preview, setPreview] = useState<GeocodedAddress | null>(null);

  function scheduleReverse(lat: number, lng: number) {
    if (geocodeTimer.current) window.clearTimeout(geocodeTimer.current);
    geocodeTimer.current = window.setTimeout(() => {
      void (async () => {
        setResolving(true);
        setError("");
        try {
          const addr = await reverseGeocode(lat, lng);
          setPreview(addr);
        } catch (e) {
          setError(e instanceof Error ? e.message : "Could not load address for this pin.");
          setPreview({
            latitude: lat,
            longitude: lng,
            formattedAddress: `${lat.toFixed(6)}, ${lng.toFixed(6)}`,
            streetLine: "",
            city: "",
            state: "",
            pinCode: "",
            country: "",
          });
        } finally {
          setResolving(false);
        }
      })();
    }, 450);
  }

  function placeMarker(lat: number, lng: number, pan = true) {
    const map = mapRef.current;
    if (!map) return;
    if (!markerRef.current) {
      markerRef.current = L.marker([lat, lng], { draggable: true }).addTo(map);
      markerRef.current.on("dragend", () => {
        const pos = markerRef.current?.getLatLng();
        if (pos) scheduleReverse(pos.lat, pos.lng);
      });
    } else {
      markerRef.current.setLatLng([lat, lng]);
    }
    if (pan) map.setView([lat, lng], Math.max(map.getZoom(), 16));
    scheduleReverse(lat, lng);
  }

  useEffect(() => {
    if (!open) return;
    setError("");
    setPreview(null);
    const startLat = initialLat;
    const startLng = initialLng;

    const timer = window.setTimeout(() => {
      const host = mapHostRef.current;
      if (!host || mapRef.current) return;
      const map = L.map(host, { zoomControl: true }).setView(
        startLat != null && startLng != null ? [startLat, startLng] : DEFAULT_CENTER,
        startLat != null && startLng != null ? 16 : 12,
      );
      L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
        maxZoom: 19,
      }).addTo(map);
      map.on("click", (e) => placeMarker(e.latlng.lat, e.latlng.lng));
      mapRef.current = map;

      if (startLat != null && startLng != null) {
        placeMarker(startLat, startLng, false);
      } else if (navigator.geolocation) {
        setLoadingGeo(true);
        navigator.geolocation.getCurrentPosition(
          (pos) => {
            setLoadingGeo(false);
            placeMarker(pos.coords.latitude, pos.coords.longitude);
          },
          () => {
            setLoadingGeo(false);
          },
          { enableHighAccuracy: true, timeout: 12000, maximumAge: 60000 },
        );
      }
      window.setTimeout(() => map.invalidateSize(), 120);
    }, 0);

    return () => {
      window.clearTimeout(timer);
      if (geocodeTimer.current) window.clearTimeout(geocodeTimer.current);
      if (mapRef.current) {
        mapRef.current.remove();
        mapRef.current = null;
      }
      markerRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  function useMyLocation() {
    if (!navigator.geolocation) {
      setError("Location is not available in this browser.");
      return;
    }
    setLoadingGeo(true);
    setError("");
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLoadingGeo(false);
        placeMarker(pos.coords.latitude, pos.coords.longitude);
      },
      (err) => {
        setLoadingGeo(false);
        setError(err.message || "Allow location access to use your current position.");
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
    );
  }

  if (!open) return null;

  return createPortal(
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <div
        className="modal panel address-map-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="address-map-title"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <p className="modal-eyebrow">Home sample collection</p>
            <h2 id="address-map-title">Choose pickup address on map</h2>
          </div>
          <button type="button" className="modal-close" aria-label="Close" onClick={onClose}>
            ×
          </button>
        </div>
        <p className="muted">
          Tap the map or drag the pin. We fill your address from the selected location.
        </p>
        <div className="address-map-toolbar row">
          <button type="button" className="secondary" onClick={useMyLocation} disabled={loadingGeo}>
            {loadingGeo ? "Finding you…" : "Use my current location"}
          </button>
          {resolving ? <span className="muted">Updating address…</span> : null}
        </div>
        <div ref={mapHostRef} className="address-map-canvas" aria-label="Map to pick address" />
        {error ? <div className="msg error">{error}</div> : null}
        {preview ? (
          <div className="address-map-preview stack">
            <strong>Selected address</strong>
            <span>{preview.formattedAddress}</span>
            <div className="grid-2">
              <span>
                <strong>City:</strong> {preview.city || "—"}
              </span>
              <span>
                <strong>State:</strong> {preview.state || "—"}
              </span>
              <span>
                <strong>PIN:</strong> {preview.pinCode || "—"}
              </span>
              <span>
                <strong>GPS:</strong> {preview.latitude.toFixed(6)}, {preview.longitude.toFixed(6)}
              </span>
            </div>
          </div>
        ) : (
          <p className="muted">Select a point on the map to load the address.</p>
        )}
        <div className="modal-actions">
          <button
            type="button"
            disabled={!preview || resolving}
            onClick={() => preview && onConfirm(preview)}
          >
            Use this address
          </button>
          <button type="button" className="secondary" onClick={onClose}>
            Cancel
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
