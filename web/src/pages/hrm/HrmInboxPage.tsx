import { useCallback, useEffect, useMemo, useState } from "react";
import {
  InboxCategoryCount,
  InboxItem,
  acknowledgeInboxItem,
  downloadInboxItem,
  getHrmInbox,
} from "../../api";
import { toast } from "../../toast";

type ViewTab = "action" | "notifications" | "archive";

function initials(name?: string) {
  if (!name) return "AK";
  return name
    .split(/\s+/)
    .map((p) => p[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
}

function timeAgo(iso?: string) {
  if (!iso) return "";
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return "";
  const days = Math.floor((Date.now() - t) / 86400000);
  if (days < 1) return "Today";
  if (days < 30) return `${days} day${days === 1 ? "" : "s"} ago`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months} month${months === 1 ? "" : "s"} ago`;
  const years = Math.floor(months / 12);
  return `${years} year${years === 1 ? "" : "s"} ago`;
}

function formatWhen(iso?: string) {
  if (!iso) return "";
  try {
    return new Date(iso).toLocaleString(undefined, {
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return iso;
  }
}

function categoryLabel(cat: string) {
  switch ((cat || "").toUpperCase()) {
    case "PERFORMANCE":
      return "Performance";
    case "LETTER_RELEASE":
      return "Letter release";
    case "SALARY_INCREMENT":
      return "Salary increment";
    default:
      return "Documents";
  }
}

export default function HrmInboxPage() {
  const [view, setView] = useState<ViewTab>("action");
  const [category, setCategory] = useState<string>("Documents");
  const [items, setItems] = useState<InboxItem[]>([]);
  const [counts, setCounts] = useState<InboxCategoryCount>({});
  const [pendingCount, setPendingCount] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [q, setQ] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getHrmInbox(view);
      setItems(data.items || []);
      setCounts(data.categoryCounts || {});
      setPendingCount(data.pendingCount || 0);
      setSelectedId((prev) => {
        if (prev && data.items?.some((i) => i.id === prev)) return prev;
        return data.items?.[0]?.id ?? null;
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load inbox");
    } finally {
      setLoading(false);
    }
  }, [view]);

  useEffect(() => {
    void load();
  }, [load]);

  const filtered = useMemo(() => {
    let rows = items;
    if (category && category !== "Documents" && category !== "ALL") {
      const want = category.toLowerCase();
      rows = rows.filter((i) => categoryLabel(i.category).toLowerCase() === want);
    }
    if (q.trim()) {
      const s = q.trim().toLowerCase();
      rows = rows.filter(
        (i) =>
          i.employeeName?.toLowerCase().includes(s) ||
          i.documentName?.toLowerCase().includes(s) ||
          i.title?.toLowerCase().includes(s),
      );
    }
    return rows;
  }, [items, category, q]);

  const selected = filtered.find((i) => i.id === selectedId) ?? filtered[0] ?? null;

  const pendingTasks = useMemo(() => {
    return [
      { key: "Documents", label: "Documents", count: pendingCount },
      {
        key: "Performance",
        label: "Performance",
        count: Number(counts.Performance ?? 0),
      },
      {
        key: "Letter release",
        label: "Letter release",
        count: Number(counts["Letter release"] ?? 0),
      },
      {
        key: "Salary increment",
        label: "Salary increment",
        count: Number(counts["Salary increment"] ?? 0),
      },
    ].filter((t) => t.count > 0 || t.key === "Documents");
  }, [counts, pendingCount]);

  async function onAcknowledge() {
    if (!selected) return;
    setBusy(true);
    try {
      await acknowledgeInboxItem(selected.id);
      toast.success("Inbox item acknowledged.");
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Acknowledge failed");
    } finally {
      setBusy(false);
    }
  }

  async function onDownload() {
    if (!selected) return;
    setBusy(true);
    try {
      await downloadInboxItem(selected.id);
      if (selected.fileUrl) {
        window.open(selected.fileUrl, "_blank", "noopener,noreferrer");
      }
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Download failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="hrm-inbox">
      <div className="hrm-inbox-tabs">
        <button
          type="button"
          className={view === "action" ? "is-active" : undefined}
          onClick={() => setView("action")}
        >
          Take Action ({pendingCount})
        </button>
        <button
          type="button"
          className={view === "notifications" ? "is-active" : undefined}
          onClick={() => setView("notifications")}
        >
          Notifications
        </button>
        <button
          type="button"
          className={view === "archive" ? "is-active" : undefined}
          onClick={() => setView("archive")}
        >
          Archive
        </button>
      </div>

      {error && <p className="error">{error}</p>}
      {loading && <p className="lead">Loading inbox…</p>}

      <div className="hrm-inbox-grid">
        <aside className="hrm-inbox-pending panel">
          <h3>Pending tasks</h3>
          <ul>
            {pendingTasks.map((t) => (
              <li key={t.key}>
                <button
                  type="button"
                  className={category === t.key ? "is-active" : undefined}
                  onClick={() => setCategory(t.key)}
                >
                  <span>{t.label}</span>
                  <strong>({t.count})</strong>
                </button>
              </li>
            ))}
          </ul>
        </aside>

        <section className="hrm-inbox-list panel">
          <div className="hrm-inbox-list-head">
            <h3>{category}</h3>
            <input
              type="search"
              placeholder="Search by employee name, document name"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </div>
          {filtered.length === 0 ? (
            <p className="hrm-muted">No documents in this view.</p>
          ) : (
            <ul className="hrm-inbox-items">
              {filtered.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    className={
                      selected?.id === item.id ? "hrm-inbox-item is-active" : "hrm-inbox-item"
                    }
                    onClick={() => setSelectedId(item.id)}
                  >
                    <span className="hrm-inbox-avatar">{initials(item.employeeName)}</span>
                    <span className="hrm-inbox-item-body">
                      <span className="hrm-inbox-item-meta">
                        <strong>{item.employeeName}</strong>
                        <em>{timeAgo(item.requestedAt || item.creationDate)}</em>
                      </span>
                      <span className="hrm-inbox-item-title">{item.title}</span>
                      <span className="hrm-inbox-item-action">{item.actionRequired}</span>
                      <span className="hrm-inbox-item-cat">{categoryLabel(item.category)}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="hrm-inbox-detail panel">
          {!selected ? (
            <p className="hrm-muted">Select a document to view details.</p>
          ) : (
            <>
              <header className="hrm-inbox-detail-head">
                <span className="hrm-inbox-avatar lg">{initials(selected.employeeName)}</span>
                <div>
                  <h3>{selected.employeeName}</h3>
                  <p>
                    Requested by {selected.requestedBy || "HR"} on{" "}
                    {formatWhen(selected.requestedAt || selected.creationDate)}
                  </p>
                </div>
              </header>
              <div className="hrm-inbox-detail-body">
                <p>Hello {selected.employeeName},</p>
                <p>
                  {selected.employeeName}&apos;s{" "}
                  <strong>{selected.documentName}</strong> requires your{" "}
                  <strong>Acknowledgement</strong>.
                </p>
                {(selected.category === "PERFORMANCE" ||
                  selected.category === "LETTER_RELEASE" ||
                  selected.category === "SALARY_INCREMENT") && (
                  <p className="hrm-muted">
                    Category: {categoryLabel(selected.category)}
                  </p>
                )}
              </div>
              {selected.status === "PENDING" && (
                <div className="hrm-inbox-detail-actions">
                  <button
                    type="button"
                    className="btn primary hrm-request-btn"
                    disabled={busy}
                    onClick={() => void onAcknowledge()}
                  >
                    Continue to Acknowledge
                  </button>
                  <button
                    type="button"
                    className="btn"
                    disabled={busy}
                    onClick={() => void onDownload()}
                  >
                    Download
                  </button>
                </div>
              )}
              <div className="hrm-inbox-activity">
                <h4>Activity</h4>
                <div className="hrm-inbox-activity-row">
                  <span className="hrm-inbox-avatar sm">
                    {initials(selected.requestedBy || "HR")}
                  </span>
                  <p>
                    {selected.requestedBy || "HR"} requested for your acknowledgement on{" "}
                    {formatWhen(selected.requestedAt || selected.creationDate)}
                  </p>
                </div>
                {selected.acknowledgedAt && (
                  <div className="hrm-inbox-activity-row">
                    <span className="hrm-inbox-avatar sm">
                      {initials(selected.employeeName)}
                    </span>
                    <p>
                      Acknowledged on {formatWhen(selected.acknowledgedAt)} · {selected.status}
                    </p>
                  </div>
                )}
              </div>
            </>
          )}
        </section>
      </div>
    </div>
  );
}
