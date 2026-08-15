type Props = { title: string; text: string };

export default function HrmPlaceholderPage({ title, text }: Props) {
  return (
    <section className="panel hrm-placeholder">
      <h2>{title}</h2>
      <p className="lead">{text}</p>
    </section>
  );
}
