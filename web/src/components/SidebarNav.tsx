import { NavLink } from "react-router-dom";
import { useT } from "../i18n";
import { navIconFor } from "../navIcons";

export type SidebarNavItem = {
  to: string;
  label: string;
  end?: boolean;
  hint?: string;
};

export type SidebarNavGroup = {
  title: string;
  items: SidebarNavItem[];
};

type LinkProps = {
  to: string;
  label: string;
  hint?: string;
  end?: boolean;
  onNavigate?: () => void;
};

function NavMaterialIcon({ path }: { path: string }) {
  return (
    <span className="material-symbols-outlined nav-icon" aria-hidden="true">
      {navIconFor(path)}
    </span>
  );
}

function SidebarIconLinkInner({ to, label, end, onNavigate }: LinkProps) {
  const t = useT();
  const name = t(label);

  return (
    <NavLink to={to} end={end === true} onClick={onNavigate} title={name}>
      <NavMaterialIcon path={to} />
      <span className="nav-copy">
        <span className="nav-label">{name}</span>
      </span>
    </NavLink>
  );
}

export function SidebarIconLink(props: LinkProps) {
  return <SidebarIconLinkInner {...props} />;
}

export default function SidebarNav({
  groups,
  onNavigate,
}: {
  groups: SidebarNavGroup[];
  onNavigate?: () => void;
}) {
  return (
    <nav className="sidebar-nav">
      {groups.map((group) => (
        <div key={group.title} className="sidebar-group">
          <ul>
            {group.items.map((item) => (
              <li key={item.to}>
                <SidebarIconLinkInner
                  to={item.to}
                  label={item.label}
                  end={item.end}
                  onNavigate={onNavigate}
                />
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );
}
