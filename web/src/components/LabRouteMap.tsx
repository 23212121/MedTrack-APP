import { useEffect, useRef } from "react";
import L from "leaflet";
import iconRetina from "leaflet/dist/images/marker-icon-2x.png";
import icon from "leaflet/dist/images/marker-icon.png";
import iconShadow from "leaflet/dist/images/marker-shadow.png";
import "leaflet/dist/leaflet.css";
import { LAB_DISTANCE_RATE_PER_KM } from "../geocode";

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
  lab: Point;
  patient: Point;
  labLabel?: string;
  patientLabel?: string;
  km: number;
};

export default function LabRouteMap({ lab, patient, labLabel, patientLabel, km }: Props) {
  const hostRef = useRef<HTMLDivElement>(null);
  const charge = Math.round(km * LAB_DISTANCE_RATE_PER_KM * 100) / 100;

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const map = L.map(host, { scrollWheelZoom: false });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(map);
    const labLl: L.LatLngExpression = [lab.latitude, lab.longitude];
    const patientLl: L.LatLngExpression = [patient.latitude, patient.longitude];
    L.marker(labLl).addTo(map).bindPopup(labLabel || "Lab");
    L.marker(patientLl).addTo(map).bindPopup(patientLabel || "Patient");
    const line = L.polyline([labLl, patientLl], {
      color: "#c2410c",
      weight: 6,
      opacity: 0.9,
    }).addTo(map);
    map.fitBounds(line.getBounds().pad(0.35));
    return () => {
      map.remove();
    };
  }, [lab.latitude, lab.longitude, patient.latitude, patient.longitude, labLabel, patientLabel]);

  return (
    <div className="stack">
      <div ref={hostRef} style={{ height: 280, borderRadius: 12, overflow: "hidden" }} />
      <p className="muted" style={{ margin: 0 }}>
        Highlighted route: lab to patient. <strong>{km} km</strong> × ₹{LAB_DISTANCE_RATE_PER_KM}/km ={" "}
        <strong>₹{charge}</strong>
      </p>
    </div>
  );
}
