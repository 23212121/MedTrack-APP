import { Link } from "react-router-dom";
import { isLoggedIn, isPatientLoggedIn, session } from "../auth";
import { useI18n, type Lang } from "../i18n";

type Props = {
  /** Optional hamburger for sidebar layouts */
  onMenuClick?: () => void;
  showMenuButton?: boolean;
  /** Compact page title shown next to logo on inner pages */
  pageTitle?: string;
  /** When set, show × Close instead of Logout (public popups). */
  closeTo?: string;
};

export default function AppHeader({
  onMenuClick,
  showMenuButton = false,
  pageTitle,
  closeTo,
}: Props) {
  const { lang, setLang, t } = useI18n();
  const loggedIn = isLoggedIn();
  const patient = isPatientLoggedIn();
  const username =
    (patient ? session.getPatientName() : null) ||
    session.getUsername() ||
    "";
  const medical = session.isMedical();
  const homeTo = patient ? "/patient" : medical ? "/medical" : loggedIn ? "/" : "/about";
  const profileTo = patient ? "/patient/profile" : medical ? "/medical" : loggedIn ? "/" : "/login";
  const logoutTo = patient ? "/patient/logout" : "/logout";

  return (
    <header className="app-header" role="banner">
      <div className="app-header-inner">
        <div className="app-header-left">
          {showMenuButton && (
            <button
              type="button"
              className="menu-toggle app-header-menu"
              aria-label={t("Open menu")}
              onClick={onMenuClick}
            >
              <span />
              <span />
              <span />
            </button>
          )}
          <Link to={homeTo} className="app-header-logo" aria-label={t("MedTrack home")}>
            <span className="app-header-mark" aria-hidden="true">
              M
            </span>
            <span className="app-header-brand">
              <span className="app-header-brand-name">MedTrack</span>
              <span className="app-header-brand-sub">{t("Clinic")}</span>
            </span>
          </Link>
          {pageTitle && (
            <span className="app-header-page-title" aria-current="page">
              {t(pageTitle)}
            </span>
          )}
        </div>

        <nav className="app-header-actions" aria-label={t("Account")}>
          <label className="app-header-lang">
            <span className="app-header-lang-label">{t("Language")}</span>
            <select
              value={lang}
              onChange={(e) => setLang(e.target.value as Lang)}
              aria-label={t("Language")}
            >
              <option value="en">English</option>
              <option value="hi">हिन्दी</option>
            </select>
          </label>
          {loggedIn ? (
            <>
              {username && (
                <span className="app-header-user" title={username}>
                  <span className="app-header-user-label">{t("Signed in as")}</span>
                  <strong>{username}</strong>
                </span>
              )}
              <Link to={profileTo} className="app-header-link">
                {t("Profile")}
              </Link>
              {closeTo ? (
                <Link to={closeTo} className="app-header-btn app-header-btn--ghost" aria-label={t("Close")}>
                  {t("× Close")}
                </Link>
              ) : (
                <Link to={logoutTo} className="app-header-btn app-header-btn--ghost">
                  {t("Logout")}
                </Link>
              )}
            </>
          ) : (
            <>
              <Link to="/about" className="app-header-link">
                {t("About")}
              </Link>
              <Link to="/about#contact" className="app-header-link">
                {t("Contact")}
              </Link>
              <Link to="/patient/login" className="app-header-link">
                {t("Patient")}
              </Link>
              <Link to="/login" className="app-header-btn">
                {t("Login")}
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
