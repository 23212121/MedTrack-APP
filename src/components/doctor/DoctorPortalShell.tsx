"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { ReactNode, useState } from "react";

const nav = [
  { href: "/doctor/home", label: "Home", icon: "⌂" },
  { href: "/doctor/patients", label: "Patients", icon: "♟" },
  { href: "/doctor/suggestions", label: "Suggestions", icon: "✦" },
];

type Props = {
  doctorName: string;
  clinicName: string;
  children: ReactNode;
};

export function DoctorPortalShell({ doctorName, clinicName, children }: Props) {
  const pathname = usePathname();
  const router = useRouter();
  const [menuOpen, setMenuOpen] = useState(false);

  async function logout() {
    await fetch("/api/auth/logout", { method: "POST" });
    router.push("/login");
    router.refresh();
  }

  return (
    <div className="ddash">
      <header className="ddash-top">
        <div className="ddash-brand">
          <div className="ddash-logo" aria-hidden>
            <span>+</span>
          </div>
          <div>
            <strong>{clinicName}</strong>
            <em>Hospital Care</em>
          </div>
        </div>
        <div className="ddash-top-title">Doctor Dashboard</div>
        <div className="ddash-user">
          <button
            type="button"
            className="ddash-user-btn"
            onClick={() => setMenuOpen((v) => !v)}
            aria-expanded={menuOpen}
          >
            <span className="ddash-avatar">{doctorName.slice(0, 1).toUpperCase()}</span>
            <span>{doctorName.startsWith("Dr") ? doctorName : `Dr. ${doctorName}`}</span>
            <span className="ddash-caret">▾</span>
          </button>
          {menuOpen ? (
            <div className="ddash-dropdown">
              <Link href="/doctor/home" onClick={() => setMenuOpen(false)}>
                Dashboard
              </Link>
              <Link href="/doctor/queue" onClick={() => setMenuOpen(false)}>
                Live queue
              </Link>
              <button type="button" onClick={logout}>
                Sign out
              </button>
            </div>
          ) : null}
        </div>
      </header>

      <div className="ddash-body">
        <aside className="ddash-side">
          <nav>
            {nav.map((item) => {
              const active =
                pathname === item.href || pathname.startsWith(item.href + "/");
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={active ? "active" : undefined}
                >
                  <span className="ddash-nav-icon" aria-hidden>
                    {item.icon}
                  </span>
                  {item.label}
                </Link>
              );
            })}
          </nav>
          <Link href="/doctor/queue" className="ddash-side-foot">
            Open live queue →
          </Link>
        </aside>
        <section className="ddash-main">{children}</section>
      </div>
    </div>
  );
}
