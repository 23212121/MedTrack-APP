import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useState } from "react";
import { isLoggedIn, isPatientLoggedIn, isPublicPath, popupClosePath } from "./auth";
import { session } from "./dl/MedTrackSession";
import { useT } from "./i18n";
import AppHeader from "./components/AppHeader";
import SidebarNav, { SidebarIconLink } from "./components/SidebarNav";
import { knownPaths, doctorNavGroups, navGroups, pageTitles } from "./nav";
import { patientKnownPaths, patientNavGroups, patientPageTitles } from "./patientNav";
import { medicalKnownPaths, medicalNavGroups, medicalPageTitles } from "./medicalNav";
import { labKnownPaths, labNavGroups, labPageTitles } from "./labNav";
import AboutPage from "./pages/AboutPage";
import HomePage from "./pages/HomePage";
import SchedulesPage from "./pages/SchedulesPage";
import AvailabilityPage from "./pages/AvailabilityPage";
import DoctorAvailabilityDatesPage from "./pages/DoctorAvailabilityDatesPage";
import CheckInPage from "./pages/CheckInPage";
import BusyChartPage from "./pages/BusyChartPage";
import FeesPage from "./pages/FeesPage";
import DoctorQueuePage from "./pages/DoctorQueuePage";
import NotificationsPage from "./pages/NotificationsPage";
import BookingsPage from "./pages/BookingsPage";
import PatientBookingPage from "./pages/PatientBookingPage";
import PatientQueuePage from "./pages/PatientQueuePage";
import PatientTrackPage from "./pages/PatientTrackPage";
import DownloadFilePage from "./pages/DownloadFilePage";
import DoctorRegistrationPage from "./pages/DoctorRegistrationPage";
import HospitalRegistrationPage from "./pages/HospitalRegistrationPage";
import LoginPage from "./pages/LoginPage";
import ForgotPasswordPage from "./pages/ForgotPasswordPage";
import LogoutPage from "./pages/LogoutPage";
import NotFoundPage from "./pages/NotFoundPage";
import HrmShell from "./pages/hrm/HrmShell";
import HrmHomePage from "./pages/hrm/HrmHomePage";
import HrmAttendancePage from "./pages/hrm/HrmAttendancePage";
import HrmPlaceholderPage from "./pages/hrm/HrmPlaceholderPage";
import HrmLeavePage from "./pages/hrm/HrmLeavePage";
import HrmInboxPage from "./pages/hrm/HrmInboxPage";
import HrmApproverPage from "./pages/hrm/HrmApproverPage";
import HrmHolidayPage from "./pages/hrm/HrmHolidayPage";
import PatientLoginPage from "./pages/patient/PatientLoginPage";
import PatientRegisterPage from "./pages/patient/PatientRegisterPage";
import PatientDashboardPage from "./pages/patient/PatientDashboardPage";
import PatientBookingPortalPage from "./pages/patient/PatientBookingPortalPage";
import PatientStatusPage from "./pages/patient/PatientStatusPage";
import PatientChatPage from "./pages/patient/PatientChatPage";
import PatientReportsPage from "./pages/patient/PatientReportsPage";
import PatientProfilePage from "./pages/patient/PatientProfilePage";
import PatientDocumentsPage from "./pages/PatientDocumentsPage";
import CheckDocumentsPage from "./pages/CheckDocumentsPage";
import HospitalPatientListPage from "./pages/HospitalPatientListPage";
import DoctorPortalPage from "./pages/DoctorPortalPage";
import DoctorPatientQueuePage from "./pages/DoctorPatientQueuePage";
import DoctorTimeSlotsPage from "./pages/DoctorTimeSlotsPage";
import SystemStatusPage from "./pages/SystemStatusPage";
import MedicineOrdersPage from "./pages/medicine/MedicineOrdersPage";
import MedicalDashboardPage from "./pages/medicine/MedicalDashboardPage";
import MedicalCheckOrderPage from "./pages/medicine/MedicalCheckOrderPage";
import MedicalStoresPage from "./pages/medicine/MedicalStoresPage";
import MedicalNotificationsPage from "./pages/medicine/MedicalNotificationsPage";
import EmergencyServicePage from "./pages/emergency/EmergencyServicePage";
import LabDashboardPage from "./pages/lab/LabDashboardPage";
import LabPatientsPage from "./pages/lab/LabPatientsPage";
import LabReportsPage from "./pages/lab/LabReportsPage";
import LabsPage from "./pages/lab/LabsPage";
import LabRegisterPage from "./pages/lab/LabRegisterPage";

export default function App() {
  const t = useT();
  const location = useLocation();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const loggedIn = isLoggedIn();
  const patientLoggedIn = isPatientLoggedIn();
  const isPatientRoute = location.pathname === "/patient" || location.pathname.startsWith("/patient/");
  const hospitalLabel =
    session.getHospitalName() ||
    (session.getHospitalId() ? `Hospital #${session.getHospitalId()}` : "");
  /** Logged-in hospital/doctor use workspace shell for /queue (full page, not public popup). */
  const queueInWorkspace =
    loggedIn &&
    !patientLoggedIn &&
    location.pathname === "/queue";
  const isPublic = isPublicPath(location.pathname) && !queueInWorkspace;
  const isMedicalUser = session.isMedical();
  const isMedicalRoute =
    location.pathname === "/medical" || location.pathname.startsWith("/medical/");
  const isLabUser = session.isLab();
  const isLabRoute = location.pathname === "/lab" || location.pathname.startsWith("/lab/");
  const isUnknown =
    !knownPaths.has(location.pathname) &&
    !patientKnownPaths.has(location.pathname) &&
    !medicalKnownPaths.has(location.pathname) &&
    !labKnownPaths.has(location.pathname) &&
    !isPublic;
  const title =
    location.pathname === "/"
      ? "Dashboard"
      : (pageTitles[location.pathname] ?? "MedTrack Clinic");
  const isDashboard = location.pathname === "/" || location.pathname === "/doctor-portal";
  const isHrm = location.pathname === "/hrm" || location.pathname.startsWith("/hrm/");
  const isDoctorUser = session.isDoctor();
  const sidebarGroups = isDoctorUser ? doctorNavGroups : navGroups;

  // Public screens — outside app shell (no login)
  if (isPublic) {
    if (patientLoggedIn && (location.pathname === "/patient/login" || location.pathname === "/patient/register")) {
      return <Navigate to="/patient" replace />;
    }
    if (loggedIn && !patientLoggedIn && location.pathname === "/login") {
      return (
        <Navigate
          to={session.isLab() ? "/lab" : session.isMedical() ? "/medical" : "/"}
          replace
        />
      );
    }
    const isBook =
      location.pathname === "/book" ||
      location.pathname.startsWith("/book/");
    const isQueue = location.pathname === "/queue";
    const isTrack = location.pathname === "/track";
    const isInfo = location.pathname === "/about";
    const widePublic =
      location.pathname === "/hospital-register" ||
      location.pathname === "/lab-register" ||
      isQueue;
    const fullBleed = isBook || isTrack;
    const isLogin =
      location.pathname === "/login" ||
      location.pathname === "/patient/login" ||
      location.pathname === "/patient/register";
    const isPopupPage =
      isBook || isTrack || widePublic || (loggedIn && isLogin);
    return (
      <div
        className={`app-shell app-shell--auth${fullBleed ? " app-shell--book" : ""}${
          isLogin ? " app-shell--login" : ""
        }${isInfo ? " app-shell--info" : ""}`}
      >
        <AppHeader closeTo={isPopupPage && !isQueue ? popupClosePath() : undefined} />
        <main
          className={`content content--auth${
            isInfo
              ? " content--info"
              : widePublic
                ? " content--auth-wide"
                : fullBleed
                  ? " content--auth-book"
                  : isLogin
                    ? " content--login"
                    : " content--error"
          }`}
        >
          <Routes>
            <Route path="/about" element={<AboutPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/forgot-password" element={<ForgotPasswordPage />} />
            <Route path="/logout" element={<LogoutPage />} />
            <Route path="/hospital-register" element={<HospitalRegistrationPage />} />
            <Route path="/book" element={<PatientBookingPage />} />
            <Route path="/book/:hospitalId" element={<PatientBookingPage />} />
            <Route path="/queue" element={<PatientQueuePage />} />
            <Route path="/track" element={<PatientTrackPage />} />
            <Route path="/download-file" element={<DownloadFilePage />} />
            <Route path="/patient/login" element={<PatientLoginPage />} />
            <Route path="/patient/register" element={<PatientRegisterPage />} />
            <Route path="/lab-register" element={<LabRegisterPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </main>
      </div>
    );
  }

  // Patient portal (logged-in patients)
  if (isPatientRoute) {
    if (!patientLoggedIn) {
      return <Navigate to="/patient/login" replace state={{ from: location.pathname }} />;
    }

    const patientTitle =
      location.pathname === "/patient"
        ? "Dashboard"
        : (patientPageTitles[location.pathname] ?? "Patient Portal");
    const patientName = session.getPatientName() || session.getUsername();

    return (
      <div className={`app-shell app-shell--sidebar app-shell--patient${sidebarOpen ? " is-open" : ""}`}>
        <AppHeader
          showMenuButton
          onMenuClick={() => setSidebarOpen(true)}
          pageTitle={patientTitle}
        />
        {sidebarOpen && (
          <button
            type="button"
            className="sidebar-backdrop"
            aria-label={t("Close menu")}
            onClick={() => setSidebarOpen(false)}
          />
        )}

        <aside className="sidebar sidebar--patient" aria-label={t("Patient navigation")}>
          <div className="sidebar-brand">
            <span className="sidebar-mark" aria-hidden="true">
              P
            </span>
            <div>
              <div className="brand">MedTrack</div>
              <p className="sidebar-tagline">{t("Patient portal")}</p>
            </div>
          </div>

          <SidebarNav groups={patientNavGroups} onNavigate={() => setSidebarOpen(false)} />

          <div className="sidebar-footer">
            <p className="sidebar-footer-meta">
              <strong>{patientName}</strong>
              <br />
              {session.getPatientPhone() || session.getPatientId()}
            </p>
            <SidebarIconLink
              to="/patient/profile"
              label="Profile"
              onNavigate={() => setSidebarOpen(false)}
            />
            <SidebarIconLink
              to="/patient/logout"
              label="Sign out"
              onNavigate={() => setSidebarOpen(false)}
            />
          </div>
        </aside>

        <div className="workspace">
          <main
            className={`content content--workspace content--patient${
              location.pathname === "/patient" ? " content--dashboard" : ""
            }`}
          >
            <Routes>
              <Route path="/patient" element={<PatientDashboardPage />} />
              <Route path="/patient/emergency" element={<EmergencyServicePage />} />
              <Route path="/patient/booking" element={<PatientBookingPortalPage />} />
              <Route path="/patient/status" element={<PatientStatusPage />} />
              <Route path="/patient/chat" element={<PatientChatPage />} />
              <Route path="/patient/reports" element={<PatientReportsPage />} />
              <Route path="/patient/medicine-orders" element={<MedicineOrdersPage title="Medicine orders" />} />
              <Route path="/patient/notifications" element={<MedicalNotificationsPage />} />
              <Route path="/patient/profile" element={<PatientProfilePage />} />
              <Route path="/patient/logout" element={<LogoutPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </main>
        </div>
      </div>
    );
  }

  // App (dashboard + menus) only after login
  if (!loggedIn || patientLoggedIn) {
    if (patientLoggedIn) {
      return <Navigate to="/patient" replace />;
    }
    if (location.pathname === "/") {
      return <Navigate to="/about" replace />;
    }
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (isLabUser && !isLabRoute && location.pathname !== "/logout") {
    return <Navigate to="/lab" replace />;
  }

  if (isLabUser && isLabRoute) {
    const labTitle =
      location.pathname === "/lab" ? "Dashboard" : (labPageTitles[location.pathname] ?? "Lab");
    return (
      <div className={`app-shell app-shell--sidebar${sidebarOpen ? " is-open" : ""}`}>
        <AppHeader
          showMenuButton
          onMenuClick={() => setSidebarOpen(true)}
          pageTitle={labTitle}
        />
        {sidebarOpen && (
          <button
            type="button"
            className="sidebar-backdrop"
            aria-label={t("Close menu")}
            onClick={() => setSidebarOpen(false)}
          />
        )}
        <aside className="sidebar" aria-label={t("Lab technician navigation")}>
          <div className="sidebar-brand">
            <span className="sidebar-mark" aria-hidden="true">
              Lb
            </span>
            <div>
              <div className="brand">MedTrack</div>
              <p className="sidebar-tagline">Lab technician</p>
            </div>
          </div>
          <SidebarNav groups={labNavGroups} onNavigate={() => setSidebarOpen(false)} />
          <div className="sidebar-footer">
            <p className="sidebar-footer-meta">
              <strong>{session.getUsername()}</strong>
              <br />
              {session.getHospitalId()
                ? t("Hospital {id}", { id: String(session.getHospitalId()) })
                : "Independent lab"}
            </p>
            <SidebarIconLink to="/logout" label="Logout" onNavigate={() => setSidebarOpen(false)} />
          </div>
        </aside>
        <div className="workspace">
          <main
            className={`content content--workspace${
              location.pathname === "/lab" ? " content--dashboard" : ""
            }`}
          >
            <Routes>
              <Route path="/lab" element={<LabDashboardPage />} />
              <Route path="/lab/patients" element={<LabPatientsPage />} />
              <Route path="/lab/uploads" element={<LabReportsPage />} />
              <Route path="/logout" element={<LogoutPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </main>
        </div>
      </div>
    );
  }

  if (isMedicalUser && !isMedicalRoute && location.pathname !== "/logout") {
    return <Navigate to="/medical" replace />;
  }

  if (isMedicalUser && isMedicalRoute) {
    const medicalTitle =
      location.pathname === "/medical"
        ? "Dashboard"
        : (medicalPageTitles[location.pathname] ?? "Medical store");
    return (
      <div className={`app-shell app-shell--sidebar${sidebarOpen ? " is-open" : ""}`}>
        <AppHeader
          showMenuButton
          onMenuClick={() => setSidebarOpen(true)}
          pageTitle={medicalTitle}
        />
        {sidebarOpen && (
          <button
            type="button"
            className="sidebar-backdrop"
            aria-label={t("Close menu")}
            onClick={() => setSidebarOpen(false)}
          />
        )}
        <aside className="sidebar" aria-label={t("Medical store navigation")}>
          <div className="sidebar-brand">
            <span className="sidebar-mark" aria-hidden="true">
              Rx
            </span>
            <div>
              <div className="brand">MedTrack</div>
              <p className="sidebar-tagline">{t("Medical store")}</p>
            </div>
          </div>
          <SidebarNav groups={medicalNavGroups} onNavigate={() => setSidebarOpen(false)} />
          <div className="sidebar-footer">
            <p className="sidebar-footer-meta">
              <strong>{session.getUsername()}</strong>
              <br />
              {t("Hospital {id}", { id: String(session.getHospitalId() || "") })}
              <br />
              {t("Store {id}", { id: String(session.getMedicalStoreId() || "") })}
            </p>
            <SidebarIconLink to="/logout" label="Logout" onNavigate={() => setSidebarOpen(false)} />
          </div>
        </aside>
        <div className="workspace">
          <main
            className={`content content--workspace${
              location.pathname === "/medical" ? " content--dashboard" : ""
            }`}
          >
            <Routes>
              <Route path="/medical" element={<MedicalDashboardPage />} />
              <Route path="/medical/emergency" element={<EmergencyServicePage />} />
              <Route path="/medical/orders/check" element={<MedicalCheckOrderPage />} />
              <Route path="/medical/orders" element={<MedicineOrdersPage title="All orders" />} />
              <Route
                path="/medical/orders/new"
                element={<MedicineOrdersPage defaultStatus="PENDING" title="New orders" />}
              />
              <Route
                path="/medical/orders/in-process"
                element={<MedicineOrdersPage defaultStatus="IN_PROCESS" title="In process" />}
              />
              <Route
                path="/medical/orders/waiting"
                element={
                  <MedicineOrdersPage
                    defaultStatus="WAITING_FOR_PATIENT_APPROVAL"
                    title="Waiting approval"
                  />
                }
              />
              <Route
                path="/medical/orders/ready"
                element={<MedicineOrdersPage defaultStatus="MEDICINE_READY" title="Medicine ready" />}
              />
              <Route
                path="/medical/orders/completed"
                element={<MedicineOrdersPage defaultStatus="COMPLETED" title="Completed orders" />}
              />
              <Route
                path="/medical/orders/canceled"
                element={<MedicineOrdersPage defaultStatus="CANCELED" title="Canceled orders" />}
              />
              <Route path="/medical/notifications" element={<MedicalNotificationsPage />} />
              <Route path="/logout" element={<LogoutPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </main>
        </div>
      </div>
    );
  }

  if (isDoctorUser && location.pathname === "/") {
    return <Navigate to="/doctor-portal" replace />;
  }

  if (isUnknown) {
    return (
      <div className="app-shell app-shell--auth">
        <AppHeader />
        <main className="content content--auth content--error">
          <Routes>
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </main>
      </div>
    );
  }

  return (
    <div className={`app-shell app-shell--sidebar${sidebarOpen ? " is-open" : ""}`}>
      <AppHeader
        showMenuButton
        onMenuClick={() => setSidebarOpen(true)}
        pageTitle={isHrm ? "HRM" : title}
      />
      {sidebarOpen && (
        <button
          type="button"
          className="sidebar-backdrop"
          aria-label={t("Close menu")}
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <aside className="sidebar" aria-label={t("Main navigation")}>
        <div className="sidebar-brand">
          <span className="sidebar-mark" aria-hidden="true">
            M
          </span>
          <div>
            <div className="brand">MedTrack</div>
            <p className="sidebar-tagline">{t("Clinic visit ops")}</p>
          </div>
        </div>

        <SidebarNav groups={sidebarGroups} onNavigate={() => setSidebarOpen(false)} />

        <div className="sidebar-footer">
          {hospitalLabel && (
            <p className="sidebar-footer-meta">
              <strong>{hospitalLabel}</strong>
              <br />
              {t("Hospital {id}", { id: String(session.getHospitalId() || "") })}
              <br />
              {session.getLoginType() === "USER" ? t("Doctor") : t("Admin")} ·{" "}
              {session.getUserId() || session.getUsername()}
            </p>
          )}
          {!hospitalLabel && <p className="sidebar-footer-meta">{t("MedTrack session")}</p>}
          <SidebarIconLink to="/logout" label="Logout" onNavigate={() => setSidebarOpen(false)} />
        </div>
      </aside>

      <div className="workspace">
        <main
          className={`content content--workspace${
            isDashboard ? " content--dashboard" : ""
          }${isHrm ? " content--hrm" : ""}`}
        >
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/system-status" element={<SystemStatusPage />} />
            <Route path="/doctor-register" element={<DoctorRegistrationPage />} />
            <Route path="/bookings" element={<BookingsPage />} />
            <Route path="/emergency" element={<EmergencyServicePage />} />
            <Route path="/queue" element={<PatientQueuePage />} />
            <Route path="/schedules" element={<SchedulesPage />} />
            <Route path="/availability" element={<AvailabilityPage />} />
            <Route path="/doctor-availability" element={<DoctorAvailabilityDatesPage />} />
            <Route path="/check-in" element={<CheckInPage />} />
            <Route path="/chart" element={<BusyChartPage />} />
            <Route path="/fees" element={<FeesPage />} />
            <Route path="/doctor" element={<DoctorQueuePage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/patient-documents" element={<PatientDocumentsPage />} />
            <Route path="/check-document" element={<CheckDocumentsPage />} />
            <Route path="/patient-list" element={<HospitalPatientListPage />} />
            <Route path="/doctor-portal" element={<DoctorPortalPage />} />
            <Route path="/doctor-portal/emergency" element={<EmergencyServicePage />} />
            <Route path="/doctor-portal/queue" element={<DoctorPatientQueuePage />} />
            <Route path="/doctor-portal/time-slots" element={<DoctorTimeSlotsPage />} />
            <Route
              path="/doctor-portal/availability"
              element={<DoctorAvailabilityDatesPage lockedDoctorId={session.getUserId()} />}
            />
            <Route
              path="/doctor-portal/medicine-orders"
              element={<MedicineOrdersPage title="Medicine orders" />}
            />
            <Route path="/medicine-orders" element={<MedicineOrdersPage title="Medicine orders" />} />
            <Route path="/medical-stores" element={<MedicalStoresPage />} />
            <Route path="/labs" element={<LabsPage />} />
            <Route path="/hrm" element={<HrmShell />}>
              <Route index element={<HrmHomePage />} />
              <Route path="attendance" element={<HrmAttendancePage />} />
              <Route path="leave" element={<HrmLeavePage />} />
              <Route path="inbox" element={<HrmInboxPage />} />
              <Route
                path="performance"
                element={
                  <HrmPlaceholderPage
                    title="Performance"
                    text="Performance reviews will appear here."
                  />
                }
              />
              <Route
                path="apps"
                element={
                  <HrmPlaceholderPage
                    title="Apps"
                    text="HR apps and shortcuts will appear here."
                  />
                }
              />
              <Route path="approver" element={<HrmApproverPage />} />
              <Route path="holidays" element={<HrmHolidayPage />} />
            </Route>
            <Route path="/logout" element={<LogoutPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </main>
      </div>
    </div>
  );
}
