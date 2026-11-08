import { useCallback, useState } from "react";

export function useGeolocation() {
  const [position, setPosition] = useState(null);
  const [error, setError] = useState(null);
  const [locating, setLocating] = useState(false);

  /** Trả về Promise<{lat,lng} | null> để các luồng khác có thể await. */
  const locate = useCallback(
    () =>
      new Promise((resolve) => {
        if (!navigator.geolocation) {
          setError("Trình duyệt không hỗ trợ định vị.");
          return resolve(null);
        }
        setLocating(true);
        navigator.geolocation.getCurrentPosition(
          (p) => {
            const pos = { lat: p.coords.latitude, lng: p.coords.longitude };
            setPosition(pos);
            setError(null);
            setLocating(false);
            resolve(pos);
          },
          () => {
            setError("Không lấy được vị trí. Hãy cho phép quyền truy cập vị trí trong trình duyệt.");
            setLocating(false);
            resolve(null);
          },
          { enableHighAccuracy: true, timeout: 10000 }
        );
      }),
    []
  );

  return { position, error, locating, locate };
}
