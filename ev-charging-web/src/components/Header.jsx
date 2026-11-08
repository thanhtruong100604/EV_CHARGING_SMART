export default function Header({ total, free, user, theme, onTheme, onHistory, onProfile }) {
  return (
    <header className="header">
      <div className="brand">
        <span className="brand-mark" aria-hidden="true">⚡</span>
        <div>
          <h1>EV Charging Smart</h1>
          <p>{free} trên {total} trạm còn chỗ</p>
        </div>
      </div>
      <div className="header-actions">
        <button className="icon-btn light" onClick={onTheme} aria-label="Đổi giao diện sáng/tối" title="Sáng / tối">
          {theme === "dark" ? "☀️" : "🌙"}
        </button>
        <button className="icon-btn light" onClick={onHistory} aria-label="Lịch sử đặt chỗ" title="Lịch sử đặt chỗ">📋</button>
        <button className="icon-btn light" onClick={onProfile} aria-label="Hồ sơ" title={user.username}>👤</button>
      </div>
    </header>
  );
}
