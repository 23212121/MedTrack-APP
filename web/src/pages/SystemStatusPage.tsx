import { useCallback, useEffect, useState } from "react";
import { api, SystemStatusResponse } from "../api";

function statusClass(status: string | undefined) {
  const s = (status || "").toUpperCase();
  if (s === "UP" || s === "EMBEDDED") return "status-up";
  if (s === "DEGRADED") return "status-degraded";
  return "status-down";
}

function statusLabel(status: string | undefined) {
  return (status || "UNKNOWN").toUpperCase();
}

function formatTime(iso?: string) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

export default function SystemStatusPage() {
  const [data, setData] = useState<SystemStatusResponse | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [lastOkAt, setLastOkAt] = useState<string>("");

  const load = useCallback(async (manual = false) => {
    if (manual) setRefreshing(true);
    try {
      const res = await api.systemStatus();
      setData(res);
      setError("");
      setLastOkAt(new Date().toLocaleTimeString());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load status");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
    const id = window.setInterval(() => void load(), 10000);
    return () => window.clearInterval(id);
  }, [load]);

  const services = data?.services ?? [];
  const upCount = data?.summary?.up ?? services.filter((s) =>
    ["UP", "EMBEDDED"].includes((s.status || "").toUpperCase()),
  ).length;
  const downCount = data?.summary?.down ?? services.length - upCount;

  return (
    <section className="system-status-page">
      <header className="system-status-header">
        <div>
          <h1>System status</h1>
          <p className="lead muted" style={{ marginTop: 0 }}>
            Live health from Spring Boot Actuator · auto-refresh every 10 seconds
          </p>
        </div>
        <div className="system-status-actions">
          {lastOkAt && (
            <span className="muted system-status-updated">Updated {lastOkAt}</span>
          )}
          <button
            type="button"
            className="secondary"
            onClick={() => void load(true)}
            disabled={loading || refreshing}
          >
            {refreshing ? "Refreshing…" : "Refresh now"}
          </button>
        </div>
      </header>

      {error && <div className="msg error">{error}</div>}

      {loading && !data && (
        <div className="panel system-status-loading">Loading service health…</div>
      )}

      {data && (
        <>
          <div className="system-status-summary">
            <div className={`system-status-card ${statusClass(data.overall)}`}>
              <span className="system-status-card-label">Overall</span>
              <span className="system-status-card-value">{statusLabel(data.overall)}</span>
              <span className="system-status-card-meta">{data.mode}</span>
            </div>
            <div className="system-status-card status-up">
              <span className="system-status-card-label">Healthy</span>
              <span className="system-status-card-value">{upCount}</span>
              <span className="system-status-card-meta">UP / EMBEDDED</span>
            </div>
            <div className={`system-status-card ${downCount > 0 ? "status-down" : "status-up"}`}>
              <span className="system-status-card-label">Down / unreachable</span>
              <span className="system-status-card-value">{downCount}</span>
              <span className="system-status-card-meta">Needs attention</span>
            </div>
            <div className="system-status-card">
              <span className="system-status-card-label">Checked at</span>
              <span className="system-status-card-value system-status-card-value-sm">
                {formatTime(data.checkedAt)}
              </span>
              <span className="system-status-card-meta">Server clock</span>
            </div>
          </div>

          <div className="panel system-status-local">
            <div className="system-status-section-head">
              <h2>Local API (medtrack-app)</h2>
              <span className={`status-pill ${statusClass(data.local?.status)}`}>
                <span className="status-dot" aria-hidden />
                {statusLabel(data.local?.status)}
              </span>
            </div>
            <div className="system-status-local-grid">
              <div>
                <div className="system-status-field-label">Service</div>
                <div>{data.local?.name || "medtrack-app"}</div>
              </div>
              <div>
                <div className="system-status-field-label">Port</div>
                <div>{data.local?.port ?? 8090}</div>
              </div>
              <div>
                <div className="system-status-field-label">Actuator</div>
                <div>
                  <a href="/actuator/health" target="_blank" rel="noreferrer">
                    /actuator/health
                  </a>
                </div>
              </div>
            </div>
            {data.local?.components && Object.keys(data.local.components).length > 0 && (
              <div className="status-components">
                {Object.entries(data.local.components).map(([name, st]) => (
                  <span key={name} className={`status-pill ${statusClass(String(st))}`}>
                    <span className="status-dot" aria-hidden />
                    {name}: {String(st)}
                  </span>
                ))}
              </div>
            )}
          </div>

          <div className="system-status-section-head" style={{ marginTop: "1.25rem" }}>
            <h2>Microservices</h2>
            <span className="muted" style={{ fontSize: "0.9rem" }}>
              EMBEDDED = inside :8090 · UP on 8081–8086 = separate process
            </span>
          </div>

          <div className="system-status-grid">
            {services.map((s) => (
              <article
                key={s.name}
                className={`system-status-service ${statusClass(s.status)}`}
              >
                <div className="system-status-service-top">
                  <h3>{s.name}</h3>
                  <span className={`status-pill ${statusClass(s.status)}`}>
                    <span className="status-dot" aria-hidden />
                    {statusLabel(s.status)}
                  </span>
                </div>
                <dl className="system-status-service-meta">
                  <div>
                    <dt>Port</dt>
                    <dd>{s.port ?? "—"}</dd>
                  </div>
                  <div>
                    <dt>Mode</dt>
                    <dd>{s.mode || "—"}</dd>
                  </div>
                </dl>
                <p className="system-status-service-detail muted">
                  {s.detail || s.url || "No detail"}
                </p>
              </article>
            ))}
          </div>

          <div className="system-status-links muted">
            Direct links:{" "}
            <a href="/actuator/health" target="_blank" rel="noreferrer">
              /actuator/health
            </a>
            {" · "}
            <a href="/actuator/info" target="_blank" rel="noreferrer">
              /actuator/info
            </a>
            {" · "}
            <a href="/api/system/status" target="_blank" rel="noreferrer">
              /api/system/status
            </a>
          </div>
        </>
      )}
    </section>
  );
}
