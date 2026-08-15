import { Link } from "react-router-dom";
import { isLoggedIn, isPatientLoggedIn, session } from "../auth";

type Props = {
  /** Optional hamburger for sidebar layouts */
  onMenuClick?: () => void;
  showMenuButton?: boolean;
  /** Compact page title shown next to logo on inner pages */
  pageTitle?: string;
};

export default function AppHeader({
  onMenuClick,
  showMenuButton = false,
  pageTitle,
}: Props) {
  const loggedIn = isLoggedIn();
  const patient = isPatientLoggedIn();
  const username =
    (patient ? session.getPatientName() : null) ||
    session.getUsername() ||
    "";
  const homeTo = patient ? "/patient" : loggedIn ? "/" : "/login";
  const profileTo = patient ? "/patient/profile" : loggedIn ? "/" : "/login";
  const logoutTo = patient ? "/patient/logout" : "/logout";

  return (
    <header className="app-header" role="banner">
      <div className="app-header-inner">
        <div className="app-header-left">
          {showMenuButton && (
            <button
              type="button"
              className="menu-toggle app-header-menu"
              aria-label="Open menu"
              onClick={onMenuClick}
            >
              <span />
              <span />
              <span />
            </button>
          )}
          <Link to={homeTo} className="app-header-logo" aria-label="MedTrack home">
            <span className="app-header-mark" aria-hidden="true">
              M
            </span>
            <span className="app-header-brand">
              <span className="app-header-brand-name">MedTrack</span>
              <span className="app-header-brand-sub">Clinic</span>
            </span>
          </Link>
          {pageTitle && (
            <span className="app-header-page-title" aria-current="page">
              {pageTitle}
            </span>
          )}
        </div>

        <nav className="app-header-actions" aria-label="Account">
          {loggedIn ? (
            <>
              {username && (
                <span className="app-header-user" title={username}>
                  <span className="app-header-user-label">Signed in as</span>
                  <strong>{username}</strong>
                </span>
              )}
              <Link to={profileTo} className="app-header-link">
                Profile
              </Link>
              <Link to={logoutTo} className="app-header-btn app-header-btn--ghost">
                Logout
              </Link>
            </>
          ) : (
            <>
              <Link to="/patient/login" className="app-header-link">
                Patient
              </Link>
              <Link to="/login" className="app-header-btn">
                Login
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
