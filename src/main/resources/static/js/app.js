/* ============================================================
   Core de Adquirencia — PagueloFacil
   SPA sin dependencias: estado, API, gráficas SVG y vistas.
   ============================================================ */

(() => {
  "use strict";

  // ---------- constantes ----------

  const SERIES = ["#55990F", "#3B62AD", "#C08A00", "#128F6C"]; // paleta validada
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
    if (!daily.length) { container.innerHTML = '<div class="empty">Sin datos en el periodo.</div>'; return; }

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
      svg.append(svgEl("line", { x1: ml, x2: W - mr, y1: gy, y2: gy, stroke: "#e1e0d9", "stroke-width": 1 }));
      const t = svgEl("text", { x: ml - 8, y: gy + 4, "text-anchor": "end", "font-size": 11, fill: "#898781" });
      t.textContent = int(max * (1 - g / 4));
      svg.append(t);
    }

    const step = Math.max(1, Math.ceil(daily.length / 7));
    daily.forEach((d, i) => {
      if (i % step !== 0 && i !== daily.length - 1) return;
      const t = svgEl("text", { x: x(i), y: H - 8, "text-anchor": "middle", "font-size": 11, fill: "#898781" });
      t.textContent = fmtDay.format(parseDay(d.date));
      svg.append(t);
    });

    const linePts = values.map((v, i) => `${x(i)},${y(v)}`).join(" ");
    const areaD = `M ${ml},${mt + ih} L ${linePts.replaceAll(" ", " L ")} L ${x(daily.length - 1)},${mt + ih} Z`;
    svg.append(svgEl("path", { d: areaD, fill: SERIES[0], opacity: 0.12 }));
    svg.append(svgEl("polyline", { points: linePts, fill: "none", stroke: SERIES[0], "stroke-width": 2, "stroke-linejoin": "round" }));
    svg.append(svgEl("line", { x1: ml, x2: W - mr, y1: mt + ih, y2: mt + ih, stroke: "#c3c2b7", "stroke-width": 1 }));

    const cross = svgEl("line", { y1: mt, y2: mt + ih, stroke: "#898781", "stroke-width": 1, "stroke-dasharray": "3 3", visibility: "hidden" });
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
      container.innerHTML = '<div class="empty">Sin datos en el periodo.</div>';
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
      const label = svgEl("text", { x: labelW - 8, y: cy + 4, "text-anchor": "end", "font-size": 12, fill: "#52514e" });
      label.textContent = item.label;
      svg.append(label);

      const w = Math.max(2, (item.value / max) * bw);
      const barY = cy - 9;
      // extremo del dato redondeado (4px), base plana
      const r = Math.min(4, w);
      const d = `M ${labelW} ${barY} h ${w - r} a ${r} ${r} 0 0 1 ${r} ${r} v ${18 - 2 * r} a ${r} ${r} 0 0 1 -${r} ${r} h -${w - r} Z`;
      const bar = svgEl("path", { d, fill: item.color });
      svg.append(bar);

      const val = svgEl("text", { x: labelW + w + 8, y: cy + 4, "font-size": 12, "font-weight": 600, fill: "#20261A" });
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
    merchants.forEach((m) => mSel.append(new Option(m.name, m.id)));

    const sSel = $("#f-service");
    SERVICES.forEach((s) => sSel.append(new Option(s.label, s.value)));

    const rSel = $("#r-merchant");
    merchants.forEach((m) => rSel.append(new Option(m.name, m.id)));

    const cSel = $("#r-condition");
    CONDITIONS.forEach((c) => cSel.append(new Option(c.label, c.value)));

    const dSel = $("#r-drcode");
    Object.entries(drCodes).forEach(([code, desc]) => dSel.append(new Option(`${code} — ${desc}`, code)));

    buildComparePicker();
    wireEvents();
    switchView("dashboard");
    await loadDashboard();
    $("#last-updated").textContent =
      `Actualizado ${fmtDateTime.format(new Date())}`;
  }

  function wireEvents() {
    document.querySelectorAll(".nav-item").forEach((item) => {
      item.addEventListener("click", () => switchView(item.dataset.view));
    });

    // grupos colapsables del sidebar
    document.querySelectorAll(".nav-group-head").forEach((head) => {
      head.addEventListener("click", () => head.parentElement.classList.toggle("open"));
    });

    // hamburguesa: contrae el sidebar (en móvil lo abre como panel flotante)
    $("#menu-toggle").addEventListener("click", () => {
      if (window.matchMedia("(max-width: 860px)").matches) {
        document.body.classList.toggle("nav-open");
      } else {
        document.body.classList.toggle("nav-collapsed");
      }
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

    $("#cred-proc-filter").addEventListener("change", renderCredentials);
    $("#cred-status-filter").addEventListener("change", renderCredentials);
  }

  function switchView(view) {
    state.view = view;
    document.querySelectorAll(".nav-item").forEach((t) =>
      t.classList.toggle("active", t.dataset.view === view));
    document.querySelectorAll(".nav-group").forEach((g) =>
      g.classList.toggle("current", !!g.querySelector(`.nav-item[data-view="${view}"]`)));
    document.querySelectorAll(".view").forEach((v) => { v.hidden = true; });
    $(`#view-${view === "alert-config" ? "alert-config" : view}`).hidden = false;
    document.body.classList.remove("nav-open");

    if (view === "projects") loadProjects();
    if (view === "credentials") loadCredentials();
    if (view === "points") loadPoints();
    if (view === "documents") loadDocuments();
    if (view === "alert-config") loadRules();
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
    renderKpis(summary, alerts);
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
    const delta = s.volume.deltaPct;
    const deltaHtml = delta == null ? '<span class="tt-muted">sin periodo previo</span>'
      : `<span class="delta ${delta >= 0 ? "up" : "down"}">${delta >= 0 ? "▲" : "▼"} ${pct(Math.abs(delta))}</span> vs periodo anterior`;

    $("#kpis").innerHTML = `
      <div class="kpi">
        <div class="kpi-accent" style="background:${SERIES[0]}"></div>
        <div class="kpi-label">Volumen procesado</div>
        <div class="kpi-value">${money(s.volume.total)}</div>
        <div class="kpi-foot">${deltaHtml}</div>
      </div>
      <div class="kpi">
        <div class="kpi-accent" style="background:${SERIES[1]}"></div>
        <div class="kpi-label">Transacciones</div>
        <div class="kpi-value">${int(s.tx.total)}</div>
        <div class="kpi-foot">${int(s.tx.approved)} aprobadas · ${int(s.tx.declined)} rechazadas · ${pct(s.tx.approvalRate)} aprobación</div>
      </div>
      <div class="kpi">
        <div class="kpi-accent" style="background:${SERIES[3]}"></div>
        <div class="kpi-label">Índice de reembolsos</div>
        <div class="kpi-value">${pct(s.refunds.pctOfVolume)}</div>
        <div class="kpi-foot">${moneyC(s.refunds.amount)} en ${int(s.refunds.count)} reembolsos</div>
      </div>
      <div class="kpi">
        <div class="kpi-accent" style="background:${critical ? "var(--status-critical)" : "var(--pf-gray)"}"></div>
        <div class="kpi-label">Alertas activas</div>
        <div class="kpi-value">${activeAlerts.length}</div>
        <div class="kpi-foot">${critical ? `${critical} crítica${critical > 1 ? "s" : ""}` : "sin alertas críticas"}</div>
      </div>`;

    const pill = $("#alert-pill");
    pill.hidden = activeAlerts.length === 0;
    $("#alert-pill-count").textContent =
      `${activeAlerts.length} alerta${activeAlerts.length !== 1 ? "s" : ""}`;
  }

  function renderAlerts(alerts) {
    const el = $("#alert-list");
    if (!alerts.length) {
      el.innerHTML = '<div class="empty">Sin alertas activas. Todo el procesamiento dentro de los umbrales.</div>';
      return;
    }
    el.innerHTML = alerts.map((a) => `
      <div class="alert-item ${SEVERITY_CLASS[a.severity]} ${a.acknowledged ? "acked" : ""}">
        <div class="alert-body">
          <div class="alert-title">${chip(SEVERITY_LABEL[a.severity], SEVERITY_CLASS[a.severity])} ${esc(a.ruleName)}</div>
          <div class="alert-msg">${esc(a.message)}</div>
          <div class="alert-meta">${a.merchantName ? esc(a.merchantName) + " · " : ""}${a.processor ? esc(a.processor) + " · " : ""}hoy</div>
        </div>
        ${a.acknowledged ? "" : `<button class="btn btn-ghost" data-ack="${esc(a.id)}">OK</button>`}
      </div>`).join("");

    el.querySelectorAll("[data-ack]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        await api(`/api/alerts/${encodeURIComponent(btn.dataset.ack)}/ack`, { method: "POST" });
        loadDashboard();
      });
    });
  }

  function renderProcessors(s) {
    const colors = [SERIES[0], SERIES[1]];
    hBars($("#chart-processors"),
      s.processors.map((p, i) => ({
        label: p.label,
        value: Number(p.volume),
        display: money(p.volume),
        color: colors[i],
        tooltip: `<div class="tt-title">${esc(p.label)}</div>` +
          `<div>Volumen: <b>${moneyC(p.volume)}</b> (${pct(p.sharePct)} del total)</div>` +
          `<div class="tt-muted">${int(p.txCount)} trx · ${pct(p.approvalRate)} aprobación</div>`,
      })),
      { legend: s.processors.map((p, i) => ({ label: `${p.label} · ${pct(p.approvalRate)} aprob.`, color: colors[i] })) });
  }

  function renderServices(s) {
    // barras nominales: una sola serie → un solo tono (slot 1), sin leyenda
    hBars($("#chart-services"),
      s.services.filter((x) => x.txCount > 0).map((x) => ({
        label: x.label,
        value: Number(x.volume),
        display: money(x.volume),
        color: SERIES[0],
        tooltip: `<div class="tt-title">${esc(x.label)}</div>` +
          `<div>Volumen: <b>${moneyC(x.volume)}</b> (${pct(x.sharePct)} del total)</div>` +
          `<div class="tt-muted">${int(x.txCount)} trx</div>`,
      })));
  }

  function render3ds(s) {
    const t = s.threeDs;
    const colors = [SERIES[0], SERIES[1]];
    hBars($("#chart-3ds"), [
      {
        label: "Con 3DS", value: t.withApprovalRate ?? 0, display: pct(t.withApprovalRate), color: colors[0],
        tooltip: `<div class="tt-title">Con 3DS</div><div>${int(t.withCount)} trx · aprobación <b>${pct(t.withApprovalRate)}</b></div>`,
      },
      {
        label: "Sin 3DS", value: t.withoutApprovalRate ?? 0, display: pct(t.withoutApprovalRate), color: colors[1],
        tooltip: `<div class="tt-title">Sin 3DS</div><div>${int(t.withoutCount)} trx · aprobación <b>${pct(t.withoutApprovalRate)}</b></div>`,
      },
    ], {
      legend: [
        { label: `Con 3DS · ${pct(t.withSharePct)} de las trx`, color: colors[0] },
        { label: "Sin 3DS", color: colors[1] },
      ],
    });
  }

  function renderDrTable(s) {
    const el = $("#dr-table");
    if (!s.topDeclineCodes.length) {
      el.innerHTML = '<div class="empty">Sin rechazos en el periodo.</div>';
      return;
    }
    el.innerHTML = `
      <div class="table-wrap"><table class="data">
        <thead><tr><th>Código</th><th>Descripción</th><th class="num">Rechazos</th><th class="num">% del total</th></tr></thead>
        <tbody>
          ${s.topDeclineCodes.map((c) => `
            <tr>
              <td><b>${esc(c.code)}</b></td>
              <td>${esc(c.description)}</td>
              <td class="num">${int(c.count)}</td>
              <td class="num">${pct(c.pctOfDeclined)}</td>
            </tr>`).join("")}
        </tbody>
      </table></div>`;
  }

  // ---------- comparador de comercios ----------

  function buildComparePicker() {
    const el = $("#compare-picker");
    el.innerHTML = "";
    state.merchants.forEach((m) => {
      const btn = document.createElement("button");
      btn.className = "pick-chip";
      btn.type = "button";
      btn.textContent = m.name;
      btn.dataset.id = m.id;
      btn.addEventListener("click", () => {
        const i = state.compareSelection.indexOf(m.id);
        if (i >= 0) state.compareSelection.splice(i, 1);
        else if (state.compareSelection.length < 4) state.compareSelection.push(m.id);
        updateComparePicker();
        renderCompare();
      });
      el.append(btn);
    });
    state.compareSelection = state.merchants.slice(0, 3).map((m) => m.id);
    updateComparePicker();
  }

  function updateComparePicker() {
    document.querySelectorAll(".pick-chip").forEach((c) => {
      const selected = state.compareSelection.includes(c.dataset.id);
      c.classList.toggle("selected", selected);
      c.disabled = !selected && state.compareSelection.length >= 4;
    });
  }

  async function renderCompare() {
    const chartEl = $("#chart-compare");
    const tableEl = $("#compare-table");
    if (state.compareSelection.length < 2) {
      chartEl.innerHTML = '<div class="empty">Elige al menos 2 comercios para comparar.</div>';
      tableEl.innerHTML = "";
      return;
    }
    const { from, to } = currentRange();
    const p = new URLSearchParams({ merchantIds: state.compareSelection.join(",") });
    if ($("#f-service").value) p.set("service", $("#f-service").value);
    if (from) p.set("from", from);
    if (to) p.set("to", to);
    const rows = await api(`/api/dashboard/compare?${p}`);

    // el color sigue al comercio según su orden de selección (fijo, no por ranking)
    const colorById = {};
    state.compareSelection.forEach((id, i) => { colorById[id] = SERIES[i]; });

    hBars(chartEl, rows.map((r) => ({
      label: r.merchantName.length > 20 ? r.merchantName.slice(0, 19) + "…" : r.merchantName,
      value: Number(r.volume),
      display: money(r.volume),
      color: colorById[r.merchantId],
      tooltip: `<div class="tt-title">${esc(r.merchantName)}</div>` +
        `<div>Volumen: <b>${moneyC(r.volume)}</b></div>` +
        `<div class="tt-muted">${int(r.txCount)} trx · ${pct(r.approvalRate)} aprobación · reembolsos ${pct(r.refundPct)}</div>`,
    })), { width: 620, labelWidth: 168 });

    tableEl.innerHTML = `
      <div class="table-wrap"><table class="data">
        <thead><tr><th>Comercio</th><th class="num">Volumen</th><th class="num">Trx</th><th class="num">Aprobación</th><th class="num">Reembolsos</th></tr></thead>
        <tbody>
          ${rows.map((r) => `
            <tr>
              <td><span class="swatch" style="display:inline-block;width:9px;height:9px;border-radius:2px;background:${colorById[r.merchantId]};margin-right:7px"></span>${esc(r.merchantName)}</td>
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

  // ---------- credenciales ----------

  let credentialsCache = [];

  async function loadCredentials() {
    credentialsCache = await api("/api/credentials");
    renderCredentials();
  }

  function renderCredentials() {
    const proc = $("#cred-proc-filter").value;
    const status = $("#cred-status-filter").value;
    const rows = credentialsCache
      .filter((c) => !proc || c.processor === proc)
      .filter((c) => !status || c.status === status);

    $("#credentials-table").innerHTML = `
      <div class="table-wrap"><table class="data">
        <thead><tr>
          <th>ID</th><th>Comercio</th><th>Procesador</th><th>DBA</th><th>Tipo</th><th>Estado</th><th>Último envío</th><th></th>
        </tr></thead>
        <tbody>
          ${rows.map((c) => `
            <tr>
              <td>${esc(c.id)}</td>
              <td>${esc(merchantName(c.merchantId))}</td>
              <td>${c.processor === "POWERTRANZ" ? "PowerTranz" : "Evertec"}</td>
              <td><code>${esc(c.dba)}</code></td>
              <td>${esc(c.credentialType)}</td>
              <td>${chip(c.status)}</td>
              <td>${c.lastSentAt ? fmtDateTime.format(new Date(c.lastSentAt)) : "—"}</td>
              <td><button class="btn" data-resend="${esc(c.id)}">Reenviar</button></td>
            </tr>`).join("")}
        </tbody>
      </table></div>
      ${rows.length === 0 ? '<div class="empty">Sin credenciales con esos filtros.</div>' : ""}`;

    document.querySelectorAll("[data-resend]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        btn.disabled = true;
        btn.textContent = "Enviando…";
        await api(`/api/credentials/${encodeURIComponent(btn.dataset.resend)}/resend`, { method: "POST" });
        await loadCredentials();
      });
    });
  }

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
              <td>${esc(merchantName(p.merchantId))}</td>
              <td>${esc(p.bank)}</td>
              <td>${esc(p.deviceType)}</td>
              <td><code>${esc(p.serial)}</code></td>
              <td>${chip(p.status)}</td>
            </tr>`).join("")}
        </tbody>
      </table></div>
      ${points.length === 0 ? '<div class="empty">Sin puntos de pago registrados.</div>' : ""}`;
  }

  // ---------- documentos ----------

  const DOC_ICON = '<svg viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M6 2h9l5 5v15H6V2zm8 1.5V8h4.5L14 3.5zM8 12h8v2H8v-2zm0 4h8v2H8v-2z"/></svg>';

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
              </div>
            </div>`).join("")}
        </div>
      </div>`).join("");
  }

  // ---------- reglas de alertas ----------

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
            ${r.scope === "MERCHANT" ? chip(merchantName(r.merchantId), "neutral") : chip("Global", "neutral")}
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
        standard: false,
        enabled: true,
      }),
    });
    $("#rule-form").reset();
    $("#r-merchant-wrap").hidden = true;
    $("#r-drcode-wrap").hidden = true;
    loadRules();
  }

  // ---------- arranque ----------

  init().catch((err) => {
    console.error(err);
    document.body.insertAdjacentHTML("beforeend",
      `<div style="position:fixed;bottom:12px;left:12px;background:#FBE7E7;color:#8f1f1f;padding:10px 14px;border-radius:10px;font-size:13px">Error cargando datos: ${esc(err.message)}</div>`);
  });
})();
