import { FormEvent, useState } from "react";
import { Link } from "react-router-dom";

/**
 * Download a direct file URL (PDF, image, zip, etc.) via the MedTrack API.
 * Streaming sites like YouTube are blocked on the server.
 */
export default function DownloadFilePage() {
  const [url, setUrl] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    const trimmed = url.trim();
    if (!trimmed) {
      setError("Paste a direct file link (http/https)");
      return;
    }
    if (!/^https?:\/\//i.test(trimmed)) {
      setError("URL must start with http:// or https://");
      return;
    }
    if (/youtube\.com|youtu\.be|vimeo\.com/i.test(trimmed)) {
      setError("YouTube / streaming links are not supported. Use a direct file URL.");
      return;
    }

    setBusy(true);
    try {
      const endpoint = `/api/download/file?url=${encodeURIComponent(trimmed)}`;
      const res = await fetch(endpoint);
      if (!res.ok) {
        let msg = `Download failed (${res.status})`;
        try {
          const text = await res.text();
          if (text) {
            const parsed = JSON.parse(text) as { message?: string };
            msg = parsed.message || text.slice(0, 200);
          }
        } catch {
          /* keep msg */
        }
        throw new Error(msg);
      }

      const blob = await res.blob();
      let filename = "download.bin";
      const cd = res.headers.get("Content-Disposition");
      if (cd) {
        const m = /filename="?([^";]+)"?/i.exec(cd);
        if (m?.[1]) filename = m[1];
      } else {
        try {
          const path = new URL(trimmed).pathname;
          const last = path.split("/").pop();
          if (last) filename = decodeURIComponent(last);
        } catch {
          /* keep default */
        }
      }

      const objectUrl = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = objectUrl;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(objectUrl);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Download failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel" style={{ maxWidth: 520, margin: "2rem auto" }}>
      <h1 style={{ color: "var(--brand-dark)" }}>Download linked file</h1>
      <p className="lead">
        Paste a direct file URL (PDF, image, zip, document). Streaming sites are
        not supported.
      </p>
      {error && <div className="msg error">{error}</div>}
      <form className="stack" onSubmit={onSubmit}>
        <label>
          File link
          <input
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://example.com/files/report.pdf"
            autoComplete="off"
            required
          />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? "Downloading…" : "Download file"}
        </button>
      </form>
      <p className="lead" style={{ marginTop: "1rem", marginBottom: 0 }}>
        <Link to="/login" style={{ textDecoration: "underline" }}>
          Back to sign in
        </Link>
      </p>
    </section>
  );
}
