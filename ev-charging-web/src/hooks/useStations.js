import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../services/api";
import { REFRESH_SECONDS } from "../config";

export function useStations() {
  const [stations, setStations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [waking, setWaking] = useState(false); // máy chủ Render đang khởi động
  const [error, setError] = useState(null);
  const mounted = useRef(true);

  const load = useCallback(async (first = false) => {
    const wakeTimer = first ? setTimeout(() => mounted.current && setWaking(true), 4000) : null;
    try {
      const list = await api.stations();
      if (!mounted.current) return;
      setStations(list);
      setError(null);
    } catch (e) {
      if (mounted.current) setError(e.message);
    } finally {
      clearTimeout(wakeTimer);
      if (mounted.current) {
        setLoading(false);
        setWaking(false);
      }
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    load(true);
    const timer = setInterval(() => load(), REFRESH_SECONDS * 1000);
    return () => {
      mounted.current = false;
      clearInterval(timer);
    };
  }, [load]);

  return { stations, loading, waking, error, reload: () => load(true) };
}
