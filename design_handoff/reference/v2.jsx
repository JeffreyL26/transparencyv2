/* ============================================================
   v2.jsx — Variante 2, voller interaktiver Prototyp
   Onboarding · Live-Scan · Add → Listen (Reise-Budget)
   ============================================================ */
const { useState, useEffect, useRef } = React;

/* ---------- persistence ---------- */
const LS = { pins: "fxlens_pins", lists: "fxlens_lists", onboarded: "fxlens_onboarded" };
const loadLS = (k, f) => { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : f; } catch (e) { return f; } };
const saveLS = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} };
const uid = () => Math.random().toString(36).slice(2, 9);
const sumList = (l) => l.items.reduce((a, i) => a + i.value, 0);

/* ---------- demo seed ---------- */
function seedLists() {
  const mk = (raw, from, cur) => ({ id: uid(), raw, from, value: convert(raw, from, cur), ts: Date.now() });
  return [
    { id: uid(), name: "USA Roadtrip", currency: "USD", budget: 1500,
      items: [mk(12.9, "EUR", "USD"), mk(8.5, "EUR", "USD"), mk(34, "EUR", "USD"), mk(22.4, "EUR", "USD")] },
    { id: uid(), name: "Hongkong Reise", currency: "EUR", budget: 800,
      items: [mk(188, "HKD", "EUR"), mk(65, "HKD", "EUR"), mk(240, "HKD", "EUR"), mk(42, "HKD", "EUR")] },
  ];
}

/* ---------- icons ---------- */
const IcPlus = () => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round"><path d="M12 5v14M5 12h14" /></svg>);
const IcList = () => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M8 6h12M8 12h12M8 18h12" /><circle cx="3.5" cy="6" r="1.2" fill="currentColor" stroke="none" /><circle cx="3.5" cy="12" r="1.2" fill="currentColor" stroke="none" /><circle cx="3.5" cy="18" r="1.2" fill="currentColor" stroke="none" /></svg>);
const IcClose = () => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round"><path d="M6 6l12 12M18 6 6 18" /></svg>);
const IcBack = () => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><path d="m15 6-6 6 6 6" /></svg>);
const IcChevron = () => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 6 6 6-6 6" /></svg>);
const IcTrash = () => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M4 7h16M9 7V5h6v2M6 7l1 13h10l1-13" /></svg>);
const IcGlobe = () => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3c2.8 3 2.8 15 0 18M12 3c-2.8 3-2.8 15 0 18" /></svg>);
const IcReset = () => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 12a9 9 0 1 0 3-6.7L3 8" /><path d="M3 3v5h5" /></svg>);
const IcEdit = () => (<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round"><path d="M4 20h4L19 9l-4-4L4 16v4Z" /><path d="M13.5 6.5l4 4" /></svg>);

/* ---------- small pieces ---------- */
function ScanHint({ show }) {
  return (<div className="scan-hint" style={{ opacity: show ? 1 : 0, transition: "opacity .3s" }}>
    <span className="live-dot" /> Zahl in den Rahmen halten
  </div>);
}

function Toast({ msg, vis }) {
  return (<div className={"toast" + (vis ? " show" : "")}>
    <span className="toast-dot"><IcCheck /></span>{msg}
  </div>);
}

function EdgeTab({ count, onClick }) {
  return (<button className="edge-tab" onClick={onClick} aria-label="Listen öffnen">
    <span className="et-grip" />
    <IcList />
  </button>);
}

function BudgetBar({ total, budget, currency, compact }) {
  const pct = Math.min(100, (total / budget) * 100);
  const over = total > budget;
  const rem = budget - total;
  return (<div className="budget">
    <div className="budget-track"><div className={"budget-fill" + (over ? " over" : "")} style={{ width: pct + "%" }} /></div>
    {!compact && (
      <div className="budget-meta">
        <span className="bm-left">Budget <b>{fmt(budget, currency)}</b></span>
        <span className={"bm-rem" + (over ? " over" : "")}>{over ? "+" + fmt(-rem, currency) + " über" : fmt(rem, currency) + " übrig"}</span>
      </div>
    )}
  </div>);
}

/* ---------- V2 scan overlay + result card (with Add) ---------- */
function ScanV2({ from, to, conv, phase, rescan, onAdd, dim }) {
  const locked = phase === "locked";
  return (<div className="scan-layer">
    <div className={"scan-box " + phase} onClick={rescan}>
      <span className="scan-glow" /><span className="win" />
      <span className="win-corner tl" /><span className="win-corner tr" />
      <span className="win-corner bl" /><span className="win-corner br" />
      <span className="scanline" />
      <span className="lockbadge"><IcCheck /> Erkannt</span>
    </div>
    <ScanHint show={!locked && !dim} />
    <div className={"res-card" + (locked ? " show" : "")}>
      <div className="rc-head">
        <span className="live-dot" /><span className="lbl">Umgerechnet</span>
        <span className="rate">1 {from} = <b>{fmtRate(from, to)}</b> {to}</span>
      </div>
      <div className="rc-body">
        <div className="rc-amt from"><span className="c">{from}</span><span className="v">{fmtNum(TARGET_RAW, from)}</span></div>
        <div className="rc-arrow"><IcArrow /></div>
        <div className="rc-amt to"><span className="c">{to}</span><span className="v">{fmtNum(conv, to)}</span></div>
      </div>
      <button className="rc-add" onClick={onAdd}><IcPlus /> Zu Liste hinzufügen</button>
    </div>
  </div>);
}

/* ---------- inline currency scroller (create form) ---------- */
function CurScroller({ value, onChange }) {
  return (<div style={{ display: "flex", gap: 8, overflowX: "auto", padding: "2px 2px 6px", margin: "0 -2px" }}>
    {ALL.map((code) => {
      const sel = code === value;
      return (<button key={code} onClick={() => onChange(code)} style={{
        flex: "none", display: "flex", alignItems: "center", gap: 8, padding: "8px 12px 8px 8px",
        borderRadius: 12, cursor: "pointer", fontFamily: "var(--sans)", fontWeight: 800, fontSize: 14,
        border: "1.5px solid " + (sel ? "var(--accent)" : "var(--line)"),
        background: sel ? "var(--accent-soft)" : "var(--surface)", color: "var(--ink)",
      }}><Flag code={code} size={24} />{code}</button>);
    })}
  </div>);
}

/* ---------- add-to-list sheet ---------- */
function AddSheet({ to, from, conv, lists, onAdd, onNew, onClose }) {
  return (<>
    <div className="sheet-scrim" onClick={onClose} />
    <div className="sheet">
      <div className="sheet-grab" />
      <div className="sheet-title">Zu Liste hinzufügen</div>
      <div className="amount-chip">
        <div>
          <div className="ac-conv">{fmt(conv, to)}</div>
          <div className="ac-src">aus {fmt(TARGET_RAW, from)} gescannt</div>
        </div>
        <span className="ac-flag"><Flag code={to} size={38} /></span>
      </div>
      <div className="sheet-list">
        {lists.length === 0 && (
          <div className="sheet-note">Noch keine Liste in <b>{to}</b>. Lege eine an, um Preise in dieser Währung zu sammeln.</div>
        )}
        {lists.map((l) => (
          <button key={l.id} className="list-row" onClick={() => onAdd(l.id)}>
            <Flag code={l.currency} size={34} />
            <span className="lr-body">
              <span className="lr-name">{l.name}</span>
              <span className="lr-meta">{l.items.length} Positionen</span>
            </span>
            <span className="lr-total">{fmt(sumList(l), l.currency)}</span>
          </button>
        ))}
        <button className="list-row new" onClick={onNew}><IcPlus /> Neue {to}-Liste</button>
      </div>
    </div>
  </>);
}

/* ---------- create-list sheet ---------- */
function CreateSheet({ context, currency, onCreate, onClose }) {
  const [name, setName] = useState("");
  const [cur, setCur] = useState(currency);
  const [budget, setBudget] = useState("");
  const fixed = context === "add";
  return (<>
    <div className="sheet-scrim" style={{ zIndex: 64 }} onClick={onClose} />
    <div className="sheet" style={{ zIndex: 65 }}>
      <div className="sheet-grab" />
      <div className="sheet-title">Neue Liste</div>
      <div className="create-form">
        <div className="field-label">Name</div>
        <div className="field"><input autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder="z. B. Hongkong Reise" /></div>

        <div className="field-label">Währung der Liste</div>
        {fixed ? (
          <div className="field" style={{ cursor: "default" }}>
            <Flag code={cur} size={26} />
            <span className="fp-code">{cur} · {CUR[cur].name}</span>
            <span style={{ marginLeft: "auto", fontSize: 11, color: "var(--ink-3)", fontWeight: 700 }}>fest</span>
          </div>
        ) : (
          <CurScroller value={cur} onChange={setCur} />
        )}

        <div className="field-label">Budget (optional)</div>
        <div className="field">
          <input type="number" inputMode="decimal" value={budget} onChange={(e) => setBudget(e.target.value)} placeholder="0" />
          <span className="unit">{CUR[cur].sym}</span>
        </div>

        <button className="btn-primary" disabled={!name.trim()}
          onClick={() => onCreate({ name: name.trim(), currency: cur, budget: budget ? parseFloat(budget) : null })}>
          {context === "add" ? "Erstellen & hinzufügen" : "Liste erstellen"}
        </button>
      </div>
    </div>
  </>);
}

/* ---------- edit / delete list sheet ---------- */
function EditListSheet({ list, onSave, onDelete, onClose }) {
  const [name, setName] = useState(list.name);
  const [budget, setBudget] = useState(list.budget != null ? String(list.budget) : "");
  const [confirm, setConfirm] = useState(false);
  return (<>
    <div className="sheet-scrim" style={{ zIndex: 64 }} onClick={onClose} />
    <div className="sheet" style={{ zIndex: 65 }}>
      <div className="sheet-grab" />
      <div className="sheet-title">Liste bearbeiten</div>
      <div className="create-form">
        <div className="field-label">Name</div>
        <div className="field"><input autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder="Listenname" /></div>

        <div className="field-label">Währung der Liste</div>
        <div className="field" style={{ cursor: "default" }}>
          <Flag code={list.currency} size={26} />
          <span className="fp-code">{list.currency} · {CUR[list.currency].name}</span>
          <span style={{ marginLeft: "auto", fontSize: 11, color: "var(--ink-3)", fontWeight: 700 }}>fest</span>
        </div>

        <div className="field-label">Budget (optional)</div>
        <div className="field">
          <input type="number" inputMode="decimal" value={budget} onChange={(e) => setBudget(e.target.value)} placeholder="0" />
          <span className="unit">{CUR[list.currency].sym}</span>
        </div>

        <button className="btn-primary" disabled={!name.trim()}
          onClick={() => onSave({ name: name.trim(), budget: budget ? parseFloat(budget) : null })}>Speichern</button>

        {!confirm ? (
          <button className="btn-danger" onClick={() => setConfirm(true)}>Liste löschen</button>
        ) : (
          <div className="confirm-row">
            <span>Wirklich löschen?</span>
            <div className="confirm-actions">
              <button className="btn-ghost" onClick={() => setConfirm(false)}>Abbrechen</button>
              <button className="btn-danger solid" onClick={onDelete}>Löschen</button>
            </div>
          </div>
        )}
      </div>
    </div>
  </>);
}

/* ---------- lists panel (full screen) ---------- */
function ListsPanel({ show, lists, selectedId, onSelect, onBack, onClose, onNew, onEdit, onDeleteItem }) {
  const list = lists.find((l) => l.id === selectedId);
  return (<div className={"panel" + (show ? " show" : "")}>
    {list ? (
      <>
        <div className="panel-head">
          <button className="icon-btn" onClick={onBack}><IcBack /></button>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="ph-title" style={{ marginBottom: 2 }}>{list.name}</div>
            <div className="ph-sub">{list.items.length} Positionen · {list.currency}</div>
          </div>
          <button className="icon-btn" onClick={() => onEdit(list.id)} aria-label="bearbeiten"><IcEdit /></button>
          <button className="icon-btn" onClick={onClose}><IcClose /></button>
        </div>
        <div className="panel-body">
          <div className="detail-total">
            <div className="dt-lbl">Gesamt</div>
            <div className="dt-val">{fmtNum(sumList(list), list.currency)}<span className="dt-cur">{CUR[list.currency].sym}</span></div>
            {list.budget && <BudgetBar total={sumList(list)} budget={list.budget} currency={list.currency} />}
          </div>
          <div className="section-label">Positionen</div>
          {list.items.length === 0 && <div className="sheet-note">Noch nichts hinzugefügt. Scanne einen Preis und tippe „Zu Liste hinzufügen“.</div>}
          {list.items.slice().reverse().map((it) => (
            <div key={it.id} className="item-row">
              <Flag code={it.from} size={30} />
              <div className="ir-mid">
                <div className="ir-conv">{fmt(it.value, list.currency)}</div>
                <div className="ir-src">aus {fmt(it.raw, it.from)}</div>
              </div>
              <button className="item-del" onClick={() => onDeleteItem(list.id, it.id)} aria-label="löschen"><IcTrash /></button>
            </div>
          ))}
        </div>
      </>
    ) : (
      <>
        <div className="panel-head">
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="ph-title">Meine Listen</div>
            <div className="ph-sub">Reise-Rechnungen je Zielwährung</div>
          </div>
          <button className="icon-btn" onClick={onClose}><IcClose /></button>
        </div>
        <div className="panel-body">
          {lists.length === 0 && (
            <div className="empty-state">
              <div className="es-icon"><IcList /></div>
              <div className="es-title">Noch keine Listen</div>
              <div className="es-sub">Scanne Preise und sammle sie in einer Liste deiner Zielwährung — z. B. dein Reisebudget.</div>
            </div>
          )}
          {lists.map((l) => {
            const total = sumList(l);
            return (<button key={l.id} className="lcard" onClick={() => onSelect(l.id)}>
              <Flag code={l.currency} size={44} />
              <div className="lc-main">
                <div className="lc-name">{l.name}</div>
                <div className="lc-meta">{l.items.length} Positionen · {l.currency}</div>
                {l.budget && <BudgetBar total={total} budget={l.budget} currency={l.currency} compact />}
              </div>
              <div className="lc-right">
                <div className="lc-total">{fmt(total, l.currency)}</div>
              </div>
              <span className="lc-chev"><IcChevron /></span>
            </button>);
          })}
          <button className="list-row new" onClick={onNew} style={{ marginTop: 4 }}><IcPlus /> Neue Liste</button>
        </div>
      </>
    )}
  </div>);
}

/* ---------- onboarding ---------- */
function Onboarding({ onDone }) {
  const [sel, setSel] = useState([]);
  const toggle = (code) => setSel((s) => s.includes(code) ? s.filter((x) => x !== code) : (s.length >= 4 ? s : [...s, code]));
  return (<div className="onb">
    <div className="onb-badge"><IcGlobe /></div>
    <div className="onb-title">Wähle deine Favoriten</div>
    <div className="onb-sub">Bis zu 4 Währungen für den Schnellzugriff beim Umrechnen. Du kannst das später jederzeit ändern.</div>
    <div className="onb-counter">
      <div className="dots">{[0,1,2,3].map((i) => <i key={i} className={i < sel.length ? "on" : ""} />)}</div>
      {sel.length}/4 ausgewählt
    </div>
    <div className="onb-grid">
      {ALL.map((code) => {
        const on = sel.includes(code);
        const dis = !on && sel.length >= 4;
        return (<button key={code} className={"onb-tile" + (on ? " sel" : "") + (dis ? " disabled" : "")} onClick={() => !dis && toggle(code)}>
          <Flag code={code} size={32} />
          <span className="ot-meta"><span className="ot-code">{code}</span><span className="ot-name">{CUR[code].name}</span></span>
          <span className="ot-check"><IcCheck /></span>
        </button>);
      })}
    </div>
    <button className="btn-primary" disabled={sel.length === 0} onClick={() => onDone(sel)}>
      {sel.length === 0 ? "Mindestens 1 wählen" : "Los geht’s"}
    </button>
  </div>);
}

/* ============================================================
   the phone app
   ============================================================ */
function ScannerApp({ onboarded, completeOnboarding, pins, togglePin, lists, addItemToList, createList, updateList, deleteList, deleteItem }) {
  const [from, setFrom] = useState("EUR");
  const [to, setTo] = useState("USD");
  const [picker, setPicker] = useState(null);     // 'from' | 'to' | null
  const [addOpen, setAddOpen] = useState(false);
  const [creating, setCreating] = useState(null); // { context, currency } | null
  const [panel, setPanel] = useState(false);
  const [selectedId, setSelectedId] = useState(null);
  const [editing, setEditing] = useState(null);   // listId being edited | null
  const [swapping, setSwapping] = useState(false);
  const [toast, setToast] = useState({ msg: "", vis: false });
  const toastT = useRef(null);
  const { phase, rescan } = useScan();

  const conv = convert(TARGET_RAW, from, to);
  const showToast = (msg) => { clearTimeout(toastT.current); setToast({ msg, vis: true }); toastT.current = setTimeout(() => setToast((t) => ({ ...t, vis: false })), 2200); };

  const doSwap = () => { setSwapping(true); setFrom(to); setTo(from); rescan(); setTimeout(() => setSwapping(false), 380); };
  const choose = (code) => {
    if (picker === "from") { setFrom(code); if (code === to) setTo(from); }
    else { setTo(code); if (code === from) setFrom(to); }
    setPicker(null); rescan();
  };

  const matching = lists.filter((l) => l.currency === to);
  const addToExisting = (listId) => {
    const item = { id: uid(), raw: TARGET_RAW, from, value: conv, ts: Date.now() };
    addItemToList(listId, item);
    const l = lists.find((x) => x.id === listId);
    setAddOpen(false); showToast(`Zu „${l.name}“ hinzugefügt`);
  };
  const doCreate = ({ name, currency, budget }) => {
    if (creating.context === "add") {
      const item = { id: uid(), raw: TARGET_RAW, from, value: convert(TARGET_RAW, from, currency), ts: Date.now() };
      createList({ name, currency, budget, firstItem: item });
      showToast(`Zu „${name}“ hinzugefügt`);
    } else {
      const id = createList({ name, currency, budget });
      setSelectedId(id); setPanel(true);
    }
    setCreating(null); setAddOpen(false);
  };

  return (<Phone>
    <Scene from={from} />
    <ScanV2 from={from} to={to} conv={conv} phase={phase} rescan={rescan}
            dim={!!(picker || addOpen || creating || panel)}
            onAdd={() => phase === "locked" && setAddOpen(true)} />
    <Menu from={from} to={to} swapping={swapping} onPick={(s) => setPicker(s)} onSwap={doSwap} />
    <EdgeTab count={lists.length} onClick={() => { setSelectedId(null); setPanel(true); }} />

    {picker && <Sheet slot={picker} from={from} to={to} pinned={pins} onTogglePin={togglePin} onChoose={choose} onClose={() => setPicker(null)} />}
    {addOpen && <AddSheet to={to} from={from} conv={conv} lists={matching}
                          onAdd={addToExisting} onNew={() => { setAddOpen(false); setCreating({ context: "add", currency: to }); }}
                          onClose={() => setAddOpen(false)} />}
    {creating && <CreateSheet context={creating.context} currency={creating.currency} onCreate={doCreate} onClose={() => setCreating(null)} />}

    <ListsPanel show={panel} lists={lists} selectedId={selectedId}
                onSelect={setSelectedId} onBack={() => setSelectedId(null)}
                onClose={() => { setPanel(false); setSelectedId(null); }}
                onNew={() => setCreating({ context: "panel", currency: to })}
                onEdit={(id) => setEditing(id)}
                onDeleteItem={deleteItem} />

    {editing && (() => {
      const l = lists.find((x) => x.id === editing);
      if (!l) return null;
      return <EditListSheet list={l}
        onSave={(patch) => { updateList(editing, patch); setEditing(null); }}
        onDelete={() => { deleteList(editing); setEditing(null); setSelectedId(null); }}
        onClose={() => setEditing(null)} />;
    })()}

    <Toast msg={toast.msg} vis={toast.vis} />
    {!onboarded && <Onboarding onDone={completeOnboarding} />}
  </Phone>);
}

/* ============================================================
   page + persistent store
   ============================================================ */
function App() {
  const [onboarded, setOnboarded] = useState(() => loadLS(LS.onboarded, false));
  const [pins, setPins] = useState(() => loadLS(LS.pins, [...DEFAULT_PINNED]));
  const [lists, setLists] = useState(() => { const s = loadLS(LS.lists, null); return s || seedLists(); });

  useEffect(() => saveLS(LS.onboarded, onboarded), [onboarded]);
  useEffect(() => saveLS(LS.pins, pins), [pins]);
  useEffect(() => saveLS(LS.lists, lists), [lists]);

  const togglePin = (code) => setPins((p) => p.includes(code) ? p.filter((x) => x !== code) : (p.length >= 4 ? p : [...p, code]));
  const completeOnboarding = (selected) => { setPins(selected); setOnboarded(true); };
  const addItemToList = (listId, item) => setLists((ls) => ls.map((l) => l.id === listId ? { ...l, items: [...l.items, item] } : l));
  const deleteItem = (listId, itemId) => setLists((ls) => ls.map((l) => l.id === listId ? { ...l, items: l.items.filter((i) => i.id !== itemId) } : l));
  const updateList = (id, patch) => setLists((ls) => ls.map((l) => l.id === id ? { ...l, ...patch } : l));
  const deleteList = (id) => setLists((ls) => ls.filter((l) => l.id !== id));
  const createList = ({ name, currency, budget, firstItem }) => {
    const id = uid();
    setLists((ls) => [{ id, name, currency, budget: budget || null, items: firstItem ? [firstItem] : [] }, ...ls]);
    return id;
  };
  const reset = () => {
    try { localStorage.removeItem(LS.onboarded); localStorage.removeItem(LS.pins); localStorage.removeItem(LS.lists); } catch (e) {}
    setOnboarded(false); setPins([...DEFAULT_PINNED]); setLists(seedLists());
  };

  return (<div className="page">
    <div className="page-head">
      <span className="eyebrow">Variante 2 · Interaktiver Prototyp</span>
      <h1 className="page-title">Scannen, umrechnen, ins Reisebudget</h1>
      <p className="page-sub">
        Der gescannte Preis wird live umgerechnet und lässt sich per „+“ in eine Liste deiner Zielwährung legen —
        eine laufende Rechnung aller Ausgaben, z. B. fürs Reisebudget. Jede Liste ist an genau eine Zielwährung
        gebunden; Preise anderer Währungen lassen sich dort nicht ablegen. Listen öffnest du über den Tab am rechten Rand.
      </p>
      <div className="flow-legend">
        <span className="flow-step"><b>1</b> Onboarding · bis zu 4 Favoriten pinnen</span>
        <span className="flow-step"><b>2</b> Live scannen &amp; umrechnen</span>
        <span className="flow-step"><b>3</b> „+“ ins Listen-Budget legen</span>
        <span className="flow-step"><b>4</b> Edge-Tab → Reise-Rechnung &amp; Budget</span>
      </div>
      <button className="demo-reset" onClick={reset}><IcReset /> Demo zurücksetzen (Onboarding erneut zeigen)</button>
    </div>
    <div className="row">
      <ScannerApp
        onboarded={onboarded} completeOnboarding={completeOnboarding}
        pins={pins} togglePin={togglePin}
        lists={lists} addItemToList={addItemToList} createList={createList}
        updateList={updateList} deleteList={deleteList} deleteItem={deleteItem}
      />
    </div>
  </div>);
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
