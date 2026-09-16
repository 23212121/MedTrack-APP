type Props = {
  unread?: number;
  onClick: () => void;
  href?: string;
};

export default function ChatLink({ unread = 0, onClick, href }: Props) {
  const label = (
    <>
      Chat
      {unread > 0 ? <span className="chat-unread-badge">{unread > 99 ? "99+" : unread}</span> : null}
    </>
  );
  if (href) {
    return (
      <a
        className="chat-link"
        href={href}
        onClick={(e) => {
          e.preventDefault();
          onClick();
        }}
      >
        {label}
      </a>
    );
  }
  return (
    <button type="button" className="chat-link" onClick={onClick}>
      {label}
    </button>
  );
}
