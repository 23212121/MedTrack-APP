import { useT } from "../../i18n";

type Props = { title: string; text: string };

export default function HrmPlaceholderPage({ title, text }: Props) {
  const t = useT();
  return (
    <section className="panel hrm-placeholder">
      <h2>{t(title)}</h2>
      <p className="lead">{t(text)}</p>
    </section>
  );
}
