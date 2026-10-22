import { useEffect, useRef } from "react";
import L from "leaflet";
import iconRetina from "leaflet/dist/images/marker-icon-2x.png";
import icon from "leaflet/dist/images/marker-icon.png";
import iconShadow from "leaflet/dist/images/marker-shadow.png";
import "leaflet/dist/leaflet.css";

const DefaultIcon = L.icon({
  iconUrl: icon,
  iconRetinaUrl: iconRetina,
  shadowUrl: iconShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
});
L.Marker.prototype.options.icon = DefaultIcon;

type Point = { latitude: number; longitude: number };

type Props = {
  ambulance: Point;
  patient: Point;
  ambulanceLabel?: string;
  patientLabel?: string;
  km: number;
  ratePerKm: number;
  estimatedMinutes?: number;
};

export default function AmbulanceRouteMap({
  ambulance,
  patient,
  ambulanceLabel,
  patientLabel,
  km,
  ratePerKm,
  estimatedMinutes,
}: Props) {
  const hostRef = useRef<HTMLDivElement>(null);
  const charge = Math.round(km * ratePerKm * 100) / 100;

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const map = L.map(host, { scrollWheelZoom: true });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(map);
    const ambLl: L.LatLngExpression = [ambulance.latitude, ambulance.longitude];
    const patientLl: L.LatLngExpression = [patient.latitude, patient.longitude];
    L.marker(ambLl).addTo(map).bindPopup(ambulanceLabel || "Ambulance");
    L.marker(patientLl).addTo(map).bindPopup(patientLabel || "Your location");
    const line = L.polyline([ambLl, patientLl], {
      color: "#dc2626",
      weight: 6,
      opacity: 0.9,
      dashArray: "8 6",
    }).addTo(map);
    map.fitBounds(line.getBounds().pad(0.35));
    window.setTimeout(() => map.invalidateSize(), 80);
    return () => {
      map.remove();
    };
  }, [
    ambulance.latitude,
    ambulance.longitude,
    patient.latitude,
    patient.longitude,
    ambulanceLabel,
    patientLabel,
  ]);

  return (
    <div className="stack">
      <div ref={hostRef} className="address-map-canvas" style={{ minHeight: 280 }} />
      <p className="muted" style={{ margin: 0 }}>
        Distance from ambulance to your pickup: <strong>{km} km</strong> · Rate{" "}
        <strong>₹{ratePerKm}/km</strong> · Est. fare <strong>₹{charge}</strong>
        {estimatedMinutes != null ? (
          <>
            {" "}
            · Est. arrival <strong>~{estimatedMinutes} min</strong>
          </>
        ) : null}
      </p>
    </div>
  );
}
