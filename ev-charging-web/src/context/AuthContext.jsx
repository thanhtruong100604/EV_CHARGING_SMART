import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api } from "../services/api";
import { getSession, setSession, setUnauthorizedHandler } from "../services/http";

const AuthCtx = createContext(null);
export const useAuth = () => useContext(AuthCtx);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => getSession());

  // Token hết hiệu lực → tự đưa về màn đăng nhập
  useEffect(() => setUnauthorizedHandler(() => setUser(null)), []);

  const start = (d) => {
    const s = { token: d.token, username: d.username, email: d.email || "", isStaff: !!d.is_staff };
    setSession(s);
    setUser(s);
  };

  const login = useCallback(async (username, password) => start(await api.login(username.trim(), password)), []);
  const register = useCallback(async (form) => start(await api.register({ ...form, username: form.username.trim() })), []);
  const logout = useCallback(() => {
    setSession(null);
    setUser(null);
  }, []);

  const value = useMemo(() => ({ user, login, register, logout }), [user, login, register, logout]);
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}
