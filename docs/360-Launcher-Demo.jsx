import { useState, useEffect, useRef, useCallback } from "react";

// ─── App Catalogue ──────────────────────────────────────────────────────────
const APPS = [
  { id: "phone",      label: "Phone",      emoji: "📞", bg: "#1b5e20", cat: "core" },
  { id: "messages",   label: "Messages",   emoji: "💬", bg: "#2e7d32", cat: "social" },
  { id: "camera",     label: "Camera",     emoji: "📷", bg: "#0d1b3e", cat: "media" },
  { id: "chrome",     label: "Chrome",     emoji: "🌐", bg: "#1565c0", cat: "core" },
  { id: "gmail",      label: "Gmail",      emoji: "📧", bg: "#b71c1c", cat: "core" },
  { id: "maps",       label: "Maps",       emoji: "🗺️", bg: "#1b5e20", cat: "core" },
  { id: "youtube",    label: "YouTube",    emoji: "▶️", bg: "#c62828", cat: "media" },
  { id: "spotify",    label: "Spotify",    emoji: "🎵", bg: "#0a3d1f", cat: "media" },
  { id: "instagram",  label: "Instagram",  emoji: "📸", bg: "#4a148c", cat: "social" },
  { id: "whatsapp",   label: "WhatsApp",   emoji: "💬", bg: "#004d40", cat: "social" },
  { id: "photos",     label: "Photos",     emoji: "🖼️", bg: "#0d47a1", cat: "media" },
  { id: "calendar",   label: "Calendar",   emoji: "📅", bg: "#1a237e", cat: "core" },
  { id: "clock",      label: "Clock",      emoji: "⏰", bg: "#212121", cat: "core" },
  { id: "settings",   label: "Settings",   emoji: "⚙️", bg: "#37474f", cat: "core" },
  { id: "calculator", label: "Calculator", emoji: "🔢", bg: "#bf360c", cat: "tool" },
  { id: "files",      label: "Files",      emoji: "📁", bg: "#0d47a1", cat: "tool" },
  { id: "weather",    label: "Weather",    emoji: "🌤️", bg: "#01579b", cat: "tool" },
  { id: "netflix",    label: "Netflix",    emoji: "🎬", bg: "#b71c1c", cat: "media" },
  { id: "reddit",     label: "Reddit",     emoji: "👾", bg: "#bf360c", cat: "social" },
  { id: "discord",    label: "Discord",    emoji: "🎮", bg: "#283593", cat: "social" },
  { id: "telegram",   label: "Telegram",   emoji: "✈️", bg: "#01579b", cat: "social" },
  { id: "twitter",    label: "X",          emoji: "𝕏",  bg: "#111",    cat: "social" },
  { id: "amazon",     label: "Amazon",     emoji: "📦", bg: "#e65100", cat: "shop" },
  { id: "snapchat",   label: "Snapchat",   emoji: "👻", bg: "#f9a825", cat: "social" },
];

const HOME_APPS   = ["camera","messages","chrome","gmail","youtube","spotify","instagram","photos","calendar","clock","settings","weather"];
const DOCK_APPS   = ["phone","chrome","messages","camera"];
const PREDICT_IDS = ["gmail","calendar","maps","spotify"];
const DISTRACTION = new Set(["youtube","instagram","reddit","twitter","snapchat","netflix","discord"]);

const FEED_CARDS = [
  { title: "Snapdragon 8 Elite Gen 2 specs surface — Poco F7 could be a beast", src: "Android Authority", time: "1h ago", accent: "#1a237e" },
  { title: "Google's new launcher experiment surfaces in beta build", src: "9to5Google", time: "3h ago", accent: "#1b5e20" },
  { title: "HyperOS 3 rolling out globally — here's what's new", src: "XDA Developers", time: "5h ago", accent: "#4a148c" },
  { title: "The 10 best Android apps of 2026 you're probably not using", src: "The Verge", time: "1d ago", accent: "#b71c1c" },
];

const getApp = (id) => APPS.find((a) => a.id === id);

// ─── Single App Icon ─────────────────────────────────────────────────────────
function AppIcon({ appId, showLabel = true, size = 52, onPress, onLongPress, dimmed = false }) {
  const app = getApp(appId);
  if (!app) return null;
  const timerRef = useRef(null);
  const fired = useRef(false);

  const onDown = () => {
    fired.current = false;
    timerRef.current = setTimeout(() => {
      fired.current = true;
      onLongPress?.(appId);
    }, 550);
  };
  const onUp = () => {
    clearTimeout(timerRef.current);
    if (!fired.current) onPress?.(appId);
  };
  const onLeave = () => clearTimeout(timerRef.current);

  return (
    <div
      className="flex flex-col items-center select-none cursor-pointer"
      style={{ width: size + 20, opacity: dimmed ? 0.35 : 1, transition: "opacity 0.3s" }}
      onMouseDown={onDown}
      onMouseUp={onUp}
      onMouseLeave={onLeave}
      onTouchStart={onDown}
      onTouchEnd={onUp}
    >
      <div
        className="flex items-center justify-center transition-transform duration-100 active:scale-90"
        style={{
          width: size, height: size, borderRadius: 14,
          background: app.bg,
          fontSize: size * 0.46,
          boxShadow: "0 2px 12px rgba(0,0,0,0.45)",
        }}
      >
        {app.emoji}
      </div>
      {showLabel && (
        <span
          className="mt-1 truncate text-center text-white"
          style={{
            fontSize: 10.5, maxWidth: size + 12,
            textShadow: "0 1px 6px rgba(0,0,0,0.9)",
            letterSpacing: 0.2,
          }}
        >
          {app.label}
        </span>
      )}
    </div>
  );
}

// ─── Glass Feed Panel ─────────────────────────────────────────────────────────
function FeedPanel({ visible, onClose }) {
  return (
    <div
      className="absolute inset-0 z-30 flex flex-col"
      style={{
        background: "rgba(6,6,18,0.97)",
        backdropFilter: "blur(28px)",
        transform: visible ? "translateX(0)" : "translateX(-100%)",
        transition: "transform 0.38s cubic-bezier(0.4,0,0.2,1)",
        willChange: "transform",
      }}
    >
      {/* Header */}
      <div className="flex items-center gap-3 px-5 pt-12 pb-4">
        <button
          onClick={onClose}
          className="flex items-center justify-center rounded-full transition-colors"
          style={{ width: 36, height: 36, background: "rgba(255,255,255,0.08)", color: "rgba(255,255,255,0.7)", fontSize: 18 }}
        >
          ‹
        </button>
        <div>
          <div className="text-white font-medium" style={{ fontSize: 16 }}>Discover</div>
          <div style={{ color: "rgba(255,255,255,0.4)", fontSize: 11 }}>Personalised for you</div>
        </div>
        {/* Google G */}
        <div className="ml-auto flex items-center justify-center rounded-full text-white font-bold" style={{ width: 30, height: 30, background: "#4285f4", fontSize: 14 }}>G</div>
      </div>

      {/* Cards */}
      <div className="flex-1 overflow-auto flex flex-col gap-3 px-4 pb-6">
        {FEED_CARDS.map((card, i) => (
          <div
            key={i}
            className="rounded-2xl p-4 cursor-pointer"
            style={{
              background: "rgba(255,255,255,0.05)",
              border: "1px solid rgba(255,255,255,0.08)",
              borderLeft: `3px solid ${card.accent}`,
            }}
          >
            <div style={{ color: "rgba(255,255,255,0.38)", fontSize: 10.5, marginBottom: 6, textTransform: "uppercase", letterSpacing: 0.5 }}>
              {card.src} · {card.time}
            </div>
            <div className="text-white font-medium leading-snug" style={{ fontSize: 13.5 }}>
              {card.title}
            </div>
          </div>
        ))}

        {/* Weather card */}
        <div className="rounded-2xl p-4" style={{ background: "linear-gradient(135deg, #01579b, #0288d1)", border: "1px solid rgba(255,255,255,0.1)" }}>
          <div style={{ color: "rgba(255,255,255,0.6)", fontSize: 10.5, marginBottom: 4 }}>WEATHER NOW</div>
          <div className="flex items-center gap-3">
            <span style={{ fontSize: 32 }}>🌤️</span>
            <div>
              <div className="text-white font-light" style={{ fontSize: 28 }}>29°</div>
              <div style={{ color: "rgba(255,255,255,0.7)", fontSize: 11 }}>Partly Cloudy · Mumbai</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── App Drawer ──────────────────────────────────────────────────────────────
function AppDrawer({ visible, apps, searchQuery, onSearchChange, focusMode, onToggleFocus, onAppPress, onLongPress, hiddenCount, onRestoreAll, onClose }) {
  const inputRef = useRef(null);
  useEffect(() => {
    if (visible) setTimeout(() => inputRef.current?.focus(), 300);
    else onSearchChange("");
  }, [visible]);

  return (
    <div
      className="absolute inset-0 z-20 flex flex-col"
      style={{
        background: "rgba(8,8,18,0.88)",
        backdropFilter: "blur(40px)",
        transform: visible ? "translateY(0)" : "translateY(100%)",
        transition: "transform 0.38s cubic-bezier(0.4,0,0.2,1)",
        willChange: "transform",
      }}
    >
      {/* Drag handle */}
      <div className="flex justify-center pt-4 pb-1 cursor-pointer" onClick={onClose}>
        <div style={{ width: 36, height: 4, borderRadius: 2, background: "rgba(255,255,255,0.25)" }} />
      </div>

      {/* Search */}
      <div className="px-5 py-3">
        <div className="flex items-center gap-2 rounded-2xl px-4 py-2.5" style={{ background: "rgba(255,255,255,0.08)", border: "1px solid rgba(255,255,255,0.1)" }}>
          <span style={{ fontSize: 15, opacity: 0.5 }}>🔍</span>
          <input
            ref={inputRef}
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Search apps…"
            className="flex-1 bg-transparent outline-none text-white"
            style={{ fontSize: 14, caretColor: "white" }}
          />
          {searchQuery && (
            <button onClick={() => onSearchChange("")} style={{ color: "rgba(255,255,255,0.4)", fontSize: 16 }}>✕</button>
          )}
        </div>
      </div>

      {/* Chips row */}
      <div className="flex items-center gap-2 px-5 pb-2 flex-wrap">
        <button
          onClick={onToggleFocus}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium transition-all duration-300"
          style={{
            background: focusMode ? "rgba(46,204,113,0.25)" : "rgba(255,255,255,0.08)",
            border: focusMode ? "1px solid rgba(46,204,113,0.5)" : "1px solid rgba(255,255,255,0.1)",
            color: focusMode ? "#2ecc71" : "rgba(255,255,255,0.6)",
          }}
        >
          <span>{focusMode ? "🎯" : "💤"}</span>
          {focusMode ? "Focus ON" : "Focus OFF"}
        </button>

        {hiddenCount > 0 && (
          <button
            onClick={onRestoreAll}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs"
            style={{ background: "rgba(255,255,255,0.06)", border: "1px solid rgba(255,255,255,0.1)", color: "rgba(255,255,255,0.5)" }}
          >
            👁 Restore {hiddenCount}
          </button>
        )}
      </div>

      {/* App Grid */}
      {apps.length === 0 ? (
        <div className="flex-1 flex flex-col items-center justify-center gap-2">
          <span style={{ fontSize: 36, opacity: 0.3 }}>🔍</span>
          <div style={{ color: "rgba(255,255,255,0.3)", fontSize: 13 }}>No apps found</div>
        </div>
      ) : (
        <div
          className="flex-1 overflow-auto px-4 pb-6"
          style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "12px 4px", alignContent: "start" }}
        >
          {apps.map((app) => (
            <div key={app.id} className="flex justify-center">
              <AppIcon
                appId={app.id}
                size={50}
                onPress={onAppPress}
                onLongPress={onLongPress}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ─── Context Menu ────────────────────────────────────────────────────────────
function ContextMenu({ appId, onHide, onOpen, onInfo, onClose }) {
  const app = getApp(appId);
  if (!app) return null;
  return (
    <div
      className="absolute inset-0 z-40 flex items-center justify-center"
      style={{ background: "rgba(0,0,0,0.6)", backdropFilter: "blur(8px)" }}
      onClick={onClose}
    >
      <div
        className="rounded-3xl overflow-hidden"
        style={{ width: 240, background: "rgba(20,20,35,0.97)", border: "1px solid rgba(255,255,255,0.12)", boxShadow: "0 20px 60px rgba(0,0,0,0.8)" }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* App preview */}
        <div className="flex items-center gap-3 px-5 py-4" style={{ borderBottom: "1px solid rgba(255,255,255,0.07)" }}>
          <div className="flex items-center justify-center rounded-2xl text-2xl" style={{ width: 48, height: 48, background: app.bg }}>
            {app.emoji}
          </div>
          <div>
            <div className="text-white font-medium" style={{ fontSize: 15 }}>{app.label}</div>
            <div style={{ color: "rgba(255,255,255,0.35)", fontSize: 11 }}>com.app.{appId}</div>
          </div>
        </div>

        {/* Actions */}
        {[
          { icon: "🚀", label: "Open", action: onOpen },
          { icon: "👁‍🗨", label: "Hide from Drawer", action: () => onHide(appId), danger: true },
          { icon: "ℹ️", label: "App Info", action: onInfo },
        ].map(({ icon, label, action, danger }) => (
          <button
            key={label}
            onClick={action}
            className="w-full flex items-center gap-3 px-5 py-3.5 text-left transition-colors"
            style={{
              color: danger ? "#e74c3c" : "rgba(255,255,255,0.85)",
              fontSize: 14,
              background: "transparent",
              borderBottom: "1px solid rgba(255,255,255,0.05)",
            }}
            onMouseEnter={(e) => (e.currentTarget.style.background = "rgba(255,255,255,0.05)")}
            onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
          >
            <span style={{ fontSize: 18 }}>{icon}</span>
            {label}
          </button>
        ))}
      </div>
    </div>
  );
}

// ─── Toast ───────────────────────────────────────────────────────────────────
function Toast({ message }) {
  return (
    <div
      className="absolute bottom-24 left-4 right-4 z-50 flex justify-center pointer-events-none"
      style={{ transition: "opacity 0.3s", opacity: message ? 1 : 0 }}
    >
      <div
        className="px-4 py-2 rounded-2xl text-white text-sm text-center"
        style={{ background: "rgba(30,30,50,0.95)", border: "1px solid rgba(255,255,255,0.12)", backdropFilter: "blur(16px)", maxWidth: 240 }}
      >
        {message}
      </div>
    </div>
  );
}

// ─── Stars Background ─────────────────────────────────────────────────────────
const STARS = Array.from({ length: 55 }, (_, i) => ({
  left: `${(i * 37.3) % 100}%`,
  top: `${(i * 61.7) % 75}%`,
  size: i % 7 === 0 ? 2.5 : i % 3 === 0 ? 1.5 : 1,
  opacity: 0.25 + (i % 5) * 0.15,
}));

// ─── Main Demo ───────────────────────────────────────────────────────────────
export default function LauncherDemo() {
  const [now, setNow] = useState(new Date());
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [feedOpen, setFeedOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [hiddenApps, setHiddenApps] = useState(new Set());
  const [focusMode, setFocusMode] = useState(false);
  const [contextApp, setContextApp] = useState(null);
  const [toast, setToast] = useState("");
  const [toastTimer, setToastTimer] = useState(null);

  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 10_000);
    return () => clearInterval(t);
  }, []);

  const timeStr = now.toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit", hour12: false });
  const dateStr = now.toLocaleDateString("en-US", { weekday: "long", month: "short", day: "numeric" });

  const showToast = useCallback((msg) => {
    setToast(msg);
    clearTimeout(toastTimer);
    const t = setTimeout(() => setToast(""), 2400);
    setToastTimer(t);
  }, [toastTimer]);

  // Derived app lists
  const drawerApps = APPS
    .filter((a) => !hiddenApps.has(a.id))
    .filter((a) => !focusMode || !DISTRACTION.has(a.id))
    .filter((a) => !searchQuery || a.label.toLowerCase().includes(searchQuery.toLowerCase()));

  const homeApps = HOME_APPS.filter((id) => !hiddenApps.has(id));
  const predictApps = PREDICT_IDS.filter((id) => !hiddenApps.has(id));

  const handleAppPress = (appId) => {
    setContextApp(null);
    showToast(`Opening ${getApp(appId)?.label}…`);
  };

  const handleLongPress = (appId) => setContextApp(appId);

  const handleHide = (appId) => {
    setHiddenApps((prev) => new Set([...prev, appId]));
    setContextApp(null);
    showToast(`${getApp(appId)?.label} hidden from drawer`);
  };

  const handleRestoreAll = () => {
    setHiddenApps(new Set());
    showToast("All apps restored");
  };

  return (
    <div
      className="min-h-screen flex items-center justify-center p-6 gap-8"
      style={{ background: "#05050f", fontFamily: "-apple-system,BlinkMacSystemFont,'SF Pro Display',system-ui,sans-serif" }}
    >
      {/* ── Left sidebar ─────────────────────────────────────────── */}
      <div className="hidden md:flex flex-col gap-5 w-44 flex-shrink-0">
        <div>
          <div className="text-xs font-mono mb-3" style={{ color: "#2ecc71", letterSpacing: 1 }}>
            360 LAUNCHER
          </div>
          <div className="text-white font-light" style={{ fontSize: 13, lineHeight: 1.6, color: "rgba(255,255,255,0.55)" }}>
            Interactive demo — all gestures simulated.
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <div className="text-xs font-mono mb-1" style={{ color: "rgba(255,255,255,0.3)", letterSpacing: 1 }}>GESTURES</div>
          {[
            ["⬅ Pull", "Google Discover feed"],
            ["⬆ Swipe up", "Open app drawer"],
            ["Hold icon", "Hide app option"],
            ["🔍 Search", "Filter installed apps"],
            ["🎯 Focus", "Hide distractions"],
          ].map(([g, d]) => (
            <div key={g} className="flex flex-col gap-0.5">
              <div className="text-xs font-medium" style={{ color: "rgba(255,255,255,0.75)" }}>{g}</div>
              <div className="text-xs" style={{ color: "rgba(255,255,255,0.35)" }}>{d}</div>
            </div>
          ))}
        </div>

        {hiddenApps.size > 0 && (
          <button
            onClick={handleRestoreAll}
            className="px-3 py-2 rounded-xl text-xs text-left transition-colors"
            style={{ background: "rgba(255,255,255,0.06)", border: "1px solid rgba(255,255,255,0.1)", color: "rgba(255,255,255,0.6)" }}
          >
            👁 Restore {hiddenApps.size} hidden app{hiddenApps.size !== 1 ? "s" : ""}
          </button>
        )}
      </div>

      {/* ── Phone ────────────────────────────────────────────────── */}
      <div
        className="relative flex-shrink-0"
        style={{
          width: 340, height: 720,
          borderRadius: 44,
          border: "6px solid #1a1a2e",
          boxShadow: "0 0 0 1.5px #0d0d20, 0 50px 100px rgba(0,0,0,0.9), 0 0 80px rgba(80,60,200,0.08)",
          overflow: "hidden",
          background: "#000",
          userSelect: "none",
        }}
      >
        {/* ── Wallpaper ─────────────────────────────────────────── */}
        <div
          className="absolute inset-0"
          style={{
            background: "radial-gradient(ellipse at 20% 15%, #1c0945 0%, transparent 55%), radial-gradient(ellipse at 85% 75%, #061638 0%, transparent 55%), radial-gradient(ellipse at 55% 50%, #0a0a1a 0%, #000 100%)",
          }}
        >
          {STARS.map((s, i) => (
            <div
              key={i}
              className="absolute rounded-full"
              style={{ left: s.left, top: s.top, width: s.size, height: s.size, background: `rgba(255,255,255,${s.opacity})` }}
            />
          ))}
          {/* Nebula glow */}
          <div className="absolute" style={{ width: 280, height: 280, borderRadius: "50%", background: "radial-gradient(circle, rgba(120,60,220,0.07) 0%, transparent 70%)", top: -80, left: -60 }} />
          <div className="absolute" style={{ width: 200, height: 200, borderRadius: "50%", background: "radial-gradient(circle, rgba(40,80,200,0.05) 0%, transparent 70%)", bottom: 100, right: -40 }} />
        </div>

        {/* ── Feed Panel ────────────────────────────────────────── */}
        <FeedPanel visible={feedOpen} onClose={() => setFeedOpen(false)} />

        {/* ── Drawer ────────────────────────────────────────────── */}
        <AppDrawer
          visible={drawerOpen}
          apps={drawerApps}
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          focusMode={focusMode}
          onToggleFocus={() => {
            const next = !focusMode;
            setFocusMode(next);
            showToast(next ? "Focus mode ON — distractions hidden" : "Focus mode OFF");
          }}
          onAppPress={(id) => { handleAppPress(id); setDrawerOpen(false); }}
          onLongPress={handleLongPress}
          hiddenCount={hiddenApps.size}
          onRestoreAll={handleRestoreAll}
          onClose={() => setDrawerOpen(false)}
        />

        {/* ── Context Menu ──────────────────────────────────────── */}
        {contextApp && (
          <ContextMenu
            appId={contextApp}
            onHide={handleHide}
            onOpen={() => { handleAppPress(contextApp); setContextApp(null); }}
            onInfo={() => { showToast(`Opening App Info for ${getApp(contextApp)?.label}`); setContextApp(null); }}
            onClose={() => setContextApp(null)}
          />
        )}

        {/* ── Status Bar ────────────────────────────────────────── */}
        <div
          className="absolute top-0 left-0 right-0 z-10 flex items-center justify-between px-6"
          style={{ paddingTop: 14, paddingBottom: 6 }}
        >
          <span className="text-white text-xs font-medium" style={{ opacity: 0.9 }}>
            {timeStr}
          </span>
          {/* Notch pill */}
          <div style={{ width: 100, height: 22, background: "#000", borderRadius: 11 }} />
          <div className="flex items-center gap-1.5" style={{ opacity: 0.85 }}>
            <span className="text-white" style={{ fontSize: 9, fontWeight: 600, letterSpacing: 0.5 }}>5G</span>
            <div className="flex items-end gap-px">
              {[3, 4, 5, 6].map((h) => (
                <div key={h} className="bg-white rounded-px" style={{ width: 2.5, height: h, borderRadius: 1 }} />
              ))}
            </div>
            <div className="flex items-center justify-center rounded-sm border border-white" style={{ width: 22, height: 11, position: "relative" }}>
              <div style={{ position: "absolute", left: 2, top: 2, right: 5, bottom: 2, background: "#fff", borderRadius: 1 }} />
              <div style={{ position: "absolute", right: -3, top: 3.5, width: 2.5, height: 4, background: "rgba(255,255,255,0.45)", borderRadius: "0 1px 1px 0" }} />
            </div>
          </div>
        </div>

        {/* ── Home Content ──────────────────────────────────────── */}
        <div className="absolute inset-0 flex flex-col" style={{ paddingTop: 48 }}>

          {/* Clock */}
          <div className="px-7 pt-5">
            <div
              style={{
                fontSize: 72, fontWeight: 100, color: "#fff", letterSpacing: -3, lineHeight: 1,
                textShadow: "0 4px 30px rgba(0,0,0,0.6)",
              }}
            >
              {timeStr}
            </div>
            <div style={{ color: "rgba(255,255,255,0.55)", fontSize: 12.5, letterSpacing: 1.5, marginTop: 5, textTransform: "uppercase" }}>
              {dateStr}
            </div>
          </div>

          {/* Home Grid — 3 rows × 4 cols */}
          <div
            className="px-5 pt-8 flex-1"
            style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "10px 0", alignContent: "start" }}
          >
            {homeApps.slice(0, 12).map((id) => (
              <div key={id} className="flex justify-center">
                <AppIcon
                  appId={id}
                  size={50}
                  onPress={handleAppPress}
                  onLongPress={handleLongPress}
                />
              </div>
            ))}
          </div>

          {/* Predictive Bar */}
          {predictApps.length > 0 && (
            <div
              className="mx-5 mb-2 flex justify-around items-center py-2.5 px-3 rounded-2xl"
              style={{
                background: "rgba(255,255,255,0.06)",
                border: "1px solid rgba(255,255,255,0.08)",
                backdropFilter: "blur(12px)",
              }}
            >
              {predictApps.map((id) => (
                <AppIcon key={id} appId={id} showLabel={false} size={40} onPress={handleAppPress} onLongPress={handleLongPress} />
              ))}
              <div style={{ color: "rgba(255,255,255,0.2)", fontSize: 9, letterSpacing: 0.5, textTransform: "uppercase" }}>AI</div>
            </div>
          )}

          {/* Dock */}
          <div
            className="mx-5 mb-4 flex justify-around items-center py-3 px-3 rounded-3xl"
            style={{
              background: "rgba(255,255,255,0.1)",
              backdropFilter: "blur(24px)",
              border: "1px solid rgba(255,255,255,0.14)",
              boxShadow: "0 4px 24px rgba(0,0,0,0.4)",
            }}
          >
            {DOCK_APPS.map((id) => (
              <AppIcon key={id} appId={id} showLabel={false} size={50} onPress={handleAppPress} onLongPress={handleLongPress} />
            ))}
          </div>

          {/* Swipe-up handle */}
          <div
            className="flex justify-center pb-2 cursor-pointer"
            onClick={() => { setDrawerOpen(true); setFeedOpen(false); }}
            title="Swipe up for App Drawer"
          >
            <div style={{ width: 34, height: 4, borderRadius: 2, background: "rgba(255,255,255,0.22)" }} />
          </div>
        </div>

        {/* ── Feed trigger tab ──────────────────────────────────── */}
        {!feedOpen && !drawerOpen && (
          <button
            onClick={() => { setFeedOpen(true); setDrawerOpen(false); }}
            title="Slide for Google Discover"
            className="absolute z-10"
            style={{
              left: 0, top: "38%",
              width: 18, height: 56, borderRadius: "0 10px 10px 0",
              background: "rgba(255,255,255,0.07)",
              border: "1px solid rgba(255,255,255,0.1)",
              borderLeft: "none",
              cursor: "pointer",
              display: "flex", alignItems: "center", justifyContent: "center",
            }}
          >
            <span style={{ color: "rgba(255,255,255,0.35)", fontSize: 11 }}>›</span>
          </button>
        )}

        {/* ── Toast ─────────────────────────────────────────────── */}
        <Toast message={toast} />
      </div>

      {/* ── Right sidebar ────────────────────────────────────────── */}
      <div className="hidden md:flex flex-col gap-4 w-44 flex-shrink-0">
        <div className="text-xs font-mono mb-1" style={{ color: "rgba(255,255,255,0.3)", letterSpacing: 1 }}>FEATURES</div>
        {[
          { label: "Google Discover", icon: "🌐", note: "← pull left tab" },
          { label: "App Drawer", icon: "⬆", note: "pull up handle" },
          { label: "App Hiding", icon: "👁", note: "long-press any icon" },
          { label: "Focus Mode", icon: "🎯", note: "toggle in drawer" },
          { label: "Predictive Row", icon: "🧠", note: "above dock" },
          { label: "Icon Packs", icon: "🎨", note: "v1 build only" },
          { label: "Widget Support", icon: "📊", note: "v1 build only" },
        ].map(({ label, icon, note }) => (
          <div key={label} className="flex items-start gap-2">
            <span style={{ fontSize: 15, minWidth: 20 }}>{icon}</span>
            <div>
              <div style={{ color: "rgba(255,255,255,0.8)", fontSize: 12, fontWeight: 500 }}>{label}</div>
              <div style={{ color: "rgba(255,255,255,0.3)", fontSize: 10.5 }}>{note}</div>
            </div>
          </div>
        ))}

        {/* Stats */}
        <div
          className="mt-2 rounded-xl p-3 flex flex-col gap-2"
          style={{ background: "rgba(255,255,255,0.04)", border: "1px solid rgba(255,255,255,0.07)" }}
        >
          <div className="text-xs font-mono" style={{ color: "rgba(255,255,255,0.25)", letterSpacing: 1 }}>LIVE STATS</div>
          {[
            ["Total apps", APPS.length],
            ["Visible", APPS.length - hiddenApps.size],
            ["Hidden", hiddenApps.size],
            ["Focus blocks", focusMode ? DISTRACTION.size : 0],
          ].map(([k, v]) => (
            <div key={k} className="flex justify-between">
              <span style={{ color: "rgba(255,255,255,0.4)", fontSize: 11 }}>{k}</span>
              <span style={{ color: "rgba(255,255,255,0.85)", fontSize: 11, fontWeight: 500 }}>{v}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
