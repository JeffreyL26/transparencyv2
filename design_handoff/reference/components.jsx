/* ============================================================
   components.jsx — data, helpers, shared UI
   ============================================================ */
const { useState, useEffect, useRef, useCallback } = React;

/* ---------- currency data (15, mock June 2026) ---------- */
const CUR = {
  EUR: { code: "EUR", name: "Euro",            sym: "€",  cc: "eu" },
  USD: { code: "USD", name: "US-Dollar",       sym: "$",  cc: "us" },
  GBP: { code: "GBP", name: "Brit. Pfund",     sym: "£",  cc: "gb" },
  CHF: { code: "CHF", name: "Schw. Franken",   sym: "Fr", cc: "ch" },
  JPY: { code: "JPY", name: "Japan. Yen",      sym: "¥",  cc: "jp" },
  AUD: { code: "AUD", name: "Austral. Dollar", sym: "$",  cc: "au" },
  CAD: { code: "CAD", name: "Kanad. Dollar",   sym: "$",  cc: "ca" },
  CNY: { code: "CNY", name: "Renminbi",        sym: "¥",  cc: "cn" },
  INR: { code: "INR", name: "Ind. Rupie",      sym: "₹",  cc: "in" },
  BRL: { code: "BRL", name: "Brasil. Real",    sym: "R$", cc: "br" },
  SEK: { code: "SEK", name: "Schwed. Krone",   sym: "kr", cc: "se" },
  NOK: { code: "NOK", name: "Norweg. Krone",   sym: "kr", cc: "no" },
  MXN: { code: "MXN", name: "Mexik. Peso",     sym: "$",  cc: "mx" },
  ZAR: { code: "ZAR", name: "Südafr. Rand",    sym: "R",  cc: "za" },
  SGD: { code: "SGD", name: "Singapur-Dollar", sym: "$",  cc: "sg" },
  HKD: { code: "HKD", name: "Hongkong-Dollar", sym: "HK$",cc: "hk" },
};
const ALL = Object.keys(CUR);
const ORDER = ALL;                       // legacy alias
const DEFAULT_PINNED = ["EUR", "USD", "GBP", "CHF"];

// rates relative to EUR
const RATES = {
  EUR: 1, USD: 1.0850, GBP: 0.8520, CHF: 0.9450, JPY: 172.0,
  AUD: 1.6300, CAD: 1.4750, CNY: 7.7400, INR: 90.50, BRL: 5.9200,
  SEK: 11.420, NOK: 11.680, MXN: 19.850, ZAR: 19.420, SGD: 1.4350, HKD: 8.4500,
};
const TARGET_RAW = 24.9;  // number the camera "sees" on the central price tag

const convert = (amt, from, to) => (amt / RATES[from]) * RATES[to];
const rateOf = (from, to) => RATES[to] / RATES[from];

const fmt = (val, code) =>
  new Intl.NumberFormat("de-DE", {
    style: "currency", currency: code,
    minimumFractionDigits: code === "JPY" ? 0 : 2,
    maximumFractionDigits: code === "JPY" ? 0 : 2,
  }).format(val);
const fmtNum = (val, code) =>
  new Intl.NumberFormat("de-DE", {
    minimumFractionDigits: code === "JPY" ? 0 : 2,
    maximumFractionDigits: code === "JPY" ? 0 : 2,
  }).format(val);
const fmtRate = (from, to) =>
  new Intl.NumberFormat("de-DE", { minimumFractionDigits: 4, maximumFractionDigits: 4 })
    .format(rateOf(from, to));

/* ---------- icons ---------- */
const IcSwap = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M7 4 4 7l3 3" /><path d="M4 7h12" /><path d="m17 20 3-3-3-3" /><path d="M20 17H8" />
  </svg>
);
const IcArrow = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
    <path d="M5 12h14" /><path d="m13 6 6 6-6 6" />
  </svg>
);
const IcCheck = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" style={{ width: 16, height: 16 }}>
    <path d="m5 13 4 4L19 7" />
  </svg>
);
const IcSearch = () => (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="11" cy="11" r="7" /><path d="m21 21-4.3-4.3" />
  </svg>
);
const IcPin = ({ filled }) => (
  <svg viewBox="0 0 24 24" width="17" height="17" fill={filled ? "currentColor" : "none"} stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 17v5" /><path d="M9 10.6V4h6v6.6l2.2 3.4H6.8L9 10.6Z" />
  </svg>
);

/* ---------- circular flag ---------- */
function Flag({ code, size = 32 }) {
  const c = CUR[code];
  return (
    <span className="flag" style={{ width: size, height: size }}>
      <img src={`https://flagcdn.com/w80/${c.cc}.png`} alt={code} loading="lazy"
           onError={(e) => { e.target.style.display = "none"; e.target.parentNode.classList.add("flag-fallback"); e.target.parentNode.dataset.sym = c.sym; }} />
    </span>
  );
}

/* ---------- status bar ---------- */
function StatusBar() {
  return (
    <div className="statusbar">
      <span>15:15</span>
      <div className="sb-icons">
        <div className="sb-net"><span style={{height:4}} /><span style={{height:6}} /><span style={{height:8}} /><span style={{height:11}} /></div>
        <svg viewBox="0 0 24 24" width="15" height="15" fill="#fff" style={{filter:"drop-shadow(0 1px 2px rgba(0,0,0,.4))"}}><path d="M12 18.5a1.4 1.4 0 1 0 0 2.8 1.4 1.4 0 0 0 0-2.8Zm0-4.2c1.5 0 2.9.6 3.9 1.6l1.5-1.5a8 8 0 0 0-10.8 0l1.5 1.5a5.5 5.5 0 0 1 3.9-1.6Zm0-4.2c2.6 0 5 1 6.8 2.8l1.5-1.5a11.5 11.5 0 0 0-16.6 0l1.5 1.5A9.5 9.5 0 0 1 12 10.1Z"/></svg>
        <div className="sb-bar"><i /></div>
      </div>
    </div>
  );
}

/* ---------- camera scene ---------- */
function Scene({ from }) {
  return (
    <div className="scene">
      <div className="scene-bg" />
      <div className="bokeh a" /><div className="bokeh b" /><div className="bokeh c" />
      <div className="ptag faint t1"><span className="pcur">{CUR[from].sym}</span><span className="pval">12,50</span></div>
      <div className="ptag faint t2"><span className="pcur">{CUR[from].sym}</span><span className="pval">8,90</span></div>
      <div className="ptag faint t3"><span className="pcur">{CUR[from].sym}</span><span className="pval">19,00</span></div>
      <div className="ptag faint t4"><span className="pcur">{CUR[from].sym}</span><span className="pval">6,40</span></div>
      <div className="target">
        <span className="tcur">{CUR[from].sym}</span>
        <span className="tval">{fmtNum(TARGET_RAW, from)}</span>
      </div>
    </div>
  );
}

/* ---------- currency chip (menu) ---------- */
function CurChip({ code, label, open, onClick }) {
  const c = CUR[code];
  return (
    <div>
      {label && <div className="menu-label">{label}</div>}
      <button className={"chip" + (open ? " open" : "")} onClick={onClick}>
        <Flag code={code} size={30} />
        <span className="cur-meta">
          <span className="cur-code">{c.code}<i className="sym">{c.sym}</i></span>
        </span>
        <span className="caret">▼</span>
      </button>
    </div>
  );
}

/* ---------- glass menu ---------- */
function Menu({ from, to, onPick, onSwap, swapping }) {
  return (
    <div className="menu">
      <div className="menu-row">
        <CurChip code={from} label="Von" open={false} onClick={() => onPick("from")} />
        <div className="swap-col">
          <div className="menu-label" aria-hidden="true">&nbsp;</div>
          <button className={"swap" + (swapping ? " spin" : "")} onClick={onSwap} aria-label="tauschen"><IcSwap /></button>
        </div>
        <CurChip code={to} label="Zu" open={false} onClick={() => onPick("to")} />
      </div>
      <div className="rate-row">
        <span className="live-dot" />
        <span className="rate-text">1 <b>{from}</b> = <b>{fmtRate(from, to)}</b> {to}</span>
        <span className="rate-live">Live</span>
      </div>
    </div>
  );
}

/* ---------- a single picker row ---------- */
function CurRow({ code, active, isPinned, full, onChoose, onTogglePin }) {
  const c = CUR[code];
  return (
    <div className={"cur-opt" + (active ? " sel" : "")}>
      <button className="cur-opt-main" onClick={() => onChoose(code)}>
        <Flag code={code} size={32} />
        <span className="cur-meta">
          <span className="cur-code">{c.code}{active && <span className="check"><IcCheck /></span>}</span>
          <span className="cur-name">{c.name}</span>
        </span>
        <span className="cur-sym-mini">{c.sym}</span>
      </button>
      <button
        className={"pin-btn" + (isPinned ? " on" : "")}
        disabled={!isPinned && full}
        onClick={() => onTogglePin(code)}
        aria-label={isPinned ? "lösen" : "anpinnen"}
        title={isPinned ? "Pin entfernen" : (full ? "Maximal 4 gepinnt" : "Anpinnen")}
      >
        <IcPin filled={isPinned} />
      </button>
    </div>
  );
}

/* ---------- picker sheet ---------- */
function Sheet({ slot, from, to, active: activeProp, title: titleProp, pinned, onTogglePin, onChoose, onClose }) {
  const [q, setQ] = useState("");
  const active = activeProp != null ? activeProp : (slot === "from" ? from : to);
  const title = titleProp != null ? titleProp : (slot === "from" ? "Eingescannte Währung" : "Zielwährung");
  const query = q.trim().toLowerCase();
  const searching = query.length > 0;
  const full = pinned.length >= 4;

  const match = (code) =>
    code.toLowerCase().includes(query) || CUR[code].name.toLowerCase().includes(query);

  const pinnedRows = pinned.filter((c) => CUR[c]);                       // fixed order
  const restRows = ALL.filter((c) => !pinned.includes(c)).sort();        // alphabetical
  const searchRows = ALL.filter(match).sort();

  const rowProps = (code) => ({
    key: code, code, active: code === active, isPinned: pinned.includes(code), full,
    onChoose, onTogglePin,
  });

  return (
    <>
      <div className="sheet-scrim" onClick={onClose} />
      <div className="sheet">
        <div className="sheet-grab" />
        <div className="sheet-title">{title}</div>
        <div className="sheet-search">
          <IcSearch />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Währung oder Code suchen" />
        </div>
        <div className="sheet-list">
          {searching ? (
            searchRows.length
              ? searchRows.map((c) => <CurRow {...rowProps(c)} />)
              : <div className="sheet-empty">Keine Treffer für „{q}“</div>
          ) : (
            <>
              <div className="sheet-section-label">Angepinnt</div>
              {pinnedRows.map((c) => <CurRow {...rowProps(c)} />)}
              <div className="sheet-divider"><span>Alle Währungen</span></div>
              {restRows.map((c) => <CurRow {...rowProps(c)} />)}
            </>
          )}
        </div>
      </div>
    </>
  );
}

/* ---------- scan state hook ---------- */
function useScan() {
  const [phase, setPhase] = useState("scanning");
  const timer = useRef(null);
  const lock = useCallback(() => {
    clearTimeout(timer.current);
    setPhase("scanning");
    timer.current = setTimeout(() => setPhase("locked"), 1000);
  }, []);
  useEffect(() => { lock(); return () => clearTimeout(timer.current); }, [lock]);
  return { phase, rescan: lock };
}

/* ---------- phone shell ---------- */
function Phone({ children }) {
  return (
    <div className="phone">
      <div className="cam-dot" />
      <div className="screen">
        {children}
        <StatusBar />
      </div>
    </div>
  );
}

Object.assign(window, {
  React, useState, useEffect, useRef, useCallback,
  CUR, ALL, ORDER, RATES, TARGET_RAW, DEFAULT_PINNED, convert, rateOf, fmt, fmtNum, fmtRate,
  IcSwap, IcArrow, IcCheck, IcSearch, IcPin, Flag,
  StatusBar, Scene, CurChip, Menu, CurRow, Sheet, useScan, Phone,
});
