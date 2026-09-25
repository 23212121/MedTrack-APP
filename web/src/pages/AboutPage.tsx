import { FormEvent, useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { submitContactInquiry } from "../api";
import { CLINICAL_DEPARTMENTS } from "../clinicalDepartments";
import { useT } from "../i18n";
import { toast } from "../toast";

const NAV_LINKS = [
  { href: "#about", label: "About" },
  { href: "#modules", label: "Modules" },
  { href: "#journey", label: "Visit journey" },
  { href: "#roles", label: "Who it is for" },
  { href: "#contact", label: "Contact" },
];

const AUDIENCES = [
  {
    title: "Hospitals & clinics",
    text: "Register a hospital, manage doctors, bookings, check-in, documents, pharmacy links, and HR from one operations desk.",
  },
  {
    title: "Doctors",
    text: "Open today’s queue, start or complete consults, mark delay, set weekly time slots, and place medicine orders for patients.",
  },
  {
    title: "Patients",
    text: "Book or track without login, or use the patient portal for status, chat, reports, prescriptions, and medicine orders.",
  },
  {
    title: "Medical stores",
    text: "Accept prescription orders, quote amounts, wait for patient approval, and mark medicine ready for pickup or delivery.",
  },
  {
    title: "Front desk & HR",
    text: "Search patients by name or phone, book in under a minute, issue tokens, and run attendance, leave, and holidays.",
  },
];

const MODULES = [
  {
    group: "Getting started",
    title: "Hospital registration",
    text: "Onboard a hospital with contact, departments, admin login, and a unique hospital ID starting from 10001.",
    to: "/hospital-register",
  },
  {
    group: "Getting started",
    title: "Doctor registration",
    text: "Doctors self-register with personal, contact, professional, clinic, identity, and bank details.",
    to: "/login",
  },
  {
    group: "Clinic ops",
    title: "Appointments & bookings",
    text: "Create visits from the desk or public book page. Voice booking in English or Hindi fills the form by speaking.",
    to: "/book",
  },
  {
    group: "Clinic ops",
    title: "Schedules & availability",
    text: "Weekly working hours plus busy, leave, and blocked blocks so slots stay honest.",
    to: "/login",
  },
  {
    group: "Clinic ops",
    title: "Check-in & tokens",
    text: "When a patient arrives, staff check in and issue a queue token. SMS and email go out with the slip number.",
    to: "/login",
  },
  {
    group: "Clinic ops",
    title: "Doctor queue",
    text: "Live consult list: start, mark +15 minutes late, complete, and write clinical notes plus a patient-safe summary.",
    to: "/login",
  },
  {
    group: "Patient",
    title: "Public book, track & queue",
    text: "Book without an account, track an appointment by mobile number, and see wait times on the public queue.",
    to: "/book",
  },
  {
    group: "Patient",
    title: "Patient portal",
    text: "Dashboard, booking, live status, hospital/doctor chat, lab reports, medicine orders, and profile.",
    to: "/patient/login",
  },
  {
    group: "Patient",
    title: "Documents & reports",
    text: "Hospitals upload test files; patients view reports and prescriptions tied to their visits.",
    to: "/patient/login",
  },
  {
    group: "Pharmacy",
    title: "Medicine orders",
    text: "Upload a prescription, send it to a registered medical store, approve the quoted amount, and track ready/completed status.",
    to: "/patient/login",
  },
  {
    group: "Pharmacy",
    title: "Medical stores",
    text: "Pharmacy login with order buckets: new, in process, waiting approval, medicine ready, completed, canceled.",
    to: "/login",
  },
  {
    group: "Insights",
    title: "Busy chart & fees",
    text: "See working vs booked vs busy time. Default consult is INR 500 for 15 minutes; overtime is INR 200 per extra 15-minute block.",
    to: "/login",
  },
  {
    group: "Insights",
    title: "Notifications",
    text: "Journey events go out on SMS, email, and in-app: booking, reminders, check-in, delay, you’re next, start, complete, overtime.",
    to: "/login",
  },
  {
    group: "People",
    title: "HRM",
    text: "Attendance clock in/out, leave, work from home, inbox, approver, holidays, and a hospital home feed.",
    to: "/login",
  },
];

const JOURNEY = [
  { status: "Called", meaning: "Phone intake started; visit not yet booked." },
  { status: "Booked", meaning: "Doctor and slot confirmed; confirmation sent." },
  { status: "Checked in", meaning: "Patient arrived; token / slip issued." },
  { status: "In consult", meaning: "Doctor started the consultation." },
  { status: "Completed", meaning: "Consult ended; next token can be advanced." },
  { status: "No-show / Cancelled", meaning: "Patient did not arrive, or the visit was cancelled." },
];

const NOTIFY_EVENTS = [
  "Booking confirmed",
  "Reminder 24h and 1h before the slot",
  "Checked in with token",
  "Doctor delayed",
  "Queue delayed for the next patient",
  "You’re next",
  "Checkup started",
  "Visit completed",
  "Overtime fee after the fixed consult time",
];

const QUICK_ACTIONS = [
  { to: "/book", label: "Book an appointment", hint: "No login required" },
  { to: "/track", label: "Track by mobile", hint: "Appointment status" },
  { to: "/queue", label: "Patient queue", hint: "Live wait times" },
  { to: "/hospital-register", label: "Register a hospital", hint: "Get a hospital ID" },
  { to: "/patient/login", label: "Patient portal", hint: "Reports, chat, orders" },
  { to: "/login", label: "Staff / doctor login", hint: "Hospital, doctor, store" },
];

const CONTACT_EMAIL = "azherkhan061@gmail.com";

export default function AboutPage() {
  const t = useT();
  const location = useLocation();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [organization, setOrganization] = useState("");
  const [subject, setSubject] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [sent, setSent] = useState(false);

  useEffect(() => {
    const id = location.hash.replace("#", "");
    if (!id) {
      window.scrollTo(0, 0);
      return;
    }
    const node = document.getElementById(id);
    if (node) node.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [location.hash]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    if (!name.trim() || !email.trim() || !subject.trim() || message.trim().length < 10) {
      setError(t("Please enter your name, email, subject, and a message of at least 10 characters."));
      return;
    }
    setSaving(true);
    try {
      await submitContactInquiry({
        name: name.trim(),
        email: email.trim(),
        phone: phone.trim() || undefined,
        organization: organization.trim() || undefined,
        subject: subject.trim(),
        message: message.trim(),
      });
      setSent(true);
      toast.success(t("Thank you. We received your message and will get back to you."));
      setName("");
      setEmail("");
      setPhone("");
      setOrganization("");
      setSubject("");
      setMessage("");
    } catch {
      const mailto = `mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent(subject.trim())}&body=${encodeURIComponent(
        `${name.trim()}\n${email.trim()}\n${phone.trim()}\n${organization.trim()}\n\n${message.trim()}`,
      )}`;
      window.location.href = mailto;
      setSent(true);
      toast.success(t("Opening your email app so you can send the message."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <article className="info-site" aria-label={t("MedTrack application information")}>
      <nav className="info-subnav" aria-label={t("On this page")}>
        {NAV_LINKS.map((item) => (
          <a key={item.href} href={item.href}>
            {t(item.label)}
          </a>
        ))}
      </nav>

      <section className="info-hero" aria-labelledby="info-hero-title">
        <p className="info-kicker">{t("MedTrack Clinic")}</p>
        <h1 id="info-hero-title">{t("Hospital visits, from the first call to the last consult.")}</h1>
        <p className="info-lead">
          {t(
            "MedTrack is a medical visit tracker for clinics and hospitals. It covers booking, check-in, live queues, doctor workflows, patient status, pharmacy orders, HR, and SMS/email alerts — in English and Hindi.",
          )}
        </p>
        <div className="info-hero-actions">
          <Link to="/book" className="info-btn info-btn--primary">
            {t("Book an appointment")}
          </Link>
          <a href="#contact" className="info-btn info-btn--ghost">
            {t("Contact us")}
          </a>
          <Link to="/login" className="info-btn info-btn--ghost">
            {t("Sign in")}
          </Link>
        </div>
        <ul className="info-stats" aria-label={t("Product snapshot")}>
          <li>
            <strong>4</strong>
            <span>{t("Login types")}</span>
          </li>
          <li>
            <strong>15+</strong>
            <span>{t("Clinic modules")}</span>
          </li>
          <li>
            <strong>3</strong>
            <span>{t("Notify channels")}</span>
          </li>
          <li>
            <strong>2</strong>
            <span>{t("Languages")}</span>
          </li>
        </ul>
      </section>

      <section id="about" className="info-section">
        <h2>{t("What MedTrack is")}</h2>
        <p>
          {t(
            "MedTrack Clinic is built so front-desk staff can create a visit from a phone call in under 45 seconds, doctors can run a minimal queue, and patients always see doctor name, fixed appointment time, token, and when the consult is likely.",
          )}
        </p>
        <p>
          {t(
            "The product is not a full EHR, lab system, insurance engine, or telemedicine app. It is the operational spine of a clinic visit: schedules, bookings, tokens, delays, fees, documents, chat, medicine orders, and hospital HR.",
          )}
        </p>
      </section>

      <section id="roles" className="info-section">
        <h2>{t("Who it is for")}</h2>
        <div className="info-card-grid">
          {AUDIENCES.map((item) => (
            <article key={item.title} className="info-card">
              <h3>{t(item.title)}</h3>
              <p>{t(item.text)}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="info-section">
        <h2>{t("Start here")}</h2>
        <div className="info-action-grid">
          {QUICK_ACTIONS.map((item) => (
            <Link key={item.to} to={item.to} className="info-action">
              <strong>{t(item.label)}</strong>
              <span>{t(item.hint)}</span>
            </Link>
          ))}
        </div>
      </section>

      <section id="modules" className="info-section">
        <h2>{t("Everything in the application")}</h2>
        <p className="info-section-lead">
          {t("These are the live MedTrack modules. Open a card to go to the matching screen (some require sign-in).")}
        </p>
        <div className="info-module-grid">
          {MODULES.map((item) => (
            <Link key={item.title} to={item.to} className="info-module">
              <span>{t(item.group)}</span>
              <h3>{t(item.title)}</h3>
              <p>{t(item.text)}</p>
            </Link>
          ))}
        </div>
      </section>

      <section id="journey" className="info-section">
        <h2>{t("Visit journey")}</h2>
        <p className="info-section-lead">
          {t("Every status change writes a timeline event and, where the patient is affected, notifies SMS + email + the app.")}
        </p>
        <ol className="info-journey">
          {JOURNEY.map((step) => (
            <li key={step.status}>
              <strong>{t(step.status)}</strong>
              <span>{t(step.meaning)}</span>
            </li>
          ))}
        </ol>
      </section>

      <section className="info-section info-split">
        <div>
          <h2>{t("Notifications")}</h2>
          <p>
            {t(
              "Journey messages stay in sync with the patient window: doctor, fixed time, token, and consult-when. Clinics can disable an event. Delay alerts are capped (default 2 per visit) so patients are not spammed.",
            )}
          </p>
          <ul className="info-list">
            {NOTIFY_EVENTS.map((item) => (
              <li key={item}>{t(item)}</li>
            ))}
          </ul>
        </div>
        <div>
          <h2>{t("Default fee rule")}</h2>
          <p>{t("Applied when a visit is completed. The patient is notified if overtime is charged.")}</p>
          <ul className="info-list">
            <li>{t("Base consult: INR 500 for 15 minutes")}</li>
            <li>{t("Extra: INR 200 per 15-minute block after the fixed time")}</li>
            <li>{t("Late grace: 10 minutes before an automatic delay alert")}</li>
            <li>{t("You’re-next: when queue position is 2 or closer")}</li>
          </ul>
        </div>
      </section>

      <section className="info-section">
        <h2>{t("Clinical departments")}</h2>
        <p className="info-section-lead">
          {t("Hospitals pick specialties during setup. Doctors register against the same list.")}
        </p>
        <ul className="info-chips">
          {CLINICAL_DEPARTMENTS.map((name) => (
            <li key={name}>{t(name)}</li>
          ))}
        </ul>
      </section>

      <section className="info-section">
        <h2>{t("Languages & voice")}</h2>
        <p>
          {t(
            "The UI switches between English and Hindi from the header. Voice booking understands spoken appointment details so desk staff and patients can fill a booking without typing every field.",
          )}
        </p>
      </section>

      <section id="contact" className="info-section info-contact">
        <div className="info-contact-copy">
          <h2>{t("Contact us")}</h2>
          <p>
            {t(
              "Questions about adopting MedTrack, registering a hospital, or a visit that needs help? Send a message. We read every inquiry.",
            )}
          </p>
          <p>
            <strong>{t("Email")}</strong>
            <br />
            <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>
          </p>
          <p>
            <strong>{t("Public tools")}</strong>
            <br />
            <Link to="/book">{t("Book")}</Link>
            {" · "}
            <Link to="/track">{t("Track")}</Link>
            {" · "}
            <Link to="/queue">{t("Queue")}</Link>
            {" · "}
            <Link to="/hospital-register">{t("Hospital registration")}</Link>
          </p>
        </div>

        {sent ? (
          <div className="info-contact-form" role="status">
            <h3>{t("Message sent")}</h3>
            <p>{t("Thank you. We received your message and will get back to you.")}</p>
            <button type="button" onClick={() => setSent(false)}>
              {t("Send another message")}
            </button>
          </div>
        ) : (
          <form className="info-contact-form stack" onSubmit={onSubmit}>
            <h3>{t("Write to MedTrack")}</h3>
            {error && <div className="msg error">{error}</div>}
            <label>
              <span>
                {t("Full name")} <span className="required-mark">*</span>
              </span>
              <input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" required />
            </label>
            <label>
              <span>
                {t("Email")} <span className="required-mark">*</span>
              </span>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                required
              />
            </label>
            <label>
              {t("Mobile number")}
              <input
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                autoComplete="tel"
                inputMode="tel"
              />
            </label>
            <label>
              {t("Hospital or organization")}
              <input value={organization} onChange={(e) => setOrganization(e.target.value)} />
            </label>
            <label>
              <span>
                {t("Subject")} <span className="required-mark">*</span>
              </span>
              <input value={subject} onChange={(e) => setSubject(e.target.value)} required />
            </label>
            <label>
              <span>
                {t("Message")} <span className="required-mark">*</span>
              </span>
              <textarea
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                rows={5}
                minLength={10}
                required
              />
            </label>
            <button type="submit" disabled={saving}>
              {saving ? t("Sending…") : t("Send message")}
            </button>
          </form>
        )}
      </section>

      <footer className="info-foot">
        <p>MedTrack Clinic · {t("Medical visit tracking")}</p>
        <p>
          <Link to="/login">{t("Sign in")}</Link>
          {" · "}
          <Link to="/hospital-register">{t("Hospital registration")}</Link>
          {" · "}
          <a href="#about">{t("Back to top")}</a>
        </p>
      </footer>
    </article>
  );
}
