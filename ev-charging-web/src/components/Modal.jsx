import { useEffect } from "react";

export default function Modal({ title, onClose, children, wide = false, dismissible = true }) {
  useEffect(() => {
    if (!dismissible) return undefined;
    const onKey = (e) => e.key === "Escape" && onClose?.();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose, dismissible]);

  return (
    <div className="backdrop" onMouseDown={(e) => e.target === e.currentTarget && dismissible && onClose?.()}>
      <div className={wide ? "modal wide" : "modal"} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <h2>{title}</h2>
          {dismissible && (
            <button className="icon-btn" onClick={onClose} aria-label="Đóng">✕</button>
          )}
        </div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}
