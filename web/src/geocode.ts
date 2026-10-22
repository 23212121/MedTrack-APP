export type GeocodedAddress = {
  latitude: number;
  longitude: number;
  formattedAddress: string;
  streetLine: string;
  city: string;
  state: string;
  pinCode: string;
  country: string;
};

type NominatimAddress = {
  house_number?: string;
  road?: string;
  neighbourhood?: string;
  suburb?: string;
  city?: string;
  town?: string;
  village?: string;
  state?: string;
  postcode?: string;
  country?: string;
};

function streetFromParts(addr: NominatimAddress): string {
  const parts = [addr.house_number, addr.road, addr.neighbourhood, addr.suburb].filter(Boolean);
  return parts.join(", ");
}

function cityFromParts(addr: NominatimAddress): string {
  return addr.city || addr.town || addr.village || addr.suburb || "";
}

export async function reverseGeocode(lat: number, lng: number): Promise<GeocodedAddress> {
  const params = new URLSearchParams({
    format: "json",
    lat: String(lat),
    lon: String(lng),
    addressdetails: "1",
  });
  const res = await fetch(`https://nominatim.openstreetmap.org/reverse?${params}`, {
    headers: {
      Accept: "application/json",
      "Accept-Language": "en",
    },
  });
  if (!res.ok) {
    throw new Error("Could not resolve this location to an address.");
  }
  const data = (await res.json()) as {
    display_name?: string;
    address?: NominatimAddress;
  };
  const addr = data.address || {};
  const streetLine = streetFromParts(addr);
  const city = cityFromParts(addr);
  const state = addr.state || "";
  const pinCode = addr.postcode || "";
  const country = addr.country || "";
  const formattedAddress =
    data.display_name ||
    [streetLine, city, state, pinCode, country].filter(Boolean).join(", ");
  return {
    latitude: lat,
    longitude: lng,
    formattedAddress,
    streetLine,
    city,
    state,
    pinCode,
    country,
  };
}

export async function forwardGeocode(query: string): Promise<{ latitude: number; longitude: number } | null> {
  const q = query.trim();
  if (!q) return null;
  const params = new URLSearchParams({ format: "json", limit: "1", q });
  const res = await fetch(`https://nominatim.openstreetmap.org/search?${params}`, {
    headers: { Accept: "application/json", "Accept-Language": "en" },
  });
  if (!res.ok) return null;
  const rows = (await res.json()) as { lat?: string; lon?: string }[];
  const hit = rows[0];
  if (!hit?.lat || !hit.lon) return null;
  const latitude = Number(hit.lat);
  const longitude = Number(hit.lon);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;
  return { latitude, longitude };
}

export function parseGpsFromAddress(address?: string): { latitude: number; longitude: number } | null {
  if (!address) return null;
  const match = address.match(/GPS:\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)/i);
  if (!match) return null;
  const latitude = Number(match[1]);
  const longitude = Number(match[2]);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;
  return { latitude, longitude };
}

/** Great-circle distance in kilometres. */
export function distanceKm(
  a: { latitude: number; longitude: number },
  b: { latitude: number; longitude: number },
): number {
  const R = 6371;
  const dLat = ((b.latitude - a.latitude) * Math.PI) / 180;
  const dLng = ((b.longitude - a.longitude) * Math.PI) / 180;
  const lat1 = (a.latitude * Math.PI) / 180;
  const lat2 = (b.latitude * Math.PI) / 180;
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return Math.round(2 * R * Math.asin(Math.min(1, Math.sqrt(h))) * 100) / 100;
}

export const LAB_DISTANCE_RATE_PER_KM = 10;

export function buildCollectionAddress(fields: {
  streetLine: string;
  city: string;
  state: string;
  pinCode: string;
  country: string;
  latitude: string;
  longitude: string;
}): string {
  const lines: string[] = [];
  if (fields.streetLine.trim()) lines.push(fields.streetLine.trim());
  const cityLine = [fields.city, fields.state, fields.pinCode].filter((p) => p.trim()).join(", ");
  if (cityLine.trim()) lines.push(cityLine.trim());
  if (fields.country.trim()) lines.push(fields.country.trim());
  const lat = fields.latitude.trim();
  const lng = fields.longitude.trim();
  if (lat && lng) lines.push(`GPS: ${lat}, ${lng}`);
  return lines.join("\n");
}
