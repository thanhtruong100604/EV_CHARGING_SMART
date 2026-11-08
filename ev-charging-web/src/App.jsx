import { useEffect, useMemo, useRef, useState } from "react";
import { useAuth } from "./context/AuthContext";
import { useToast } from "./context/ToastContext";
import { useStations } from "./hooks/useStations";
import { useGeolocation } from "./hooks/useGeolocation";
import { useTheme } from "./hooks/useTheme";
import { api } from "./services/api";
import { fetchRoute } from "./services/geo";
import { distanceKm } from "./utils/format";
import AuthScreen from "./components/AuthScreen";
import Header from "./components/Header";
import FilterBar from "./components/FilterBar";
import StationList from "./components/StationList";
import MapView from "./components/MapView";
import StationSheet from "./components/StationSheet";
import PaymentModal from "./components/PaymentModal";
import InvoiceModal from "./components/InvoiceModal";
import HistoryModal from "./components/HistoryModal";
import ProfileModal from "./components/ProfileModal";

export default function App() {
  const { user } = useAuth();
  return user ? <Main key={user.username} /> : <AuthScreen />;
}

function Main() {
  const { user, logout } = useAuth();
  const toast = useToast();
  const { stations, loading, waking, error, reload } = useStations();
  const { position, error: geoError, locating, locate } = useGeolocation();
  const [theme, toggleTheme] = useTheme();

  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [ward, setWard] = useState("");
  const [selectedId, setSelectedId] = useState(null);
  const [route, setRoute] = useState(null);
  const [routing, setRouting] = useState(false);
  const [place, setPlace] = useState(null);
  const [modal, setModal] = useState(null); // {kind:'pay'|'invoice'|'history'|'profile', ...}

  const wards = useMemo(() => [...new Set(stations.map((s) => s.ward))].sort(), [stations]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    let list = stations.filter(
      (s) =>
        (!status || s.status === status) &&
        (!ward || s.ward === ward) &&
        (!q || `${s.name} ${s.address} ${s.ward}`.toLowerCase().includes(q))
    );
    if (position) {
      list = list
        .map((s) => ({ ...s, distance: distanceKm(position.lat, position.lng, s.lat, s.lng) }))
        .sort((a, b) => a.distance - b.distance);
    }
    return list;
  }, [stations, query, status, ward, position]);

  const selected = stations.find((s) => s.id === selectedId) || null;

  const select = (s) => {
    setSelectedId(s?.id ?? null);
    setRoute(null);
  };

  // Mở lại đơn đang chờ thanh toán (vd: tải lại trang giữa chừng) — app không làm được việc này
  const resumed = useRef(false);
  useEffect(() => {
    if (resumed.current) return;
    resumed.current = true;
    api.history(user.username)
      .then((list) => {
        const pending = list.find((b) => b.status === "Quick_Booking" && new Date(b.expiry_time) > new Date());
        if (pending) {
          setModal({ kind: "pay", id: pending.id });
          toast("Bạn có một đơn đang chờ thanh toán.", "info");
        }
      })
      .catch(() => {});
  }, [user.username, toast]);

  const findNearest = async () => {
    const pos = await locate();
    if (!pos) return;
    const withDist = stations.map((s) => ({ ...s, d: distanceKm(pos.lat, pos.lng, s.lat, s.lng) }));
    const open = withDist.filter((s) => s.freeTimeSlots > 0);
    const pool = open.length ? open : withDist;
    if (!pool.length) return toast("Chưa có trạm nào.", "warn");
    const nearest = pool.sort((a, b) => a.d - b.d)[0];
    select(nearest);
    toast(`Trạm gần nhất: ${nearest.name} (${nearest.d.toFixed(1)} km)`, "ok");
  };

  const startRoute = async () => {
    if (!selected) return;
    setRouting(true);
    try {
      const from = position || (await locate());
      if (!from) return;
      setRoute(await fetchRoute(from, selected));
    } catch (e) {
      toast(e.message, "bad");
    } finally {
      setRouting(false);
    }
  };

  const closeModal = () => setModal(null);

  return (
    <div className="app">
      <aside className="sidebar">
        <Header
          total={stations.length}
          free={stations.filter((s) => s.freeTimeSlots > 0).length}
          user={user}
          theme={theme}
          onTheme={toggleTheme}
          onHistory={() => setModal({ kind: "history" })}
          onProfile={() => setModal({ kind: "profile" })}
        />
        <FilterBar
          query={query} onQuery={setQuery}
          status={status} onStatus={setStatus}
          ward={ward} onWard={setWard} wards={wards}
          onLocate={findNearest} locating={locating}
          onPickPlace={(p) => {
            setPlace(p);
            setRoute(null);
          }}
        />
        {geoError && <p className="alert">{geoError}</p>}
        <StationList
          stations={visible}
          selectedId={selectedId}
          onSelect={select}
          loading={loading}
          waking={waking}
          error={error}
          onRetry={reload}
        />
      </aside>

      <main className="map-area">
        <MapView
          stations={visible}
          selected={selected}
          onSelect={select}
          userPosition={position}
          route={route}
          place={place}
        />
        {selected && (
          <StationSheet
            station={selected}
            userPosition={position}
            onLocate={locate}
            onClose={() => select(null)}
            onRoute={startRoute}
            routing={routing}
            routeInfo={route}
            onBooked={(b) => {
              toast("Đã giữ chỗ! Hãy thanh toán để xác nhận.", "ok");
              setModal({ kind: "pay", id: b.id });
              reload();
            }}
          />
        )}
      </main>

      {modal?.kind === "pay" && (
        <PaymentModal
          bookingId={modal.id}
          onClose={closeModal}
          onPaid={(b) => {
            toast("✅ Thanh toán thành công! Chỗ sạc đã được xác nhận.", "ok");
            setModal({ kind: "invoice", booking: b });
            reload();
          }}
          onEnded={(_b, msg) => {
            toast(msg, "warn");
            closeModal();
            reload();
          }}
        />
      )}
      {modal?.kind === "invoice" && <InvoiceModal booking={modal.booking} onClose={closeModal} />}
      {modal?.kind === "history" && (
        <HistoryModal
          username={user.username}
          onClose={closeModal}
          onPay={(id) => setModal({ kind: "pay", id })}
          onInvoice={(b) => setModal({ kind: "invoice", booking: b })}
        />
      )}
      {modal?.kind === "profile" && (
        <ProfileModal
          user={user}
          onClose={closeModal}
          onHistory={() => setModal({ kind: "history" })}
          onLogout={logout}
        />
      )}
    </div>
  );
}
