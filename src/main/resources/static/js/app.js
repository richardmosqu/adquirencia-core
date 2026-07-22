/* ============================================================
   Core de Adquirencia — PagueloFacil
   SPA sin dependencias: estado, API, gráficas SVG y vistas.
   ============================================================ */

(() => {
  "use strict";

  // ---------- constantes ----------

  // Fuente única de color: se leen las variables de marca definidas en :root
  // (app.css) para no repetir HEX en el JS de las gráficas.
  const rootStyle = getComputedStyle(document.documentElement);
  const cssVar = (name) => rootStyle.getPropertyValue(name).trim();

  const SERIES = ["--series-1", "--series-2", "--series-3", "--series-4"].map(cssVar);
  // Colores de marca para los canales (donut): verde primario y teal del manual.
  const CHANNEL_COLOR = {
    POWERTRANZ: cssVar("--green-500"),
    EVERTEC: cssVar("--teal-700"),
  };
  // Paleta categórica de marca para el donut de servicios (verdes + teal + ámbar).
  const SERVICE_COLORS = ["--green-500", "--teal-500", "--amber-500", "--green-800", "--teal-700"].map(cssVar);
  const THREEDS_COLORS = ["--green-500", "--teal-700"].map(cssVar);
  const CHART = {
    grid: cssVar("--grid"),
    axis: cssVar("--axis-text"),
    baseline: cssVar("--chart-baseline"),
    crosshair: cssVar("--chart-crosshair"),
    barLabel: cssVar("--bar-label"),
    barValue: cssVar("--bar-value"),
  };
  const SERVICES = [
    { value: "AUTH_CAPTURE", label: "Auth/Capture" },
    { value: "RECURRENCIA", label: "Recurrencia" },
    { value: "LINK_PAGO", label: "Link de pago" },
    { value: "CHECKOUT", label: "Checkout" },
    { value: "API", label: "API" },
  ];
  const CONDITIONS = [
    { value: "REJECT_RATE_ABOVE", label: "Tasa de rechazo mayor a umbral (%)" },
    { value: "DECLINED_COUNT_ABOVE", label: "Rechazadas en el día mayores a umbral" },
    { value: "REFUND_RATE_ABOVE", label: "Índice de reembolsos mayor a umbral (%)" },
    { value: "THREE_DS_FAILURE_RATE_ABOVE", label: "Fallas 3DS mayores a umbral (%)" },
    { value: "DR_CODE_RECURRENT", label: "Código DR recurrente (ocurrencias)" },
    { value: "CHANNEL_VOLUME_DROP", label: "Caída de volumen de canal (%)" },
    { value: "INTERNAL_ERRORS_ABOVE", label: "Errores internos mayores a umbral" },
  ];
  const SEVERITY_LABEL = { WARNING: "Atención", SERIOUS: "Serio", CRITICAL: "Crítico" };
  const SEVERITY_CLASS = { WARNING: "warning", SERIOUS: "serious", CRITICAL: "critical" };

  const state = {
    merchants: [],
    drCodes: {},
    compareSelection: [],
    alerts: [],
    actionPlans: [],
    credentials: [],
    credView: "table",
    credDetailId: null,
    view: "dashboard",
  };

  // ---------- utilidades ----------

  const $ = (sel) => document.querySelector(sel);

  const fmtMoney = new Intl.NumberFormat("es-PA", { style: "currency", currency: "USD", maximumFractionDigits: 0 });
  const fmtMoneyC = new Intl.NumberFormat("es-PA", { style: "currency", currency: "USD" });
  const fmtInt = new Intl.NumberFormat("es-PA");
  const fmtDay = new Intl.DateTimeFormat("es-PA", { day: "numeric", month: "short" });
  const fmtDate = new Intl.DateTimeFormat("es-PA", { day: "numeric", month: "short", year: "numeric" });
  const fmtDateTime = new Intl.DateTimeFormat("es-PA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

  const money = (v) => fmtMoney.format(Number(v || 0));
  const moneyC = (v) => fmtMoneyC.format(Number(v || 0));
  const int = (v) => fmtInt.format(Number(v || 0));
  const pct = (v) => (v == null ? "—" : `${Number(v).toLocaleString("es-PA", { maximumFractionDigits: 1 })}%`);
  const parseDay = (s) => new Date(`${s}T12:00:00`);

  const esc = (s) => String(s ?? "")
    .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");

  async function api(path, opts) {
    const res = await fetch(path, opts);
    if (!res.ok) throw new Error(`${res.status} ${res.statusText} en ${path}`);
    return res.status === 204 ? null : res.json();
  }

  function merchantName(id) {
    const m = state.merchants.find((x) => x.id === id);
    return m ? m.name : id ?? "—";
  }

  // Todo comercio se muestra como "Nombre (ID)". Los canales NO usan este formato.
  const nameId = (name, id) => (id ? `${name} (${id})` : (name ?? "—"));
  function merchantLabel(id) {
    const m = state.merchants.find((x) => x.id === id);
    return m ? `${m.name} (${m.id})` : (id ?? "—");
  }

  // ---------- tooltip ----------

  const tooltip = $("#tooltip");

  function showTooltip(html, x, y) {
    tooltip.innerHTML = html;
    tooltip.hidden = false;
    const pad = 14;
    const rect = tooltip.getBoundingClientRect();
    let left = x + pad;
    let top = y + pad;
    if (left + rect.width > window.innerWidth - 8) left = x - rect.width - pad;
    if (top + rect.height > window.innerHeight - 8) top = y - rect.height - pad;
    tooltip.style.left = `${left}px`;
    tooltip.style.top = `${top}px`;
  }

  const hideTooltip = () => { tooltip.hidden = true; };

  // ---------- chips de estado (ícono + etiqueta) ----------

  const CHIP_ICONS = {
    good: '<svg viewBox="0 0 16 16"><path fill="currentColor" d="M6.5 11.2 3.3 8l1-1 2.2 2.1L11.7 4l1 1z"/></svg>',
    warning: '<svg viewBox="0 0 16 16"><path fill="currentColor" d="M8 1.5 15 14H1L8 1.5zm-.7 4.5v3.8h1.4V6H7.3zm0 5v1.4h1.4V11H7.3z"/></svg>',
    serious: '<svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="6.5" fill="none" stroke="currentColor" stroke-width="1.6"/><path fill="currentColor" d="M7.3 4.5h1.4V9H7.3zm0 5.6h1.4v1.4H7.3z"/></svg>',
    critical: '<svg viewBox="0 0 16 16"><path fill="currentColor" d="m4 3 4 4 4-4 1 1-4 4 4 4-1 1-4-4-4 4-1-1 4-4-4-4z"/></svg>',
    neutral: '<svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="3" fill="currentColor"/></svg>',
  };

  const STATUS_KIND = {
    "Activa": "good", "Operativo": "good", "Completado": "good", "En curso": "good",
    "Pendiente": "warning", "Por instalar": "warning", "Por iniciar": "warning", "En pausa": "warning",
    "Vencida": "serious", "En reparación": "serious", "Sin conexión": "serious",
    "Error de configuración": "critical",
  };

  function chip(text, kind) {
    const k = kind || STATUS_KIND[text] || "neutral";
    return `<span class="chip ${k}">${CHIP_ICONS[k]}${esc(text)}</span>`;
  }

  // ---------- gráficas SVG ----------

  const SVG_NS = "http://www.w3.org/2000/svg";

  function svgEl(name, attrs = {}) {
    const el = document.createElementNS(SVG_NS, name);
    for (const [k, v] of Object.entries(attrs)) el.setAttribute(k, v);
    return el;
  }

  function niceMax(v) {
    if (v <= 0) return 1;
    const exp = Math.pow(10, Math.floor(Math.log10(v)));
    const f = v / exp;
    const nf = f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10;
    return nf * exp;
  }

  /** Serie diaria de volumen: área + línea con crosshair y tooltip. */
  function lineChart(container, daily) {
    container.innerHTML = "";
    if (!daily.length) { container.innerHTML = '<div class="empty">Aún no hay datos en este periodo. Prueba con otro rango.</div>'; return; }

    const W = 820, H = 250, ml = 58, mr = 14, mt = 12, mb = 30;
    const iw = W - ml - mr, ih = H - mt - mb;
    const values = daily.map((d) => Number(d.volume));
    const max = niceMax(Math.max(...values));
    const x = (i) => ml + (daily.length === 1 ? iw / 2 : (i * iw) / (daily.length - 1));
    const y = (v) => mt + ih - (v / max) * ih;

    const svg = svgEl("svg", { viewBox: `0 0 ${W} ${H}`, role: "img",
      "aria-label": "Volumen procesado por día" });

    for (let g = 0; g <= 4; g++) {
      const gy = mt + (ih * g) / 4;
      svg.append(svgEl("line", { x1: ml, x2: W - mr, y1: gy, y2: gy, stroke: CHART.grid, "stroke-width": 1 }));
      const t = svgEl("text", { x: ml - 8, y: gy + 4, "text-anchor": "end", "font-size": 11, fill: CHART.axis });
      t.textContent = int(max * (1 - g / 4));
      svg.append(t);
    }

    const step = Math.max(1, Math.ceil(daily.length / 7));
    daily.forEach((d, i) => {
      if (i % step !== 0 && i !== daily.length - 1) return;
      const t = svgEl("text", { x: x(i), y: H - 8, "text-anchor": "middle", "font-size": 11, fill: CHART.axis });
      t.textContent = fmtDay.format(parseDay(d.date));
      svg.append(t);
    });

    const linePts = values.map((v, i) => `${x(i)},${y(v)}`).join(" ");
    const areaD = `M ${ml},${mt + ih} L ${linePts.replaceAll(" ", " L ")} L ${x(daily.length - 1)},${mt + ih} Z`;
    svg.append(svgEl("path", { d: areaD, fill: SERIES[0], opacity: 0.12 }));
    svg.append(svgEl("polyline", { points: linePts, fill: "none", stroke: SERIES[0], "stroke-width": 2, "stroke-linejoin": "round" }));
    svg.append(svgEl("line", { x1: ml, x2: W - mr, y1: mt + ih, y2: mt + ih, stroke: CHART.baseline, "stroke-width": 1 }));

    const cross = svgEl("line", { y1: mt, y2: mt + ih, stroke: CHART.crosshair, "stroke-width": 1, "stroke-dasharray": "3 3", visibility: "hidden" });
    const dot = svgEl("circle", { r: 4.5, fill: SERIES[0], stroke: "#fff", "stroke-width": 2, visibility: "hidden" });
    svg.append(cross, dot);

    const overlay = svgEl("rect", { x: ml, y: mt, width: iw, height: ih, fill: "transparent" });
    overlay.addEventListener("mousemove", (ev) => {
      const rect = svg.getBoundingClientRect();
      const px = ((ev.clientX - rect.left) / rect.width) * W;
      const i = Math.max(0, Math.min(daily.length - 1,
        Math.round(((px - ml) / iw) * (daily.length - 1))));
      const d = daily[i];
      cross.setAttribute("x1", x(i)); cross.setAttribute("x2", x(i));
      cross.setAttribute("visibility", "visible");
      dot.setAttribute("cx", x(i)); dot.setAttribute("cy", y(Number(d.volume)));
      dot.setAttribute("visibility", "visible");
      showTooltip(
        `<div class="tt-title">${fmtDate.format(parseDay(d.date))}</div>` +
        `<div>Volumen: <b>${moneyC(d.volume)}</b></div>` +
        `<div class="tt-muted">${int(d.txCount)} trx · ${int(d.approved)} aprobadas · ${int(d.declined)} rechazadas</div>`,
        ev.clientX, ev.clientY);
    });
    overlay.addEventListener("mouseleave", () => {
      cross.setAttribute("visibility", "hidden");
      dot.setAttribute("visibility", "hidden");
      hideTooltip();
    });
    svg.append(overlay);
    container.append(svg);
  }

  /**
   * Barras horizontales con extremo de dato redondeado, etiqueta directa y
   * tooltip por barra. items: {label, value, display, color, tooltip}
   */
  function hBars(container, items, { legend = null, valueSuffix = "", width = 420, labelWidth = 118 } = {}) {
    container.innerHTML = "";
    if (!items.length || items.every((i) => !i.value)) {
      container.innerHTML = '<div class="empty">Aún no hay datos en este periodo. Prueba con otro rango.</div>';
      return;
    }

    if (legend) {
      const lg = document.createElement("div");
      lg.className = "legend";
      lg.innerHTML = legend.map((l) =>
        `<span class="key"><span class="swatch" style="background:${l.color}"></span>${esc(l.label)}</span>`).join("");
      container.append(lg);
    }

    const W = width, rowH = 34, labelW = labelWidth, valueW = 74;
    const H = items.length * rowH + 6;
    const max = niceMax(Math.max(...items.map((i) => i.value)));
    const bw = W - labelW - valueW - 10;

    const svg = svgEl("svg", { viewBox: `0 0 ${W} ${H}`, role: "img" });

    items.forEach((item, idx) => {
      const cy = idx * rowH + rowH / 2 + 3;
      const label = svgEl("text", { x: labelW - 8, y: cy + 4, "text-anchor": "end", "font-size": 12, fill: CHART.barLabel });
      label.textContent = item.label;
      svg.append(label);

      const w = Math.max(2, (item.value / max) * bw);
      const barY = cy - 9;
      // extremo del dato redondeado (4px), base plana
      const r = Math.min(4, w);
      const d = `M ${labelW} ${barY} h ${w - r} a ${r} ${r} 0 0 1 ${r} ${r} v ${18 - 2 * r} a ${r} ${r} 0 0 1 -${r} ${r} h -${w - r} Z`;
      const bar = svgEl("path", { d, fill: item.color });
      svg.append(bar);

      const val = svgEl("text", { x: labelW + w + 8, y: cy + 4, "font-size": 12, "font-weight": 600, fill: CHART.barValue });
      val.textContent = item.display + valueSuffix;
      svg.append(val);

      const hit = svgEl("rect", { x: 0, y: idx * rowH + 3, width: W, height: rowH - 2, fill: "transparent" });
      if (item.tooltip) {
        hit.addEventListener("mousemove", (ev) => showTooltip(item.tooltip, ev.clientX, ev.clientY));
        hit.addEventListener("mouseleave", hideTooltip);
        bar.addEventListener("mousemove", (ev) => showTooltip(item.tooltip, ev.clientX, ev.clientY));
        bar.addEventListener("mouseleave", hideTooltip);
      }
      svg.append(hit);
    });

    container.append(svg);
  }

  // ---------- carga inicial ----------

  async function init() {
    const [merchants, drCodes] = await Promise.all([
      api("/api/merchants"),
      api("/api/dr-codes"),
    ]);
    state.merchants = merchants;
    state.drCodes = drCodes;

    const mSel = $("#f-merchant");
    merchants.forEach((m) => mSel.append(new Option(`${m.name} (${m.id})`, m.id)));

    const sSel = $("#f-service");
    SERVICES.forEach((s) => sSel.append(new Option(s.label, s.value)));

    const rSel = $("#r-merchant");
    merchants.forEach((m) => rSel.append(new Option(`${m.name} (${m.id})`, m.id)));

    const cSel = $("#r-condition");
    CONDITIONS.forEach((c) => cSel.append(new Option(c.label, c.value)));

    const dSel = $("#r-drcode");
    Object.entries(drCodes).forEach(([code, desc]) => dSel.append(new Option(`${code} — ${desc}`, code)));

    setupCompare();
    wireEvents();
    switchView("dashboard");
    await loadDashboard();
    $("#last-updated").textContent =
      `Actualizado ${fmtDateTime.format(new Date())}`;
    // deep-link opcional: #alert=<id> abre el detalle; #<vista> abre esa vista
    handleHash();
    window.addEventListener("hashchange", handleHash);
  }

  function wireEvents() {
    // dock flotante: navegación plana
    document.querySelectorAll(".dock-item").forEach((item) => {
      item.addEventListener("click", () => switchView(item.dataset.view));
    });

    // la píldora de alertas del topbar abre el panel y despliega la alerta top
    $("#alert-pill").addEventListener("click", (ev) => {
      ev.preventDefault();
      openTopAlert();
    });

    $("#f-range").addEventListener("change", () => {
      $("#f-custom").hidden = $("#f-range").value !== "custom";
      if ($("#f-range").value !== "custom") loadDashboard();
    });
    $("#f-merchant").addEventListener("change", loadDashboard);
    $("#f-service").addEventListener("change", loadDashboard);
    $("#f-from").addEventListener("change", loadDashboard);
    $("#f-to").addEventListener("change", loadDashboard);

    $("#r-scope").addEventListener("change", () => {
      $("#r-merchant-wrap").hidden = $("#r-scope").value !== "MERCHANT";
    });
    $("#r-condition").addEventListener("change", () => {
      $("#r-drcode-wrap").hidden = $("#r-condition").value !== "DR_CODE_RECURRENT";
    });
    $("#rule-form").addEventListener("submit", onCreateRule);
    $("#ap-form").addEventListener("submit", onCreatePlan);

    $("#cred-view-table").addEventListener("click", () => setCredView("table"));
    $("#cred-view-kanban").addEventListener("click", () => setCredView("kanban"));
    $("#cred-status-filter").addEventListener("change", renderCredBody);
    $("#cred-bank-filter").addEventListener("change", renderCredBody);
    $("#cred-merchant-filter").addEventListener("change", renderCredBody);
    $("#cred-refund-filter").addEventListener("change", renderCredBody);
    $("#cred-new").addEventListener("click", () => openCredForm(null));

    // copiar al portapapeles cualquier campo con [data-copy]
    document.addEventListener("click", (ev) => {
      const btn = ev.target.closest("[data-copy]");
      if (!btn) return;
      if (navigator.clipboard) navigator.clipboard.writeText(btn.dataset.copy).catch(() => {});
      const prev = btn.innerHTML;
      btn.innerHTML = CHECK_ICON;
      btn.classList.add("copied");
      setTimeout(() => { btn.innerHTML = prev; btn.classList.remove("copied"); }, 1100);
    });
    // Esc cierra el modal abierto
    document.addEventListener("keydown", (ev) => {
      if (ev.key === "Escape" && !$("#modal-root").hidden) closeModal();
    });

    $("#calc-form").addEventListener("submit", onCalcSubmit);
    $("#calc-reset").addEventListener("click", () => { $("#calc-result").innerHTML = ""; });
    $("#acq-form").addEventListener("submit", onAcqSubmit);
    $("#acq-cancel").addEventListener("click", resetAcqForm);
  }

  function switchView(view) {
    state.view = view;
    document.querySelectorAll(".dock-item").forEach((t) =>
      t.classList.toggle("active", t.dataset.view === view));
    document.querySelectorAll(".view").forEach((v) => { v.hidden = true; });
    $(`#view-${view}`).hidden = false;
    $(".content").scrollTop = 0;

    if (view === "projects") loadProjects();
    if (view === "credentials") loadCredentials();
    if (view === "points") loadPoints();
    if (view === "documents") loadDocuments();
    if (view === "profitability") loadProfitability();
    if (view === "alert-config") loadRulesAndPlans();
  }

  const KNOWN_VIEWS = ["dashboard", "alert-config", "profitability", "projects", "credentials", "points", "documents"];

  // Deep-link por hash: #alert=<id> / #cred=<id> abren el detalle; #<vista> abre la vista.
  function handleHash() {
    const h = (location.hash || "").replace(/^#/, "");
    if (!h) return;
    if (h.startsWith("alert=")) { loadAlertDetail(decodeURIComponent(h.slice(6))); return; }
    if (h.startsWith("cred=")) { openCredFromHash(decodeURIComponent(h.slice(5))); return; }
    if (h === "credentials-kanban") { switchView("credentials"); setTimeout(() => setCredView("kanban"), 500); return; }
    if (KNOWN_VIEWS.includes(h)) switchView(h);
  }

  async function openCredFromHash(id) {
    if (!state.credentials.length) await loadCredentials();
    openCredDetail(id);
  }

  // Abre el dashboard y despliega el detalle inline de la alerta más importante.
  function openTopAlert() {
    const active = state.alerts.filter((a) => !a.acknowledged);
    switchView("dashboard");
    setTimeout(() => {
      const card = $("#alert-list") && $("#alert-list").closest(".card");
      if (card) card.scrollIntoView({ behavior: "smooth", block: "center" });
      if (active.length) expandAlert(active[0].id);
    }, 60);
  }

  // ---------- filtros ----------

  function currentRange() {
    const sel = $("#f-range").value;
    const today = new Date();
    const iso = (d) => d.toISOString().slice(0, 10);
    if (sel === "custom") {
      return { from: $("#f-from").value || null, to: $("#f-to").value || null };
    }
    if (sel === "today") return { from: iso(today), to: iso(today) };
    if (sel === "mtd") {
      return { from: iso(new Date(today.getFullYear(), today.getMonth(), 1, 12)), to: iso(today) };
    }
    const days = Number(sel);
    const from = new Date(today);
    from.setDate(from.getDate() - (days - 1));
    return { from: iso(from), to: iso(today) };
  }

  function filterParams() {
    const { from, to } = currentRange();
    const p = new URLSearchParams();
    if ($("#f-merchant").value) p.set("merchantId", $("#f-merchant").value);
    if ($("#f-service").value) p.set("service", $("#f-service").value);
    if (from) p.set("from", from);
    if (to) p.set("to", to);
    return p;
  }

  // ---------- dashboard ----------

  async function loadDashboard() {
    const [summary, alerts] = await Promise.all([
      api(`/api/dashboard/summary?${filterParams()}`),
      api("/api/alerts"),
    ]);
    state.alerts = alerts;
    renderKpis(summary, alerts);
    renderAlertBanner(alerts);
    renderAlerts(alerts);
    lineChart($("#chart-volume"), summary.daily);
    $("#volume-range-label").textContent =
      `${fmtDate.format(parseDay(summary.range.from))} — ${fmtDate.format(parseDay(summary.range.to))}`;
    renderProcessors(summary);
    renderServices(summary);
    render3ds(summary);
    renderDrTable(summary);
    await renderCompare();
  }

  function renderKpis(s, alerts) {
    const activeAlerts = alerts.filter((a) => !a.acknowledged);
    const critical = activeAlerts.filter((a) => a.severity === "CRITICAL").length;

    // Señalización: umbrales calibrados sobre los datos demo (aprobación
    // normal 88–93 %, reembolsos 1–4 %). Solo salta ante degradación real.
    const approval = s.tx.approvalRate;
    const declineRisk = approval != null && approval < 85;
    const refundRisk = s.refunds.pctOfVolume != null && s.refunds.pctOfVolume > 5;

    const delta = s.volume.deltaPct;
    const deltaHtml = delta == null ? '<span class="tt-muted">sin periodo previo</span>'
      : `<span class="delta ${delta >= 0 ? "up" : "down"}">${delta >= 0 ? "▲" : "▼"} ${pct(Math.abs(delta))}</span> vs periodo anterior`;

    const approvalHtml = approval == null ? "—"
      : `<b class="${declineRisk ? "sig-bad" : "sig-ok"}">${pct(approval)} aprobación</b>`;

    $("#kpis").innerHTML = `
      <div class="kpi" style="--kpi-accent:${SERIES[0]}">
        <div class="kpi-label">Volumen procesado</div>
        <div class="kpi-value">${money(s.volume.total)}</div>
        <div class="kpi-foot">${deltaHtml}</div>
      </div>
      <div class="kpi ${declineRisk ? "risk" : ""}" style="--kpi-accent:${declineRisk ? "var(--status-critical)" : SERIES[1]}">
        <div class="kpi-label">Transacciones</div>
        <div class="kpi-value">${int(s.tx.total)}</div>
        <div class="kpi-foot">${int(s.tx.approved)} aprobadas · <span class="sig-bad">${int(s.tx.declined)} rechazadas</span> · ${approvalHtml}</div>
      </div>
      <div class="kpi ${refundRisk ? "risk" : ""}" style="--kpi-accent:${refundRisk ? "var(--status-warning)" : SERIES[3]}">
        <div class="kpi-label">Índice de reembolsos</div>
        <div class="kpi-value">${pct(s.refunds.pctOfVolume)}</div>
        <div class="kpi-foot">${moneyC(s.refunds.amount)} en ${int(s.refunds.count)} reembolsos</div>
      </div>
      <div class="kpi ${critical ? "risk" : ""}" style="--kpi-accent:${critical ? "var(--status-critical)" : (activeAlerts.length ? "var(--status-warning)" : "var(--status-good)")}">
        <div class="kpi-label">Alertas activas</div>
        <div class="kpi-value">${activeAlerts.length}</div>
        <div class="kpi-foot">${critical ? `<span class="sig-bad">${critical} crítica${critical > 1 ? "s" : ""}, la revisamos contigo</span>` : (activeAlerts.length ? "ninguna crítica, todo bajo control" : "todo en orden por ahora")}</div>
      </div>`;

    const pill = $("#alert-pill");
    pill.hidden = activeAlerts.length === 0;
    $("#alert-pill-count").textContent =
      `${activeAlerts.length} alerta${activeAlerts.length !== 1 ? "s" : ""}`;

    const sub = $("#alert-count-sub");
    if (sub) {
      sub.textContent = activeAlerts.length
        ? `${activeAlerts.length} activa${activeAlerts.length !== 1 ? "s" : ""}${critical ? ` · ${critical} crítica${critical > 1 ? "s" : ""}` : ""}`
        : "";
    }
  }

  // ícono de "ubicación / comercio" para señalar dónde ocurre la alerta
  const WHERE_ICON = '<svg viewBox="0 0 16 16"><path fill="currentColor" d="M8 1.5a4.5 4.5 0 0 0-4.5 4.5c0 3.3 4.5 8.5 4.5 8.5s4.5-5.2 4.5-8.5A4.5 4.5 0 0 0 8 1.5zm0 6.2A1.7 1.7 0 1 1 8 4.3a1.7 1.7 0 0 1 0 3.4z"/></svg>';

  // Señala claramente el comercio/canal donde ocurre la alerta.
  function alertWhere(a) {
    if (a.merchantName) return `<span class="alert-where">${WHERE_ICON}Comercio: <b>${esc(nameId(a.merchantName, a.merchantId))}</b></span>`;
    if (a.processor) {
      const label = a.processor === "POWERTRANZ" ? "PowerTranz" : a.processor === "EVERTEC" ? "Evertec" : a.processor;
      return `<span class="alert-where">${WHERE_ICON}Canal: <b>${esc(label)}</b></span>`;
    }
    return `<span class="alert-where">${WHERE_ICON}Alcance: <b>Todos los comercios</b></span>`;
  }

  // Banner ancho arriba de los KPIs: comunica que hay una alerta por revisar.
  function renderAlertBanner(alerts) {
    const el = $("#alert-banner");
    const active = alerts.filter((a) => !a.acknowledged);
    if (!active.length) {
      el.className = "alert-banner sev-good";
      el.innerHTML = `
        <div class="ab-ico">${CHIP_ICONS.good}</div>
        <div class="ab-body">
          <div class="ab-kicker">Todo en orden</div>
          <div class="ab-msg">No hay alertas activas ahora mismo. Seguimos monitoreando tu procesamiento por ti.</div>
        </div>`;
      return;
    }
    // el backend ya ordena por severidad desc: la primera es la más importante
    const top = active[0];
    const critical = active.filter((a) => a.severity === "CRITICAL").length;
    const sev = SEVERITY_CLASS[top.severity];
    const where = top.merchantName
      ? ` en <b>${esc(nameId(top.merchantName, top.merchantId))}</b>`
      : (top.processor ? ` en el canal <b>${esc(top.processor === "POWERTRANZ" ? "PowerTranz" : "Evertec")}</b>` : "");
    el.className = `alert-banner sev-${sev}`;
    el.innerHTML = `
      <div class="ab-ico">${CHIP_ICONS[sev]}</div>
      <div class="ab-body">
        <div class="ab-kicker">${active.length} alerta${active.length !== 1 ? "s" : ""} por revisar${critical ? ` · ${critical} crítica${critical > 1 ? "s" : ""}` : ""}</div>
        <div class="ab-msg"><b>${esc(top.ruleName)}</b>${where} — ${esc(top.message)}</div>
      </div>
      <button class="btn btn-primary" id="ab-review">Revisar</button>`;

    $("#ab-review").addEventListener("click", () => {
      const card = $("#alert-list").closest(".card");
      card.scrollIntoView({ behavior: "smooth", block: "center" });
      const first = $("#alert-list").querySelector(".alert-item");
      if (first) {
        first.classList.add("flash");
        setTimeout(() => first.classList.remove("flash"), 1600);
      }
    });
  }

  function renderAlerts(alerts) {
    const el = $("#alert-list");
    if (!alerts.length) {
      el.innerHTML = '<div class="empty">Todo en orden: no hay alertas activas. Seguimos monitoreando por ti.</div>';
      return;
    }
    el.innerHTML = alerts.map((a) => `
      <div class="alert-item ${SEVERITY_CLASS[a.severity]} ${a.acknowledged ? "acked" : ""}" data-alert-id="${esc(a.id)}">
        <div class="alert-head" data-toggle-alert="${esc(a.id)}" role="button" tabindex="0" aria-expanded="false">
          <div class="alert-body">
            <div class="alert-title">${chip(SEVERITY_LABEL[a.severity], SEVERITY_CLASS[a.severity])} ${esc(a.ruleName)}
              <svg class="alert-caret" viewBox="0 0 16 16" width="14" height="14"><path fill="currentColor" d="M4 6l4 4 4-4z"/></svg></div>
            <div class="alert-msg">${esc(a.message)}</div>
            <div>${alertWhere(a)}</div>
          </div>
          ${a.acknowledged ? '<span class="chip good" style="align-self:flex-start">Atendida</span>'
            : `<button class="btn btn-ghost" data-ack="${esc(a.id)}">OK</button>`}
        </div>
        <div class="alert-drop" hidden>
          <div class="alert-drop-grid">
            <div><span class="adg-k">Comercio afectado</span><span class="adg-v">${a.merchantName ? esc(nameId(a.merchantName, a.merchantId)) : (a.processor ? "Canal " + esc(a.processor === "POWERTRANZ" ? "PowerTranz" : "Evertec") : "Todos los comercios")}</span></div>
            <div><span class="adg-k">Severidad</span><span class="adg-v">${esc(SEVERITY_LABEL[a.severity])}</span></div>
            <div><span class="adg-k">Plan de acción</span><span class="adg-v">${a.actionPlanName ? esc(a.actionPlanName) : "—"}</span></div>
          </div>
          <button class="btn btn-primary btn-sm" data-detail="${esc(a.id)}">Ver detalles →</button>
        </div>
      </div>`).join("");

    el.querySelectorAll("[data-toggle-alert]").forEach((row) => {
      const toggle = (ev) => {
        if (ev.target.closest("[data-ack]")) return; // el botón OK no despliega
        expandAlert(row.dataset.toggleAlert, true);
      };
      row.addEventListener("click", toggle);
      row.addEventListener("keydown", (ev) => { if (ev.key === "Enter" || ev.key === " ") { ev.preventDefault(); toggle(ev); } });
    });

    el.querySelectorAll("[data-detail]").forEach((btn) => {
      btn.addEventListener("click", (ev) => { ev.stopPropagation(); loadAlertDetail(btn.dataset.detail); });
    });

    el.querySelectorAll("[data-ack]").forEach((btn) => {
      btn.addEventListener("click", async (ev) => {
        ev.stopPropagation();
        await api(`/api/alerts/${encodeURIComponent(btn.dataset.ack)}/ack`, { method: "POST" });
        loadDashboard();
      });
    });
  }

  // Despliega/pliega el dropdown de una alerta en el panel.
  function expandAlert(id, toggle) {
    const item = document.querySelector(`.alert-item[data-alert-id="${CSS.escape(id)}"]`);
    if (!item) return;
    const drop = item.querySelector(".alert-drop");
    const head = item.querySelector(".alert-head");
    const open = toggle ? drop.hidden : true;
    drop.hidden = !open;
    item.classList.toggle("open", open);
    if (head) head.setAttribute("aria-expanded", String(open));
  }

  // ---------- página de detalle de alerta ----------

  async function loadAlertDetail(id) {
    let d;
    try {
      d = await api(`/api/alerts/${encodeURIComponent(id)}`);
    } catch (err) {
      d = null;
    }
    renderAlertDetail(d);
    switchView("alert-detail");
  }

  function detailField(label, value) {
    return `<div class="ad-field"><div class="ad-k">${esc(label)}</div><div class="ad-v">${value}</div></div>`;
  }

  function renderAlertDetail(d) {
    const el = $("#alert-detail");
    if (!d) {
      el.innerHTML = `
        <div class="panel">
          <button class="btn" id="ad-back">← Volver</button>
          <div class="empty" style="margin-top:16px">Esta alerta ya no está activa. Puede que se haya atendido o que la condición se haya normalizado. ¡Buena señal!</div>
        </div>`;
      $("#ad-back").addEventListener("click", () => switchView("dashboard"));
      return;
    }
    const a = d.alert;
    const sev = SEVERITY_CLASS[a.severity];
    const plan = d.actionPlan;

    el.innerHTML = `
      <div class="ad-top">
        <button class="btn" id="ad-back">← Volver</button>
      </div>
      <div class="card ad-header sev-${sev}">
        <div class="ad-header-main">
          <div class="ad-kicker">${chip(SEVERITY_LABEL[a.severity], sev)} Detalle de alerta</div>
          <h1 class="ad-title">${esc(a.ruleName)}</h1>
          <p class="ad-msg">${esc(a.message)}</p>
        </div>
        ${a.acknowledged ? '<span class="chip good">Atendida</span>'
          : `<button class="btn btn-primary" id="ad-ack">Marcar como atendida</button>`}
      </div>

      <div class="dash-block" style="margin-top:22px">
        <div class="block-label">Información de la alerta</div>
        <div class="card">
          <div class="ad-grid">
            ${detailField("Hora", esc(fmtDateTime.format(new Date(a.triggeredAt))))}
            ${detailField("Código de operación", `<code>${esc(d.operationCode)}</code>`)}
            ${detailField("Tarjeta", `${esc(d.cardBrand)} · <code>${esc(d.cardMask)}</code>`)}
            ${detailField("Volumen procesado", `<b>${moneyC(d.processedVolume)}</b>`)}
            ${detailField("Magnitud del problema", `<b class="sig-bad">${esc(d.magnitudeText)}</b>`)}
            ${detailField("Comercio afectado", `<b>${esc(a.merchantName ? nameId(a.merchantName, a.merchantId) : d.scopeLabel)}</b>`)}
            ${detailField("Condición evaluada", esc(d.conditionLabel))}
            ${detailField(d.metric.label, `<b>${esc(fmtMetric(d.metric))}</b> · umbral ${esc(fmtNum(d.metric.threshold))}${esc(d.metric.unit)}`)}
            ${detailField("Transacciones evaluadas (hoy)", `${int(d.txEvaluated)} · ${int(d.declinedCount)} rechazadas`)}
            ${d.drCode ? detailField("Código DR", `<b>${esc(d.drCode)}</b> — ${esc(d.drDescription || "")}`) : ""}
            ${detailField("Importe de la operación", d.sampleAmount != null ? moneyC(d.sampleAmount) : "—")}
            ${detailField("Hora de la operación", esc(fmtDateTime.format(new Date(d.sampleTime))))}
          </div>
        </div>
      </div>

      <div class="dash-block">
        <div class="block-label">Plan de acción recomendado</div>
        <div class="card ad-plan">
          ${plan ? `
            <h2 class="ad-plan-name">${PLAN_ICON}${esc(plan.name)}</h2>
            <p class="ad-plan-desc">${esc(plan.description || "")}</p>
            <ol class="ad-steps">${(plan.steps || []).map((s) => `<li>${esc(s)}</li>`).join("")}</ol>`
          : '<div class="empty">Esta regla no tiene un plan de acción enlazado.</div>'}
        </div>
      </div>`;

    $("#ad-back").addEventListener("click", () => switchView("dashboard"));
    const ack = $("#ad-ack");
    if (ack) {
      ack.addEventListener("click", async () => {
        await api(`/api/alerts/${encodeURIComponent(a.id)}/ack`, { method: "POST" });
        await loadDashboard();
        loadAlertDetail(a.id);
      });
    }
  }

  const fmtNum = (v) => Number(v).toLocaleString("es-PA", { maximumFractionDigits: 1 });
  const fmtMetric = (m) => `${fmtNum(m.value)}${m.unit}`;
  const PLAN_ICON = '<svg viewBox="0 0 24 24" width="20" height="20" style="vertical-align:-4px;margin-right:8px"><path fill="currentColor" d="M9 2h6a2 2 0 0 1 2 2h1a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h1a2 2 0 0 1 2-2zm0 2v2h6V4H9zm-1 7 1.4-1.4L11 11.2l3.6-3.6L16 9l-5 5-3-3z"/></svg>';

  /** Path de un segmento de dona (anillo) entre dos ángulos. */
  function annulusPath(cx, cy, rO, rI, a0, a1) {
    const large = a1 - a0 > Math.PI ? 1 : 0;
    const p = (r, a) => `${(cx + r * Math.cos(a)).toFixed(2)} ${(cy + r * Math.sin(a)).toFixed(2)}`;
    return `M ${p(rO, a0)} A ${rO} ${rO} 0 ${large} 1 ${p(rO, a1)} ` +
      `L ${p(rI, a1)} A ${rI} ${rI} 0 ${large} 0 ${p(rI, a0)} Z`;
  }

  // Formato compacto para el número del centro del donut (menos llamativo).
  function moneyCompact(v) {
    v = Number(v || 0);
    const a = Math.abs(v);
    if (a >= 1e6) return "$" + (v / 1e6).toLocaleString("es-PA", { maximumFractionDigits: a >= 1e7 ? 0 : 1 }) + "M";
    if (a >= 1e3) return "$" + (v / 1e3).toLocaleString("es-PA", { maximumFractionDigits: a >= 1e4 ? 0 : 1 }) + "K";
    return "$" + v.toLocaleString("es-PA", { maximumFractionDigits: 0 });
  }

  /**
   * Dona SVG con segmentos que se expanden al pasar el cursor + tooltip.
   * segments: {label, value, count, color, valueLabel?}
   * El texto del centro es discreto: valor compacto + etiqueta pequeña.
   */
  function donut(container, segments, { centerValue, centerLabel } = {}) {
    container.innerHTML = "";
    const segs = segments.filter((s) => Number(s.value) > 0);
    if (!segs.length) {
      container.innerHTML = '<div class="empty">Sin datos en este periodo. Prueba con otro rango.</div>';
      return;
    }
    const total = segs.reduce((a, s) => a + Number(s.value), 0);
    const size = 200, cx = size / 2, cy = size / 2, rO = 92, rI = 64;
    const svg = svgEl("svg", { viewBox: `0 0 ${size} ${size}`, class: "donut-svg", role: "img",
      "aria-label": centerLabel || "Distribución" });

    let a0 = -Math.PI / 2;
    segs.forEach((seg) => {
      const frac = Number(seg.value) / total;
      // un solo segmento: anillo completo (evita el arco degenerado)
      const a1 = segs.length === 1 ? a0 + 2 * Math.PI - 0.0001 : a0 + frac * 2 * Math.PI;
      const mid = (a0 + a1) / 2;
      const g = svgEl("g", { class: "donut-seg" });
      const path = svgEl("path", { d: annulusPath(cx, cy, rO, rI, a0, a1),
        fill: seg.color, stroke: "#fff", "stroke-width": 2.5, "stroke-linejoin": "round" });
      g.append(path);
      const dx = (Math.cos(mid) * 8).toFixed(1), dy = (Math.sin(mid) * 8).toFixed(1);
      const tip = `<div class="tt-title">${esc(seg.label)}</div>` +
        `<div>${int(seg.count)} trx · <b>${pct(frac * 100)}</b> del total</div>` +
        (seg.valueLabel ? `<div class="tt-muted">${seg.valueLabel}</div>` : "");
      const enter = (ev) => { g.style.transform = `translate(${dx}px, ${dy}px)`; showTooltip(tip, ev.clientX, ev.clientY); };
      g.addEventListener("mousemove", enter);
      g.addEventListener("mouseleave", () => { g.style.transform = ""; hideTooltip(); });
      svg.append(g);
      a0 = a1;
    });

    if (centerValue != null) {
      const v = svgEl("text", { x: cx, y: cy - 1, "text-anchor": "middle", "font-size": 15, class: "donut-center-value" });
      v.textContent = centerValue;
      svg.append(v);
    }
    if (centerLabel) {
      const l = svgEl("text", { x: cx, y: cy + 13, "text-anchor": "middle", "font-size": 9, class: "donut-center-label" });
      l.textContent = centerLabel;
      svg.append(l);
    }
    container.append(svg);
    return svg;
  }

  // pill de comparación vs periodo anterior
  function deltaPill(delta) {
    if (delta == null) return '<span class="delta-pill flat">sin periodo previo</span>';
    const dir = delta > 0.05 ? "up" : delta < -0.05 ? "down" : "flat";
    const arrow = dir === "up" ? "▲" : dir === "down" ? "▼" : "•";
    return `<span class="delta-pill ${dir}">${arrow} ${pct(Math.abs(delta))} vs. periodo anterior</span>`;
  }

  // Leyenda compartida por todos los donuts. items: {color, name, valueText, subText}
  function buildDonutLegend(items) {
    const el = document.createElement("div");
    el.className = "donut-legend";
    el.innerHTML = items.map((it) => `
      <div class="donut-key">
        <span class="donut-swatch" style="background:${it.color}"></span>
        <span class="dk-name">${esc(it.name)}</span>
        <span class="dk-val">${it.valueText}</span>
        <span class="dk-sub">${it.subText}</span>
      </div>`).join("");
    return el;
  }

  function renderProcessors(s) {
    const container = $("#chart-processors");
    const total = s.processors.reduce((a, p) => a + Number(p.volume), 0);
    const segs = s.processors.map((p) => ({
      label: p.label,
      value: Number(p.volume),
      count: p.txCount,
      color: CHANNEL_COLOR[p.processor] || SERIES[0],
      valueLabel: `Volumen ${moneyC(p.volume)}`,
    }));

    donut(container, segs, { centerValue: moneyCompact(total), centerLabel: "volumen total" });
    container.append(buildDonutLegend(s.processors.map((p) => ({
      color: CHANNEL_COLOR[p.processor] || SERIES[0],
      name: p.label,
      valueText: money(p.volume),
      subText: `${int(p.txCount)} trx · ${pct(p.sharePct)} del total · ${pct(p.approvalRate)} aprob. ${deltaPill(p.deltaPct)}`,
    }))));
  }

  function renderServices(s) {
    const container = $("#chart-services");
    const items = s.services.filter((x) => x.txCount > 0);
    const total = items.reduce((a, x) => a + Number(x.volume), 0);
    const color = (i) => SERVICE_COLORS[i % SERVICE_COLORS.length];
    const segs = items.map((x, i) => ({
      label: x.label,
      value: Number(x.volume),
      count: x.txCount,
      color: color(i),
      valueLabel: `${int(x.txCount)} trx`,
    }));

    donut(container, segs, { centerValue: moneyCompact(total), centerLabel: "volumen aprob." });
    container.append(buildDonutLegend(items.map((x, i) => ({
      color: color(i),
      name: x.label,
      valueText: money(x.volume),
      subText: `${int(x.txCount)} trx · ${pct(x.sharePct)} del total`,
    }))));
  }

  function render3ds(s) {
    const t = s.threeDs;
    const container = $("#chart-3ds");
    const total = (t.withCount || 0) + (t.withoutCount || 0);
    const segs = [
      { label: "Con 3DS", value: t.withCount, count: t.withCount, color: THREEDS_COLORS[0],
        valueLabel: `${pct(t.withApprovalRate)} aprobación` },
      { label: "Sin 3DS", value: t.withoutCount, count: t.withoutCount, color: THREEDS_COLORS[1],
        valueLabel: `${pct(t.withoutApprovalRate)} aprobación` },
    ];

    donut(container, segs, { centerValue: int(total), centerLabel: "transacciones" });
    container.append(buildDonutLegend([
      { color: THREEDS_COLORS[0], name: "Con 3DS", valueText: pct(t.withSharePct),
        subText: `${int(t.withCount)} trx · ${pct(t.withApprovalRate)} aprob.` },
      { color: THREEDS_COLORS[1], name: "Sin 3DS", valueText: pct(t.withSharePct == null ? null : 100 - t.withSharePct),
        subText: `${int(t.withoutCount)} trx · ${pct(t.withoutApprovalRate)} aprob.` },
    ]));
  }

  // Clasifica el plan de acción oficial para darle color de marca.
  function actionClass(action) {
    const a = (action || "").toLowerCase();
    if (a.includes("emisor")) return "issuer";
    if (a.includes("paguelofacil")) return "pf";
    if (a.includes("autenticaci")) return "auth";
    if (a.includes("tarjeta") || a.includes("aprobada")) return "card";
    return "";
  }

  function renderDrTable(s) {
    const el = $("#dr-table");
    if (!s.topDeclineCodes.length) {
      el.innerHTML = '<div class="empty">Sin rechazos en el periodo. ¡Buen trabajo!</div>';
      return;
    }
    el.innerHTML = `
      <div class="table-wrap"><table class="data">
        <thead><tr><th>Código</th><th>Motivo</th><th>Plan de acción</th><th class="num">Rechazos</th><th class="num">% del total</th></tr></thead>
        <tbody>
          ${s.topDeclineCodes.map((c) => `
            <tr>
              <td><b>${esc(c.code)}</b></td>
              <td>${esc(c.description)}</td>
              <td><span class="dr-action ${actionClass(c.action)}">${esc(c.action)}</span></td>
              <td class="num">${int(c.count)}</td>
              <td class="num">${pct(c.pctOfDeclined)}</td>
            </tr>`).join("")}
        </tbody>
      </table></div>
      <div class="dr-source">Fuente: catálogo oficial de códigos de rechazo de PagueloFacil.
        <a class="doc-open" href="codigos-rechazo.html" target="_blank" rel="noopener">Ver catálogo completo →</a></div>`;
  }

  // ---------- comparador de comercios ----------

  const COMPARE_MAX = 5;
  const compareColor = (i) => SERVICE_COLORS[i % SERVICE_COLORS.length];

  function setupCompare() {
    // autocompletado: nombre visible, el ID como etiqueta secundaria
    const list = $("#merchant-list");
    list.innerHTML = "";
    state.merchants.forEach((m) => {
      const opt = document.createElement("option");
      opt.value = m.name;
      opt.label = m.id;
      list.append(opt);
    });

    $("#compare-add").addEventListener("submit", (ev) => {
      ev.preventDefault();
      addCompareMerchant($("#compare-input").value);
    });

    // arranca con un par de comercios para que la comparación no salga vacía
    state.compareSelection = state.merchants.slice(0, 3).map((m) => m.id);
    renderCompareChips();
  }

  // Resuelve un texto (nombre o ID) a un comercio y lo agrega a la comparación.
  function addCompareMerchant(query) {
    const q = (query || "").trim();
    const hint = $("#compare-hint");
    if (!q) return;
    const ql = q.toLowerCase();
    const m = state.merchants.find((x) => x.id.toLowerCase() === ql)
      || state.merchants.find((x) => x.name.toLowerCase() === ql)
      || state.merchants.find((x) => x.name.toLowerCase().includes(ql));

    if (!m) { hint.textContent = `No encontramos ningún comercio para "${q}". Prueba con el nombre o su ID (ej: M-002).`; return; }
    if (state.compareSelection.includes(m.id)) { hint.textContent = `${m.name} ya está en la comparación.`; return; }
    if (state.compareSelection.length >= COMPARE_MAX) {
      hint.textContent = `Puedes comparar hasta ${COMPARE_MAX} comercios. Quita uno para agregar otro.`;
      return;
    }
    state.compareSelection.push(m.id);
    $("#compare-input").value = "";
    hint.textContent = "";
    renderCompareChips();
    renderCompare();
  }

  function removeCompareMerchant(id) {
    const i = state.compareSelection.indexOf(id);
    if (i >= 0) state.compareSelection.splice(i, 1);
    renderCompareChips();
    renderCompare();
  }

  // Chips de los comercios ya agregados, con color de gráfica y botón de quitar.
  function renderCompareChips() {
    const el = $("#compare-picker");
    if (!state.compareSelection.length) {
      el.innerHTML = '<div class="compare-empty">Agrega comercios por nombre o ID para compararlos.</div>';
      return;
    }
    el.innerHTML = state.compareSelection.map((id, i) => `
      <span class="compare-chip">
        <span class="compare-dot" style="background:${compareColor(i)}"></span>
        ${esc(merchantLabel(id))}
        <button type="button" class="compare-remove" data-remove="${esc(id)}" title="Quitar" aria-label="Quitar">✕</button>
      </span>`).join("");
    el.querySelectorAll("[data-remove]").forEach((btn) => {
      btn.addEventListener("click", () => removeCompareMerchant(btn.dataset.remove));
    });
  }

  async function renderCompare() {
    const chartEl = $("#chart-compare");
    const tableEl = $("#compare-table");
    if (state.compareSelection.length < 2) {
      chartEl.innerHTML = '<div class="empty">Elige al menos 2 comercios y te los comparamos aquí.</div>';
      tableEl.innerHTML = "";
      return;
    }
    const { from, to } = currentRange();
    const p = new URLSearchParams({ merchantIds: state.compareSelection.join(",") });
    if ($("#f-service").value) p.set("service", $("#f-service").value);
    if (from) p.set("from", from);
    if (to) p.set("to", to);
    const rows = await api(`/api/dashboard/compare?${p}`);

    // el color sigue al comercio según su orden de selección (paleta de marca)
    const colorById = {};
    state.compareSelection.forEach((id, i) => { colorById[id] = compareColor(i); });

    hBars(chartEl, rows.map((r) => {
      const full = nameId(r.merchantName, r.merchantId);
      return {
        label: full.length > 22 ? full.slice(0, 21) + "…" : full,
        value: Number(r.volume),
        display: money(r.volume),
        color: colorById[r.merchantId],
        tooltip: `<div class="tt-title">${esc(full)}</div>` +
          `<div>Volumen: <b>${moneyC(r.volume)}</b></div>` +
          `<div class="tt-muted">${int(r.txCount)} trx · ${pct(r.approvalRate)} aprobación · reembolsos ${pct(r.refundPct)}</div>`,
      };
    }), { width: 620, labelWidth: 178 });

    tableEl.innerHTML = `
      <div class="table-wrap"><table class="data">
        <thead><tr><th>Comercio</th><th class="num">Volumen</th><th class="num">Trx</th><th class="num">Aprobación</th><th class="num">Reembolsos</th></tr></thead>
        <tbody>
          ${rows.map((r) => `
            <tr>
              <td><span class="swatch" style="display:inline-block;width:9px;height:9px;border-radius:2px;background:${colorById[r.merchantId]};margin-right:7px"></span>${esc(nameId(r.merchantName, r.merchantId))}</td>
              <td class="num">${money(r.volume)}</td>
              <td class="num">${int(r.txCount)}</td>
              <td class="num">${pct(r.approvalRate)}</td>
              <td class="num">${pct(r.refundPct)}</td>
            </tr>`).join("")}
        </tbody>
      </table></div>`;
  }

  // ---------- proyectos ----------

  async function loadProjects() {
    const projects = await api("/api/projects");
    $("#project-grid").innerHTML = projects.map((p) => `
      <div class="project-card">
        <div style="display:flex;justify-content:space-between;gap:8px;align-items:center">
          <h3>${esc(p.name)}</h3>${chip(p.status)}
        </div>
        <p>${esc(p.description)}</p>
        <div class="project-foot">
          <span>${esc(p.owner)}</span>
          <span>Actualizado ${fmtDate.format(parseDay(p.updatedAt))}</span>
        </div>
      </div>`).join("");
  }

  // ---------- credenciales (ciclo de vida) ----------

  // Pipeline de estados en orden. col = etiqueta corta para el tablero Kanban.
  const CRED_STATUSES = [
    { key: "Solicitado", col: "Solicitado", kind: "warning" },
    { key: "En espera de credenciales", col: "En espera", kind: "warning" },
    { key: "Recibido", col: "Recibido", kind: "neutral" },
    { key: "En pruebas", col: "En pruebas", kind: "warning" },
    { key: "Visto bueno enviado", col: "Visto bueno enviado", kind: "neutral" },
    { key: "Aprobado por BAC", col: "Aprobado por BAC", kind: "good" },
    { key: "Habilitado/Configurado", col: "Habilitado", kind: "good" },
  ];
  const CRED_BANKS = ["BAC", "Towerbank"];
  const CRED_BRANDS = [
    { key: "Mc", label: "Mastercard", short: "MC" },
    { key: "Visa", label: "VISA", short: "VISA" },
    { key: "Amex", label: "AMEX", short: "AMEX" },
  ];
  const TEST_STATES = ["Pendiente", "OK", "Falló"];

  const COPY_ICON = '<svg viewBox="0 0 24 24" width="15" height="15"><path fill="currentColor" d="M16 1H4a2 2 0 0 0-2 2v12h2V3h12V1zm3 4H8a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2zm0 16H8V7h11v14z"/></svg>';
  const CHECK_ICON = '<svg viewBox="0 0 24 24" width="15" height="15"><path fill="currentColor" d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"/></svg>';

  const statusMeta = (key) => CRED_STATUSES.find((s) => s.key === key) || { col: key, kind: "neutral" };
  const statusIndex = (key) => CRED_STATUSES.findIndex((s) => s.key === key);
  const credStatusChip = (key) => chip(statusMeta(key).col, statusMeta(key).kind);
  const procLabel = (p) => (p === "POWERTRANZ" ? "PowerTranz" : "Evertec");
  const anyBrandOk = (c) => CRED_BRANDS.some((b) => c["prueba" + b.key] === "OK");
  const refundPending = (c) => anyBrandOk(c) && !c.reembolsosPruebas;
  const isoToday = () => new Date().toISOString().slice(0, 10);

  async function loadCredentials() {
    state.credentials = await api("/api/credentials");
    populateCredFilters();
    renderCredBody();
  }

  function populateCredFilters() {
    const st = $("#cred-status-filter"), bk = $("#cred-bank-filter"), me = $("#cred-merchant-filter");
    if (st.options.length <= 1) {
      CRED_STATUSES.forEach((s) => st.append(new Option(s.key, s.key)));
      CRED_BANKS.forEach((b) => bk.append(new Option(b, b)));
      state.merchants.forEach((m) => me.append(new Option(`${m.name} (${m.id})`, m.id)));
    }
  }

  function setCredView(v) {
    state.credView = v;
    $("#cred-view-table").classList.toggle("active", v === "table");
    $("#cred-view-kanban").classList.toggle("active", v === "kanban");
    renderCredBody();
  }

  function filteredCreds() {
    const st = $("#cred-status-filter").value;
    const bk = $("#cred-bank-filter").value;
    const me = $("#cred-merchant-filter").value;
    const onlyRefund = $("#cred-refund-filter").checked;
    return state.credentials
      .filter((c) => !st || c.status === st)
      .filter((c) => !bk || c.bank === bk)
      .filter((c) => !me || c.merchantId === me)
      .filter((c) => !onlyRefund || refundPending(c));
  }

  function renderCredBody() {
    if (!state.credentials.length) {
      $("#credentials-body").innerHTML = `
        <div class="cred-empty">
          <div class="empty">Aún no hay credenciales. Empieza registrando la primera: la solicitas al banco, la avanzas por cada etapa y controlas sus pruebas por marca.</div>
          <button class="btn btn-primary" id="cred-empty-new">Registrar la primera credencial</button>
        </div>`;
      $("#cred-empty-new").addEventListener("click", () => openCredForm(null));
      return;
    }
    const list = filteredCreds();
    if (state.credView === "kanban") renderCredKanban(list);
    else renderCredTable(list);
  }

  function renderCredTable(list) {
    const el = $("#credentials-body");
    el.innerHTML = `
      <div class="table-wrap"><table class="data">
        <thead><tr>
          <th>ID</th><th>Comercio</th><th>Banco</th><th>Procesador</th><th>Afiliado</th>
          <th>Estado</th><th>Observaciones</th><th></th>
        </tr></thead>
        <tbody>
          ${list.map((c) => `
            <tr>
              <td>${esc(c.id)}</td>
              <td><a class="cell-link" data-cred-detail="${esc(c.id)}">${esc(merchantLabel(c.merchantId))}</a></td>
              <td>${esc(c.bank || "—")}</td>
              <td>${esc(procLabel(c.processor))}</td>
              <td><code>${esc(c.afiliado || "—")}</code></td>
              <td>${credStatusChip(c.status)}${refundPending(c) ? ' <span class="chip warning">Reembolso pendiente</span>' : ""}</td>
              <td><button class="btn btn-ghost" data-cred-obs="${esc(c.id)}">Observaciones</button></td>
              <td><button class="btn" data-cred-view="${esc(c.id)}">Ver credenciales</button></td>
            </tr>`).join("")}
        </tbody>
      </table></div>
      ${list.length === 0 ? '<div class="empty">No encontramos credenciales con esos filtros. Prueba ajustándolos.</div>' : ""}`;
    wireCredActions(el);
  }

  function renderCredKanban(list) {
    const el = $("#credentials-body");
    const byStatus = {};
    CRED_STATUSES.forEach((s) => (byStatus[s.key] = []));
    list.forEach((c) => (byStatus[c.status] || (byStatus[c.status] = [])).push(c));

    el.innerHTML = `<div class="kanban">
      ${CRED_STATUSES.map((s) => `
        <div class="kanban-col">
          <div class="kanban-col-head">
            <span class="kc-title">${esc(s.col)}</span>
            <span class="kc-count">${byStatus[s.key].length}</span>
          </div>
          <div class="kanban-cards">
            ${byStatus[s.key].map((c) => credCard(c)).join("") || '<div class="kanban-empty">—</div>'}
          </div>
        </div>`).join("")}
    </div>`;
    wireCredActions(el);
  }

  function credCard(c) {
    const idx = statusIndex(c.status);
    const next = idx >= 0 && idx < CRED_STATUSES.length - 1 ? CRED_STATUSES[idx + 1] : null;
    return `
      <div class="kanban-card" data-cred-detail="${esc(c.id)}">
        <div class="kc-merchant">${esc(merchantLabel(c.merchantId))}</div>
        <div class="kc-meta">${esc(c.bank || "—")} · ${esc(procLabel(c.processor))}</div>
        <div class="kc-meta kc-afiliado"><code>${esc(c.afiliado || "—")}</code></div>
        ${refundPending(c) ? '<div class="kc-flag">Reembolso pendiente</div>' : ""}
        ${next ? `<button class="btn btn-sm kc-advance" data-cred-advance="${esc(c.id)}" title="Mover a ${esc(next.col)}">Mover a ${esc(next.col)} →</button>` : '<span class="kc-done">✓ Completado</span>'}
      </div>`;
  }

  function wireCredActions(root) {
    root.querySelectorAll("[data-cred-detail]").forEach((elm) => {
      elm.addEventListener("click", (ev) => {
        if (ev.target.closest("[data-cred-advance]")) return;
        openCredDetail(elm.dataset.credDetail);
      });
    });
    root.querySelectorAll("[data-cred-view]").forEach((b) =>
      b.addEventListener("click", (ev) => { ev.stopPropagation(); openVerCredenciales(credById(b.dataset.credView)); }));
    root.querySelectorAll("[data-cred-obs]").forEach((b) =>
      b.addEventListener("click", (ev) => { ev.stopPropagation(); openObservaciones(credById(b.dataset.credObs)); }));
    root.querySelectorAll("[data-cred-advance]").forEach((b) =>
      b.addEventListener("click", (ev) => { ev.stopPropagation(); advanceCred(credById(b.dataset.credAdvance)); }));
  }

  const credById = (id) => state.credentials.find((c) => c.id === id);

  // ---- persistencia ----
  async function putCred(c) {
    const updated = await api(`/api/credentials/${encodeURIComponent(c.id)}`, {
      method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(c),
    });
    const i = state.credentials.findIndex((x) => x.id === c.id);
    if (i >= 0) state.credentials[i] = updated;
    return updated;
  }

  function refreshCredViews(id) {
    renderCredBody();
    if (state.credDetailId && (!id || state.credDetailId === id)) {
      const c = credById(state.credDetailId);
      if (c) renderCredDetail(c);
    }
  }

  async function advanceCred(c) {
    const idx = statusIndex(c.status);
    if (idx < 0 || idx >= CRED_STATUSES.length - 1) return;
    const next = { ...c, status: CRED_STATUSES[idx + 1].key };
    if (next.status === "Aprobado por BAC") next.aprobadoPorBac = true;
    if (next.status === "Habilitado/Configurado" && !next.fechaMigracion) next.fechaMigracion = isoToday();
    await putCred(next);
    refreshCredViews(c.id);
  }

  async function setBrandTest(c, brandKey, result) {
    const next = { ...c, ["prueba" + brandKey]: result };
    await putCred(next);
    refreshCredViews(c.id);
  }

  async function setRefunds(c, done) {
    await putCred({ ...c, reembolsosPruebas: done });
    refreshCredViews(c.id);
  }

  async function saveOpCode(c, brandKey, value) {
    await putCred({ ...c, ["codigoOperacion" + brandKey]: value });
  }

  // ---- popups ----
  function credField(label, value, copy) {
    const v = value == null || value === "" ? "" : String(value);
    return `<div class="cred-field">
      <div class="cf-label">${esc(label)}</div>
      <div class="cf-value"><span class="cf-text">${v ? esc(v) : "—"}</span>
        ${v && copy ? `<button class="cf-copy" data-copy="${esc(v)}" title="Copiar">${COPY_ICON}</button>` : ""}
      </div>
    </div>`;
  }

  function openVerCredenciales(c) {
    if (!c) return;
    const body = `
      <div class="ver-head">
        <div><div class="ver-merchant">${esc(merchantLabel(c.merchantId))}</div>
          <div class="ver-sub">${esc(c.bank || "—")} · ${esc(procLabel(c.processor))} · ${esc(c.afiliado || "")}</div></div>
        ${credStatusChip(c.status)}
      </div>
      <div class="cred-fields">
        ${credField("PowerTranz ID", c.powertranzId, true)}
        ${credField("Contraseña", c.contrasena, true)}
        ${credField("PW Admin Site", c.pwAdminSite, true)}
        ${credField("Tarjetas", c.tarjetas, true)}
        ${credField("Monedas", c.monedas, true)}
        ${credField("3DS", c.threeDs ? "Sí" : "No", false)}
        ${credField("Rebill", c.rebill ? "Sí" : "No", false)}
        ${credField("Límite por Trx", c.limitePorTrx ? moneyC(c.limitePorTrx) : "", true)}
        ${credField("Límite mensual", c.limiteMensual ? moneyC(c.limiteMensual) : "", true)}
      </div>`;
    const foot = `<button class="btn" data-modal-close>Cerrar</button>
      <button class="btn btn-primary" id="ver-edit">Editar</button>`;
    openModal("Credenciales", body, foot);
    $("#ver-edit").addEventListener("click", () => { closeModal(); openCredForm(c); });
  }

  function openObservaciones(c) {
    if (!c) return;
    const obs = c.observaciones && c.observaciones.trim();
    openModal(`Observaciones · ${esc(merchantLabel(c.merchantId))}`,
      obs ? `<p class="obs-text">${esc(obs)}</p>`
          : '<div class="empty">Sin observaciones para esta credencial todavía.</div>',
      `<button class="btn" data-modal-close>Cerrar</button>
       <button class="btn btn-primary" id="obs-edit">Editar</button>`);
    $("#obs-edit").addEventListener("click", () => { closeModal(); openCredForm(c); });
  }

  // ---- formulario crear / editar ----
  function opt(list, sel) { return list.map((v) => `<option value="${esc(v)}" ${v === sel ? "selected" : ""}>${esc(v)}</option>`).join(""); }

  function openCredForm(c) {
    const isNew = !c;
    c = c || { processor: "POWERTRANZ", bank: "BAC", status: "Solicitado", tarjetas: "MC, VISA, AMEX", monedas: "USD", threeDs: true };
    const merchOpts = state.merchants.map((m) => `<option value="${m.id}" ${m.id === c.merchantId ? "selected" : ""}>${esc(m.name)} (${esc(m.id)})</option>`).join("");
    const body = `
      <form id="cred-form" class="cred-form">
        <div class="cred-form-grid">
          <label class="cf-wide">Comercio<select id="cf-merchant" required><option value="">Selecciona…</option>${merchOpts}</select></label>
          <label>Banco<select id="cf-bank">${opt(CRED_BANKS, c.bank)}</select></label>
          <label>Procesador<select id="cf-proc">${opt(["POWERTRANZ", "EVERTEC"], c.processor)}</select></label>
          <label>Afiliado<input id="cf-afiliado" value="${esc(c.afiliado || "")}"></label>
          <label>Fecha de solicitud<input type="date" id="cf-solicitud" value="${esc(c.fechaSolicitud || "")}"></label>
          <label>Estado<select id="cf-status">${CRED_STATUSES.map((s) => `<option value="${esc(s.key)}" ${s.key === c.status ? "selected" : ""}>${esc(s.key)}</option>`).join("")}</select></label>
          <label class="cf-check"><input type="checkbox" id="cf-rebill" ${c.rebill ? "checked" : ""}> Rebill</label>
          <label class="cf-check"><input type="checkbox" id="cf-3ds" ${c.threeDs ? "checked" : ""}> 3DS</label>

          <div class="cf-section">Credenciales</div>
          <label>PowerTranz ID<input id="cf-ptid" value="${esc(c.powertranzId || "")}"></label>
          <label>Contraseña<input id="cf-pass" value="${esc(c.contrasena || "")}"></label>
          <label>PW Admin Site<input id="cf-adminpw" value="${esc(c.pwAdminSite || "")}"></label>
          <label>Tarjetas<input id="cf-tarjetas" value="${esc(c.tarjetas || "")}"></label>
          <label>Monedas<input id="cf-monedas" value="${esc(c.monedas || "")}"></label>
          <label>Límite por Trx (USD)<input type="number" step="any" id="cf-pertx" value="${c.limitePorTrx || ""}"></label>
          <label>Límite mensual (USD)<input type="number" step="any" id="cf-monthly" value="${c.limiteMensual || ""}"></label>

          <div class="cf-section">Pruebas por marca</div>
          ${CRED_BRANDS.map((b) => `
            <label>Prueba ${b.short}<select id="cf-prueba${b.key}">${opt(TEST_STATES, c["prueba" + b.key] || "Pendiente")}</select></label>
            <label>Código operación ${b.short}<input id="cf-op${b.key}" value="${esc(c["codigoOperacion" + b.key] || "")}"></label>`).join("")}
          <label class="cf-check"><input type="checkbox" id="cf-refunds" ${c.reembolsosPruebas ? "checked" : ""}> Reembolsos de pruebas emitidos</label>
          <label class="cf-check"><input type="checkbox" id="cf-bac" ${c.aprobadoPorBac ? "checked" : ""}> Aprobado por BAC</label>
          <label>Fecha de migración<input type="date" id="cf-migracion" value="${esc(c.fechaMigracion || "")}"></label>

          <div class="cf-section">Notas</div>
          <label class="cf-wide">Notas<textarea id="cf-notas" rows="2">${esc(c.notas || "")}</textarea></label>
          <label class="cf-wide">Observaciones<textarea id="cf-obs" rows="2">${esc(c.observaciones || "")}</textarea></label>
        </div>
      </form>`;
    const foot = `<button class="btn" data-modal-close>Cancelar</button>
      <button class="btn btn-primary" id="cf-save">${isNew ? "Crear credencial" : "Guardar cambios"}</button>`;
    openModal(isNew ? "Nueva credencial" : `Editar credencial · ${esc(c.id)}`, body, foot);
    $("#cf-save").addEventListener("click", () => saveCredForm(isNew ? null : c));
  }

  async function saveCredForm(existing) {
    const num = (id) => { const v = Number($(id).value); return Number.isFinite(v) ? v : 0; };
    const body = {
      ...(existing || {}),
      merchantId: $("#cf-merchant").value,
      bank: $("#cf-bank").value,
      processor: $("#cf-proc").value,
      afiliado: $("#cf-afiliado").value.trim(),
      fechaSolicitud: $("#cf-solicitud").value || null,
      status: $("#cf-status").value,
      rebill: $("#cf-rebill").checked,
      threeDs: $("#cf-3ds").checked,
      powertranzId: $("#cf-ptid").value.trim(),
      contrasena: $("#cf-pass").value.trim(),
      pwAdminSite: $("#cf-adminpw").value.trim(),
      tarjetas: $("#cf-tarjetas").value.trim(),
      monedas: $("#cf-monedas").value.trim(),
      limitePorTrx: num("#cf-pertx"),
      limiteMensual: num("#cf-monthly"),
      reembolsosPruebas: $("#cf-refunds").checked,
      aprobadoPorBac: $("#cf-bac").checked,
      fechaMigracion: $("#cf-migracion").value || null,
      notas: $("#cf-notas").value.trim(),
      observaciones: $("#cf-obs").value.trim(),
    };
    CRED_BRANDS.forEach((b) => {
      body["prueba" + b.key] = $("#cf-prueba" + b.key).value;
      body["codigoOperacion" + b.key] = $("#cf-op" + b.key).value.trim();
    });
    if (!body.merchantId) { $("#cf-merchant").focus(); return; }

    if (existing) {
      await putCred({ ...body, id: existing.id });
    } else {
      const created = await api("/api/credentials", {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
      });
      state.credentials.push(created);
    }
    closeModal();
    refreshCredViews(existing ? existing.id : null);
  }

  // ---- vista de detalle + stepper ----
  function openCredDetail(id) {
    const c = credById(id);
    if (!c) return;
    state.credDetailId = id;
    renderCredDetail(c);
    switchView("cred-detail");
  }

  function renderCredDetail(c) {
    const el = $("#cred-detail");
    const idx = statusIndex(c.status);
    const next = idx >= 0 && idx < CRED_STATUSES.length - 1 ? CRED_STATUSES[idx + 1] : null;

    const stepper = `<div class="stepper">
      ${CRED_STATUSES.map((s, i) => `
        <div class="step ${i < idx ? "done" : i === idx ? "current" : "future"}">
          <span class="step-dot">${i < idx ? "✓" : i + 1}</span>
          <span class="step-label">${esc(s.col)}</span>
        </div>`).join('<span class="step-line"></span>')}
    </div>`;

    const brands = CRED_BRANDS.map((b) => {
      const test = c["prueba" + b.key] || "Pendiente";
      const opc = c["codigoOperacion" + b.key] || "";
      const kind = test === "OK" ? "good" : test === "Falló" ? "serious" : "warning";
      return `<div class="brand-test">
        <div class="bt-head"><span class="bt-name">${esc(b.short)}</span> ${chip(test, kind)}</div>
        <div class="bt-actions">
          ${TEST_STATES.map((t) => `<button class="btn btn-sm ${t === test ? "btn-primary" : ""}" data-brand="${b.key}" data-result="${t}">${t}</button>`).join("")}
        </div>
        <label class="bt-op">Código de operación
          <input data-opcode="${b.key}" value="${esc(opc)}" placeholder="OP-000000">
        </label>
      </div>`;
    }).join("");

    const refund = refundPending(c)
      ? `<div class="refund-banner pending">
          <div><b>Reembolsos de pruebas pendientes.</b> Al día siguiente de cada prueba aprobada debes emitir el reembolso para que el banco vea liquidar la transacción.</div>
          <button class="btn btn-primary btn-sm" id="cred-refund-done">Marcar reembolsos como emitidos</button>
        </div>`
      : (anyBrandOk(c)
          ? `<div class="refund-banner ok"><div>${CHECK_ICON} Reembolsos de pruebas emitidos.</div>
              <button class="btn btn-sm" id="cred-refund-undo">Deshacer</button></div>`
          : '<div class="refund-banner idle">Aún no hay pruebas aprobadas que requieran reembolso.</div>');

    el.innerHTML = `
      <div class="ad-top"><button class="btn" id="cred-back">← Volver</button></div>
      <div class="card cred-detail-head">
        <div class="cdh-main">
          <div class="cdh-kicker">${credStatusChip(c.status)} Credencial ${esc(c.id)}</div>
          <h1 class="ad-title">${esc(merchantLabel(c.merchantId))}</h1>
          <p class="ad-msg">${esc(c.bank || "—")} · ${esc(procLabel(c.processor))} · ${esc(c.afiliado || "")}</p>
        </div>
        <div class="cdh-actions">
          <button class="btn" id="cred-ver">Ver credenciales</button>
          <button class="btn btn-primary" id="cred-edit">Editar</button>
        </div>
      </div>

      <div class="dash-block" style="margin-top:22px">
        <div class="block-label">Ciclo de vida</div>
        <div class="card">
          ${stepper}
          ${next ? `<div class="stepper-action"><button class="btn btn-primary" id="cred-advance">Avanzar a: ${esc(next.col)} →</button></div>`
            : '<div class="stepper-action"><span class="chip good">✓ Habilitada y configurada</span></div>'}
        </div>
      </div>

      <div class="dash-block">
        <div class="block-label">Pruebas por marca (MC · VISA · AMEX)</div>
        <div class="card">
          <p class="view-sub" style="margin-bottom:14px">Las pruebas se ejecutan en el core real de PagueloFacil. Aquí solo registras el resultado y el código de operación por marca.</p>
          <div class="brand-tests">${brands}</div>
          ${refund}
        </div>
      </div>

      <div class="dash-block">
        <div class="block-label">Datos del expediente</div>
        <div class="card"><div class="ad-grid">
          ${detailField("Comercio", esc(merchantLabel(c.merchantId)))}
          ${detailField("Banco", esc(c.bank || "—"))}
          ${detailField("Procesador", esc(procLabel(c.processor)))}
          ${detailField("Afiliado", esc(c.afiliado || "—"))}
          ${detailField("Fecha de solicitud", c.fechaSolicitud ? esc(fmtDate.format(parseDay(c.fechaSolicitud))) : "—")}
          ${detailField("Rebill", c.rebill ? "Sí" : "No")}
          ${detailField("3DS", c.threeDs ? "Sí" : "No")}
          ${detailField("Tarjetas", esc(c.tarjetas || "—"))}
          ${detailField("Monedas", esc(c.monedas || "—"))}
          ${detailField("Límite por Trx", c.limitePorTrx ? moneyC(c.limitePorTrx) : "—")}
          ${detailField("Límite mensual", c.limiteMensual ? moneyC(c.limiteMensual) : "—")}
          ${detailField("Aprobado por BAC", c.aprobadoPorBac ? "Sí" : "No")}
          ${detailField("Fecha de migración", c.fechaMigracion ? esc(fmtDate.format(parseDay(c.fechaMigracion))) : "—")}
        </div>
        ${c.notas ? `<div class="cred-notas"><div class="ad-k">Notas</div><p>${esc(c.notas)}</p></div>` : ""}
        ${c.observaciones ? `<div class="cred-notas"><div class="ad-k">Observaciones</div><p>${esc(c.observaciones)}</p></div>` : ""}
        </div>
      </div>`;

    $("#cred-back").addEventListener("click", () => { state.credDetailId = null; switchView("credentials"); });
    $("#cred-ver").addEventListener("click", () => openVerCredenciales(c));
    $("#cred-edit").addEventListener("click", () => openCredForm(c));
    if (next) $("#cred-advance").addEventListener("click", () => advanceCred(c));
    const rd = $("#cred-refund-done"); if (rd) rd.addEventListener("click", () => setRefunds(c, true));
    const ru = $("#cred-refund-undo"); if (ru) ru.addEventListener("click", () => setRefunds(c, false));
    el.querySelectorAll("[data-brand]").forEach((b) =>
      b.addEventListener("click", () => setBrandTest(c, b.dataset.brand, b.dataset.result)));
    el.querySelectorAll("[data-opcode]").forEach((inp) =>
      inp.addEventListener("change", () => saveOpCode(c, inp.dataset.opcode, inp.value.trim())));
  }

  // ---------- modales ----------
  function openModal(title, bodyHtml, footHtml) {
    const root = $("#modal-root");
    root.innerHTML = `
      <div class="modal-overlay" data-modal-overlay>
        <div class="modal" role="dialog" aria-modal="true">
          <header class="modal-head"><h2>${title}</h2><button class="modal-close" data-modal-close aria-label="Cerrar">✕</button></header>
          <div class="modal-body">${bodyHtml}</div>
          ${footHtml ? `<footer class="modal-foot">${footHtml}</footer>` : ""}
        </div>
      </div>`;
    root.hidden = false;
    root.querySelectorAll("[data-modal-close]").forEach((b) => b.addEventListener("click", closeModal));
    root.querySelector("[data-modal-overlay]").addEventListener("click", (ev) => {
      if (ev.target === ev.currentTarget) closeModal();
    });
  }
  function closeModal() { const r = $("#modal-root"); r.hidden = true; r.innerHTML = ""; }

  // ---------- puntos de pago ----------

  async function loadPoints() {
    const points = await api("/api/payment-points");
    $("#points-table").innerHTML = `
      <div class="table-wrap"><table class="data">
        <thead><tr><th>ID</th><th>Comercio</th><th>Banco</th><th>Dispositivo</th><th>Serial</th><th>Estado</th></tr></thead>
        <tbody>
          ${points.map((p) => `
            <tr>
              <td>${esc(p.id)}</td>
              <td>${esc(merchantLabel(p.merchantId))}</td>
              <td>${esc(p.bank)}</td>
              <td>${esc(p.deviceType)}</td>
              <td><code>${esc(p.serial)}</code></td>
              <td>${chip(p.status)}</td>
            </tr>`).join("")}
        </tbody>
      </table></div>
      ${points.length === 0 ? '<div class="empty">Todavía no hay puntos de pago registrados.</div>' : ""}`;
  }

  // ---------- documentos ----------

  const DOC_ICON = '<svg viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M6 2h9l5 5v15H6V2zm8 1.5V8h4.5L14 3.5zM8 12h8v2H8v-2zm0 4h8v2H8v-2z"/></svg>';
  const RECO_ICON = '<svg viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M12 2 9.2 8.6 2 9.2l5.5 4.7L5.8 21 12 17.3 18.2 21l-1.7-7.1L22 9.2l-7.2-.6L12 2z"/></svg>';
  const WARN_ICON = '<svg viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M12 2 22 20H2L12 2zm-1 6v6h2V8h-2zm0 8v2h2v-2h-2z"/></svg>';

  // documentos con archivo abrible (formularios/plantillas)
  const DOC_LINKS = {
    "D-001": { url: "codigos-rechazo.html", label: "Abrir catálogo →" },
    "D-008": { url: "ficha-comercio.html", label: "Abrir formulario →" },
  };

  async function loadDocuments() {
    const docs = await api("/api/documents");
    const byCat = {};
    docs.forEach((d) => (byCat[d.category] ??= []).push(d));
    $("#documents-grid").innerHTML = Object.entries(byCat).map(([cat, list]) => `
      <div class="doc-category">
        <h2>${esc(cat)}</h2>
        <div class="doc-grid">
          ${list.map((d) => `
            <div class="doc-card">
              <div class="doc-icon">${DOC_ICON}</div>
              <div>
                <h3>${esc(d.title)}</h3>
                <p>${esc(d.description)}</p>
                <div class="doc-date">Actualizado ${fmtDate.format(parseDay(d.updatedAt))}</div>
                ${DOC_LINKS[d.id] ? `<a class="doc-open" href="${DOC_LINKS[d.id].url}" target="_blank" rel="noopener">${DOC_LINKS[d.id].label}</a>` : ""}
              </div>
            </div>`).join("")}
        </div>
      </div>`).join("");
  }

  // ---------- rentabilidad por adquirente ----------

  let acquirersCache = [];

  // tasa guardada como fracción (0.035) → texto en % (3.5%)
  const ratePct = (frac) => `${(Number(frac || 0) * 100).toLocaleString("es-PA", { maximumFractionDigits: 2 })}%`;

  async function loadProfitability() {
    acquirersCache = await api("/api/acquirers");
    renderAcquirers();
  }

  function renderAcquirers() {
    const el = $("#acquirers-list");
    if (!acquirersCache.length) {
      el.innerHTML = '<div class="empty">Aún no hay adquirentes. Agrega el primero con el formulario de la derecha y aparecerá en la comparación.</div>';
      return;
    }
    el.innerHTML = acquirersCache.map((a) => `
      <div class="rule-item">
        <div class="rule-body">
          <div class="rule-name">${esc(a.name)}</div>
          <div class="rule-desc">Nacional ${ratePct(a.nationalCostRate)} · Internacional ${ratePct(a.internationalCostRate)} · ${moneyC(a.fixedCostPerTx)} por trx${a.monthlyFixedCost ? ` · ${moneyC(a.monthlyFixedCost)} fijo mensual` : ""}</div>
        </div>
        <button class="btn btn-ghost" data-edit-acq="${esc(a.id)}" title="Editar">Editar</button>
        <button class="btn btn-ghost" data-del-acq="${esc(a.id)}" title="Eliminar">✕</button>
      </div>`).join("");

    el.querySelectorAll("[data-edit-acq]").forEach((btn) => {
      btn.addEventListener("click", () => editAcquirer(btn.dataset.editAcq));
    });
    el.querySelectorAll("[data-del-acq]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        await api(`/api/acquirers/${encodeURIComponent(btn.dataset.delAcq)}`, { method: "DELETE" });
        await loadProfitability();
      });
    });
  }

  function editAcquirer(id) {
    const a = acquirersCache.find((x) => x.id === id);
    if (!a) return;
    $("#a-id").value = a.id;
    $("#a-name").value = a.name;
    $("#a-nat-rate").value = +(a.nationalCostRate * 100).toFixed(4);
    $("#a-intl-rate").value = +(a.internationalCostRate * 100).toFixed(4);
    $("#a-fee").value = a.fixedCostPerTx;
    $("#a-monthly").value = a.monthlyFixedCost;
    $("#acq-form-title").textContent = "Editar adquirente";
    $("#acq-submit").textContent = "Guardar cambios";
    $("#acq-cancel").hidden = false;
    $("#a-name").focus();
  }

  function resetAcqForm() {
    $("#acq-form").reset();
    $("#a-id").value = "";
    $("#a-monthly").value = "0";
    $("#acq-form-title").textContent = "Nuevo adquirente";
    $("#acq-submit").textContent = "Agregar adquirente";
    $("#acq-cancel").hidden = true;
  }

  async function onAcqSubmit(ev) {
    ev.preventDefault();
    const id = $("#a-id").value;
    const body = {
      name: $("#a-name").value.trim(),
      nationalCostRate: (Number($("#a-nat-rate").value) || 0) / 100,
      internationalCostRate: (Number($("#a-intl-rate").value) || 0) / 100,
      fixedCostPerTx: Number($("#a-fee").value) || 0,
      monthlyFixedCost: Number($("#a-monthly").value) || 0,
    };
    await api(id ? `/api/acquirers/${encodeURIComponent(id)}` : "/api/acquirers", {
      method: id ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    resetAcqForm();
    await loadProfitability();
  }

  async function onCalcSubmit(ev) {
    ev.preventDefault();
    const body = {
      merchantName: $("#c-name").value.trim(),
      natVol: Number($("#c-nat-vol").value) || 0,
      natTx: Number($("#c-nat-tx").value) || 0,
      intlVol: Number($("#c-intl-vol").value) || 0,
      intlTx: Number($("#c-intl-tx").value) || 0,
      pfRate: (Number($("#c-pf-rate").value) || 0) / 100,
      pfFee: Number($("#c-pf-fee").value) || 0,
    };
    const res = await api("/api/profitability/calculate", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    renderCalcResult(res);
  }

  function renderCalcResult(res) {
    const el = $("#calc-result");
    const who = res.merchantName ? esc(res.merchantName) : "el comercio";

    if (!res.rows.length) {
      el.innerHTML = `
        <div class="calc-empty card">
          <div class="empty">Aún no hay adquirentes para comparar. Agrega al menos uno abajo y volvemos a calcular al instante.</div>
        </div>`;
      return;
    }
    if (res.totalVol === 0 && res.totalTx === 0) {
      el.innerHTML = `
        <div class="calc-empty card">
          <div class="empty">Ingresa el volumen y las transacciones de ${who} para comparar la rentabilidad por adquirente.</div>
        </div>`;
      return;
    }

    const best = res.rows[0];
    const bestPositive = best.profit >= 0;
    const callout = `
      <div class="reco ${bestPositive ? "" : "reco-warn"}">
        <div class="reco-ico">${bestPositive ? RECO_ICON : WARN_ICON}</div>
        <div class="reco-body">
          <div class="reco-kicker">${bestPositive ? "Adquirente más rentable" : "Mejor opción disponible (margen negativo)"}</div>
          <div class="reco-title">${esc(best.acquirerName)} — ${pct(best.profitabilityPct)} de margen</div>
          <div class="reco-sub">Con ${who}, PagueloFacil gana <b>${moneyC(best.profit)}</b> al mes sobre un ingreso de ${moneyC(best.pfRevenue)}. ${bestPositive ? "Es el mejor destino para afiliar este comercio." : "Ningún adquirente deja ganancia con estos números; revisa el pricing o los costos."}</div>
        </div>
      </div>`;

    const summary = `
      <div class="calc-summary">
        <span>Volumen total <b>${moneyC(res.totalVol)}</b></span>
        <span>Transacciones <b>${int(res.totalTx)}</b></span>
        <span>Pricing PF <b>${ratePct(res.pfRate)} + ${moneyC(res.pfFee)}/trx</b></span>
        <span>Ingreso PF <b>${moneyC(res.pfRevenue)}</b></span>
      </div>`;

    const rows = res.rows.map((r, i) => {
      const profitClass = r.profit < 0 ? "neg" : "pos";
      return `
        <tr class="${i === 0 ? "top-row" : ""}">
          <td>${i === 0 ? '<span class="rank-badge">1º</span>' : `<span class="rank-num">${i + 1}º</span>`}${esc(r.acquirerName)}</td>
          <td class="num">${moneyC(r.pfRevenue)}</td>
          <td class="num">${moneyC(r.acquirerCost)}
            <div class="cost-break">Nac ${moneyC(r.nationalCost)} · Int ${moneyC(r.internationalCost)} · Trx ${moneyC(r.txCost)}${r.monthlyFixed ? ` · Fijo ${moneyC(r.monthlyFixed)}` : ""}</div>
          </td>
          <td class="num ${profitClass}"><b>${moneyC(r.profit)}</b></td>
          <td class="num ${profitClass}">${pct(r.profitabilityPct)}</td>
        </tr>`;
    }).join("");

    el.innerHTML = `
      <div class="card calc-result-card">
        ${callout}
        ${summary}
        <div class="table-wrap"><table class="data calc-table">
          <thead><tr>
            <th>Adquirente</th>
            <th class="num">Ingreso PF</th>
            <th class="num">Costo adquirente</th>
            <th class="num">Ganancia</th>
            <th class="num">Rentabilidad</th>
          </tr></thead>
          <tbody>${rows}</tbody>
        </table></div>
      </div>`;
  }

  // ---------- reglas de alertas y planes de acción ----------

  const planName = (id) => (state.actionPlans.find((p) => p.id === id) || {}).name || null;

  async function loadRulesAndPlans() {
    // los planes primero: alimentan el selector de la regla y la lista
    state.actionPlans = await api("/api/action-plans");
    renderPlans();
    populatePlanSelect();
    await loadRules();
  }

  function populatePlanSelect() {
    const sel = $("#r-actionplan");
    const current = sel.value;
    sel.innerHTML = '<option value="">Selecciona un plan…</option>';
    state.actionPlans.forEach((p) => sel.append(new Option(p.name, p.id)));
    if (current) sel.value = current;
  }

  function renderPlans() {
    const el = $("#plans-list");
    if (!state.actionPlans.length) {
      el.innerHTML = '<div class="empty">Aún no hay planes de acción. Crea el primero con el formulario.</div>';
      return;
    }
    el.innerHTML = state.actionPlans.map((p) => `
      <div class="rule-item">
        <div class="rule-body">
          <div class="rule-name">${esc(p.name)}</div>
          <div class="rule-desc">${esc(p.description || "")}</div>
          <div class="rule-tags">${chip(`${(p.steps || []).length} paso${(p.steps || []).length !== 1 ? "s" : ""}`, "neutral")}</div>
        </div>
        <button class="btn btn-ghost" data-del-plan="${esc(p.id)}" title="Eliminar">✕</button>
      </div>`).join("");

    el.querySelectorAll("[data-del-plan]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        const res = await fetch(`/api/action-plans/${encodeURIComponent(btn.dataset.delPlan)}`, { method: "DELETE" });
        if (res.status === 409) {
          alert("No puedes eliminar este plan: sigue enlazado a una o más reglas.");
          return;
        }
        loadRulesAndPlans();
      });
    });
  }

  async function onCreatePlan(ev) {
    ev.preventDefault();
    const steps = $("#ap-steps").value.split("\n").map((s) => s.trim()).filter(Boolean);
    await api("/api/action-plans", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: $("#ap-name").value.trim(),
        description: $("#ap-desc").value.trim(),
        steps,
      }),
    });
    $("#ap-form").reset();
    await loadRulesAndPlans();
  }

  async function loadRules() {
    const rules = await api("/api/alert-rules");
    const conditionLabel = (c) => (CONDITIONS.find((x) => x.value === c) || {}).label || c;
    $("#rules-list").innerHTML = rules.map((r) => `
      <div class="rule-item">
        <div class="rule-body">
          <div class="rule-name">${esc(r.name)}</div>
          <div class="rule-desc">${esc(conditionLabel(r.condition))} · umbral ${r.threshold}${r.drCode ? ` · código ${esc(r.drCode)}` : ""}</div>
          <div class="rule-tags">
            ${r.standard ? chip("Regla estándar", "neutral") : chip("Personalizada", "neutral")}
            ${r.scope === "MERCHANT" ? chip(merchantLabel(r.merchantId), "neutral") : chip("Global", "neutral")}
            ${r.actionPlanId ? chip("Plan: " + (planName(r.actionPlanId) || r.actionPlanId), "good") : chip("Sin plan", "warning")}
          </div>
        </div>
        <label class="switch" title="${r.enabled ? "Desactivar" : "Activar"}">
          <input type="checkbox" data-toggle="${esc(r.id)}" ${r.enabled ? "checked" : ""}>
          <span class="track"></span>
        </label>
        ${r.standard ? "" : `<button class="btn btn-ghost" data-del="${esc(r.id)}" title="Eliminar">✕</button>`}
      </div>`).join("");

    document.querySelectorAll("[data-toggle]").forEach((sw) => {
      sw.addEventListener("change", async () => {
        const rule = rules.find((r) => r.id === sw.dataset.toggle);
        await api(`/api/alert-rules/${encodeURIComponent(sw.dataset.toggle)}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ ...rule, enabled: sw.checked }),
        });
      });
    });

    document.querySelectorAll("[data-del]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        await api(`/api/alert-rules/${encodeURIComponent(btn.dataset.del)}`, { method: "DELETE" });
        loadRules();
      });
    });
  }

  async function onCreateRule(ev) {
    ev.preventDefault();
    const scope = $("#r-scope").value;
    const condition = $("#r-condition").value;
    const actionPlanId = $("#r-actionplan").value;
    if (!actionPlanId) { $("#r-actionplan").focus(); return; }
    await api("/api/alert-rules", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: $("#r-name").value,
        scope,
        merchantId: scope === "MERCHANT" ? $("#r-merchant").value : null,
        condition,
        threshold: Number($("#r-threshold").value),
        drCode: condition === "DR_CODE_RECURRENT" ? $("#r-drcode").value : null,
        actionPlanId,
        standard: false,
        enabled: true,
      }),
    });
    $("#rule-form").reset();
    $("#r-merchant-wrap").hidden = true;
    $("#r-drcode-wrap").hidden = true;
    loadRulesAndPlans();
  }

  // ---------- arranque ----------

  init().catch((err) => {
    console.error(err);
    document.body.insertAdjacentHTML("beforeend",
      `<div style="position:fixed;bottom:12px;left:12px;background:var(--red-100);color:var(--red-700);padding:12px 16px;border-radius:12px;font-size:13px;max-width:340px;box-shadow:0 6px 20px rgba(20,30,40,.14)">` +
      `Tuvimos un problema al cargar tus datos. Ya estamos en ello; vuelve a intentarlo en un momento.` +
      `<span style="display:block;color:var(--muted);font-size:11.5px;margin-top:4px">${esc(err.message)}</span></div>`);
  });
})();
