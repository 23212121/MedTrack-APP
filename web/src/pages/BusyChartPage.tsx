import { useEffect, useState } from "react";
import { api, DayChart, DOCTOR_ID } from "../api";

function todayLocal() {
  const d = new Date();
  const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

function timeLabel(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

export default function BusyChartPage() {
  const [date, setDate] = useState(todayLocal());
  const [chart, setChart] = useState<DayChart | null>(null);
  const [error, setError] = useState("");

  async function load(d = date) {
    setError("");
    try {
      setChart(await api.chart(DOCTOR_ID, d));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load chart");
    }
  }

  useEffect(() => {
    load();
  }, [date]);

  return (
    <section>
      <h1>Doctor schedules chart</h1>
      <p className="lead">
        Working hours, booked visits, and busy/leave blocks with utilization for the day.
      </p>
      {error && <div className="msg error">{error}</div>}
      <div className="panel">
        <div className="row">
          <label>
            Date
            <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </label>
          <button type="button" className="secondary" onClick={() => load()}>
            Refresh
          </button>
        </div>
        {chart && (
          <>
            <div className="stats">
              <div className="stat">
                <strong>{chart.availableMinutes}m</strong>
                <span>Working</span>
              </div>
              <div className="stat">
                <strong>{chart.bookedMinutes}m</strong>
                <span>Booked</span>
              </div>
              <div className="stat">
                <strong>{chart.busyMinutes}m</strong>
                <span>Busy / leave</span>
              </div>
              <div className="stat">
                <strong>{chart.utilizationPercent}%</strong>
                <span>Utilization</span>
              </div>
            </div>
            <div className="chart">
              {chart.blocks.map((b, i) => (
                <div className="chart-bar" key={`${b.kind}-${i}`}>
                  <div>
                    {timeLabel(b.startsAt)} – {timeLabel(b.endsAt)}
                  </div>
                  <div className="bar-track">
                    <div className={`bar-fill ${b.kind}`} style={{ width: "100%" }}>
                      {b.label}
                    </div>
                  </div>
                  <div>
                    <span className={`badge ${b.kind}`}>{b.kind}</span>
                    {b.status ? ` · ${b.status}` : ""}
                  </div>
                </div>
              ))}
              {chart.blocks.length === 0 && <p>No schedule blocks for this day.</p>}
            </div>
          </>
        )}
      </div>
    </section>
  );
}
