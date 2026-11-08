import { useState } from "react";
import { useAuth } from "../context/AuthContext";

export default function AuthScreen() {
  const { login, register } = useAuth();
  const [mode, setMode] = useState("login");
  const [form, setForm] = useState({ username: "", email: "", password: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    if (!form.username.trim() || !form.password) return setError("Vui lòng nhập tài khoản và mật khẩu.");
    if (mode === "register" && form.password.length < 6) return setError("Mật khẩu cần ít nhất 6 ký tự.");
    setBusy(true);
    try {
      if (mode === "login") await login(form.username, form.password);
      else await register(form);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth">
      <form className="auth-card" onSubmit={submit}>
        <div className="brand-mark big" aria-hidden="true">⚡</div>
        <h1>EV Charging Smart</h1>
        <p className="muted">{mode === "login" ? "Đăng nhập để đặt chỗ sạc" : "Tạo tài khoản mới"}</p>

        <label>
          Tên đăng nhập
          <input value={form.username} onChange={set("username")} autoComplete="username" autoFocus />
        </label>
        {mode === "register" && (
          <label>
            Email
            <input type="email" value={form.email} onChange={set("email")} autoComplete="email" />
          </label>
        )}
        <label>
          Mật khẩu
          <input
            type="password"
            value={form.password}
            onChange={set("password")}
            autoComplete={mode === "login" ? "current-password" : "new-password"}
          />
        </label>

        {error && <p className="alert" role="alert">{error}</p>}
        <button className="primary" disabled={busy}>
          {busy ? "Đang xử lý… (lần đầu có thể mất ~1 phút)" : mode === "login" ? "Đăng nhập" : "Đăng ký"}
        </button>
        <button
          type="button"
          className="link"
          onClick={() => {
            setMode(mode === "login" ? "register" : "login");
            setError("");
          }}
        >
          {mode === "login" ? "Chưa có tài khoản? Đăng ký" : "Đã có tài khoản? Đăng nhập"}
        </button>
      </form>
    </div>
  );
}
