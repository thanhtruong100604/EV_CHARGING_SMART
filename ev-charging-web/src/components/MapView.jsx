import { useEffect, useRef, useState } from "react";
import { CircleMarker, MapContainer, Marker, Polyline, Popup, TileLayer, useMap } from "react-leaflet";
import L from "leaflet";
import { DEFAULT_CENTER, DEFAULT_ZOOM, STATUS, TILE_PROVIDERS } from "../config";

const iconCache = {};
function stationIcon(status, selected) {
  const key = status + selected;
  if (!iconCache[key]) {
    const size = selected ? 40 : 32;
    iconCache[key] = L.divIcon({
      className: "pin-wrap",
      iconSize: [size, size],
      iconAnchor: [size / 2, size / 2],
      html: `<div class="pin${selected ? " pin-selected" : ""}" style="background:${STATUS[status].color}">⚡</div>`,
    });
  }
  return iconCache[key];
}

const placeIcon = L.divIcon({
  className: "pin-wrap",
  iconSize: [30, 30],
  iconAnchor: [15, 30],
  html: '<div class="pin-place">📍</div>',
});

function FlyTo({ target, zoom }) {
  const map = useMap();
  useEffect(() => {
    if (target) map.flyTo([target.lat, target.lng], zoom, { duration: 0.8 });
  }, [target, zoom, map]);
  return null;
}

function FitRoute({ points }) {
  const map = useMap();
  useEffect(() => {
    if (points?.length > 1) map.fitBounds(points, { padding: [50, 50] });
  }, [points, map]);
  return null;
}

export default function MapView({ stations, selected, onSelect, userPosition, route, place }) {
  const [providerIdx, setProviderIdx] = useState(0);
  const errors = useRef(0);
  const loaded = useRef(false);
  const provider = TILE_PROVIDERS[providerIdx];

  const switchProvider = (idx) => {
    errors.current = 0;
    loaded.current = false;
    setProviderIdx(idx);
  };

  // Nguồn bản đồ lỗi liên tiếp mà chưa tải được ô nào → chuyển nguồn kế tiếp
  const tileHandlers = {
    tileload: () => {
      loaded.current = true;
      errors.current = 0;
    },
    tileerror: () => {
      errors.current += 1;
      if (!loaded.current && errors.current >= 4 && providerIdx < TILE_PROVIDERS.length - 1) {
        switchProvider(providerIdx + 1);
      }
    },
  };

  return (
    <>
      <MapContainer center={DEFAULT_CENTER} zoom={DEFAULT_ZOOM} className="map" zoomControl>
        <TileLayer
          key={provider.name}
          url={provider.url}
          subdomains={provider.subdomains}
          maxZoom={19}
          attribution={provider.attribution}
          eventHandlers={tileHandlers}
        />

        {stations.map((s) => (
          <Marker
            key={s.id}
            position={[s.lat, s.lng]}
            icon={stationIcon(s.status, s.id === selected?.id)}
            eventHandlers={{ click: () => onSelect(s) }}
          >
            <Popup>
              <strong>{s.name}</strong>
              <br />
              {s.address}
              <br />
              {s.freeTimeSlots} khung giờ còn trống
            </Popup>
          </Marker>
        ))}

        {userPosition && (
          <CircleMarker
            center={[userPosition.lat, userPosition.lng]}
            radius={9}
            pathOptions={{ color: "#fff", weight: 3, fillColor: "#1a73e8", fillOpacity: 1 }}
          />
        )}

        {place && (
          <Marker position={[place.lat, place.lng]} icon={placeIcon}>
            <Popup>
              <strong>{place.name}</strong>
              <br />
              {place.address}
            </Popup>
          </Marker>
        )}

        {route && <Polyline positions={route.points} pathOptions={{ color: "#1a73e8", weight: 6, opacity: 0.85 }} />}

        {route ? <FitRoute points={route.points} /> : <FlyTo target={selected} zoom={16} />}
        <FlyTo target={userPosition} zoom={13} />
        <FlyTo target={place} zoom={16} />
      </MapContainer>

      <label className="provider">
        Nguồn bản đồ
        <select value={providerIdx} onChange={(e) => switchProvider(Number(e.target.value))}>
          {TILE_PROVIDERS.map((p, i) => (
            <option key={p.name} value={i}>{p.name}</option>
          ))}
        </select>
      </label>
    </>
  );
}
