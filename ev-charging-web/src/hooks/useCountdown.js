import { useEffect, useState } from "react";

/** Số giây còn lại tới mốc `iso` (null nếu chưa có mốc). */
export function useCountdown(iso) {
  const calc = () => (iso ? Math.max(0, Math.floor((new Date(iso).getTime() - Date.now()) / 1000)) : null);
  const [left, setLeft] = useState(calc);
  useEffect(() => {
    setLeft(calc());
    if (!iso) return undefined;
    const t = setInterval(() => setLeft(calc()), 1000);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [iso]);
  return left;
}
