import { useEffect, useState } from "react";
import QRCode from "qrcode";

type Props = {
  payload: string;
  size?: number;
  className?: string;
};

export default function UpiQr({ payload, size = 240, className = "appointment-pay-qr" }: Props) {
  const [src, setSrc] = useState("");

  useEffect(() => {
    if (!payload) {
      setSrc("");
      return;
    }
    let cancelled = false;
    QRCode.toDataURL(payload, {
      errorCorrectionLevel: "M",
      margin: 4,
      width: size,
      color: { dark: "#000000", light: "#ffffff" },
    })
      .then((url) => {
        if (!cancelled) setSrc(url);
      })
      .catch(() => {
        if (!cancelled) setSrc("");
      });
    return () => {
      cancelled = true;
    };
  }, [payload, size]);

  if (!src) return null;
  return <img className={className} src={src} alt="Scan to pay with UPI" width={size} height={size} />;
}
