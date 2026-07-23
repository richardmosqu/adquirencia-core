/* ============================================================
   Brújula PF — SPA sin dependencias: evaluar leads + editar KB.
   ============================================================ */
(() => {
  "use strict";

  const $ = (s) => document.querySelector(s);
  const esc = (s) => String(s ?? "")
    .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;");

  async function api(path, opts) {
    const res = await fetch(path, opts);
    if (!res.ok) throw new Error(`${res.status} en ${path}`);
    return res.status === 204 ? null : res.json();
  }

  const CANALES = ["Físico", "En línea", "Mixto"];
  const ANGLE_CANALES = ["Físico", "En línea", "Mixto", "Cualquiera"];
  const TRIGGERS = ["Cualquiera", "Físico", "En línea", "Mixto", "Pequeño", "Mediano", "Grande"];
  const NEED_CHIPS = ["Cobrar en línea", "Suscripciones / recurrente", "Cobro presencial",
    "Integrar por API", "Cobrar por redes / link", "Tienda web / ecommerce"];

  const CHECK = '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"/></svg>';
  const CROSS = '<svg viewBox="0 0 24 24"><path fill="currentColor" d="m6.4 5 5.6 5.6L17.6 5 19 6.4 13.4 12 19 17.6 17.6 19 12 13.4 6.4 19 5 17.6 10.6 12 5 6.4z"/></svg>';
  const WARN = '<svg viewBox="0 0 24 24"><path fill="currentColor" d="M12 2 22.5 20.5h-21L12 2zm-1 6.5v6h2v-6h-2zm0 8v2h2v-2h-2z"/></svg>';

  const state = { kb: { rubros: [], services: [], angles: [], objections: [] } };

  const BLANK = {
    rubros: { name: "", allowed: true, nota: "" },
    services: { code: "", name: "", descripcion: "", canales: [], tamanos: [], needs: [], peso: 10 },
    angles: { canal: "Cualquiera", angulo: "" },
    objections: { trigger: "Cualquiera", objecion: "", respuesta: "" },
  };

  // ---------- navegación ----------
  function switchView(v) {
    document.querySelectorAll(".topnav-item").forEach((b) => b.classList.toggle("active", b.dataset.view === v));
    document.querySelectorAll(".view").forEach((s) => { s.hidden = true; });
    $("#view-" + v).hidden = false;
    window.scrollTo(0, 0);
    if (v === "kb") renderKb();
    if (v === "historial") loadHistory();
  }

  // ---------- evaluar (manual) ----------
  async function onEvaluate(ev) {
    ev.preventDefault();
    const nombre = $("#f-nombre").value.trim();
    const body = {
      nombreComercio: nombre,
      rubro: $("#f-rubro").value.trim(),
      tamano: $("#f-tamano").value,
      volumenEstimado: Number($("#f-volumen").value) || 0,
      canal: $("#f-canal").value,
      necesidad: $("#f-necesidad").value.trim(),
    };
    const a = await api("/api/evaluate", {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
    });
    renderManualResults(a, $("#results"), nombre);
    $("#results").scrollIntoView({ behavior: "smooth", block: "nearest" });
  }

  function eligMeta(estado) {
    const s = (estado || "").toLowerCase();
    if (s.startsWith("no ")) return { cls: "elig-no", icon: CROSS };       // No elegible / No permitido
    if (s === "elegible" || s === "permitido") return { cls: "elig-ok", icon: CHECK };
    return { cls: "elig-cond", icon: WARN };                                // condiciones / condicionado
  }

  function renderManualResults(a, container, nombre) {
    const e = a.eligibility, m = eligMeta(e.estado);
    const portafolio = a.portafolio || [], est = a.estrategia;
    container.innerHTML = `
      ${nombre ? `<div class="result-lead-head"><h2>${esc(nombre)}</h2><span class="origin-badge manual">Lead manual</span></div>` : ""}
      <div class="card result-block">
        <div class="block-label">1 · Validación de elegibilidad</div>
        <div class="elig-head"><span class="elig-badge ${m.cls}">${m.icon} ${esc(e.estado)}</span></div>
        <ul class="elig-list">${(e.razones || []).map((r) => `<li>${esc(r)}</li>`).join("")}</ul>
        ${(e.riesgos && e.riesgos.length) ? `<div class="elig-risk"><h4>Riesgos / condiciones</h4>
          <ul class="elig-list">${e.riesgos.map((r) => `<li>${esc(r)}</li>`).join("")}</ul></div>` : ""}
      </div>
      <div class="card result-block">
        <div class="block-label">2 · Portafolio priorizado de servicios PF</div>
        ${portafolio.length ? portafolio.map((s, i) => `
          <div class="reco-item ${i === 0 ? "top" : ""}">
            <span class="reco-rank">${i + 1}</span>
            <div><div class="reco-name">${esc(s.name)}</div><div class="reco-why">${esc(s.why)}</div></div>
          </div>`).join("") : '<div class="reco-empty">Sin servicios recomendados para este lead.</div>'}
      </div>
      <div class="card result-block">
        <div class="block-label">3 · Estrategia comercial sugerida</div>
        <div class="angle">${esc(est.angulo)}</div>
        ${(est.objeciones && est.objeciones.length) ? est.objeciones.map((o) => `
          <div class="obj-item"><div class="obj-q">${esc(o.objecion)}</div>
            <div class="obj-a"><b>Respuesta:</b> ${esc(o.respuesta)}</div></div>`).join("")
          : '<div class="reco-empty">Sin objeciones sugeridas.</div>'}
      </div>`;
  }

  // ---------- analizar por link (Gemini) ----------
  async function onAnalyzeLink(ev) {
    ev.preventDefault();
    const url = $("#f-url").value.trim();
    if (!url) { $("#f-url").focus(); return; }
    const btn = $("#link-submit"), prev = btn.textContent;
    btn.disabled = true; btn.textContent = "Analizando…";
    $("#link-results").innerHTML = `<div class="analyzing card"><span class="spinner"></span>
      <p>Leyendo el sitio y consultando la IA… puede tardar unos segundos.</p></div>`;
    try {
      const r = await api("/api/analyze-link", {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ url }),
      });
      if (!r.ok) {
        $("#link-results").innerHTML = `<div class="card error-card">${esc(r.error || "No pudimos analizar el sitio.")}</div>`;
      } else {
        renderLinkResults(r.analysis, $("#link-results"));
      }
    } catch (e) {
      $("#link-results").innerHTML = '<div class="card error-card">No pudimos completar el análisis. Inténtalo de nuevo.</div>';
    } finally {
      btn.disabled = false; btn.textContent = prev;
    }
  }

  const pgField = (k, v) => `<div class="pg-field"><span class="pg-k">${esc(k)}</span><span class="pg-v">${esc(v || "—")}</span></div>`;

  function renderLinkResults(a, container) {
    a = a || {};
    const el = a.elegibilidad || {}, m = eligMeta(el.estado);
    const port = a.portafolioPF || [], est = a.estrategiaAfiliacion || {};
    const prod = a.productosServicios || [];
    container.innerHTML = `
      <div class="result-lead-head"><h2>${esc(a.nombreComercio || "Comercio")}</h2><span class="origin-badge link">Analizado por link</span></div>
      <div class="card result-block">
        <div class="block-label">Perfil del comercio</div>
        <div class="profile-grid">
          ${pgField("Rubro", a.rubro)}
          ${pgField("Canal", a.canal)}
          ${pgField("Tamaño estimado", a.tamanoEstimado)}
          ${pgField("País / idioma", a.paisIdioma)}
        </div>
        ${a.descripcion ? `<p class="profile-desc">${esc(a.descripcion)}</p>` : ""}
        ${prod.length ? `<div class="tags-row">${prod.map((p) => `<span class="tag">${esc(p)}</span>`).join("")}</div>` : ""}
      </div>
      <div class="card result-block">
        <div class="block-label">1 · Validación de elegibilidad</div>
        <div class="elig-head"><span class="elig-badge ${m.cls}">${m.icon} ${esc(el.estado || "—")}</span></div>
        <ul class="elig-list">${(el.razones || []).map((r) => `<li>${esc(r)}</li>`).join("")}</ul>
        ${(el.riesgos && el.riesgos.length) ? `<div class="elig-risk"><h4>Riesgos / condiciones</h4>
          <ul class="elig-list">${el.riesgos.map((r) => `<li>${esc(r)}</li>`).join("")}</ul></div>` : ""}
      </div>
      <div class="card result-block">
        <div class="block-label">2 · Portafolio priorizado de servicios PF</div>
        ${port.length ? port.map((s, i) => `
          <div class="reco-item ${i === 0 ? "top" : ""}">
            <span class="reco-rank">${i + 1}</span>
            <div><div class="reco-name">${esc(s.servicio || "")}</div><div class="reco-why">${esc(s.porque || "")}</div></div>
          </div>`).join("") : '<div class="reco-empty">Sin servicios sugeridos.</div>'}
      </div>
      <div class="card result-block">
        <div class="block-label">3 · Estrategia de afiliación</div>
        ${est.angulo ? `<div class="angle">${esc(est.angulo)}</div>` : ""}
        ${(est.objeciones && est.objeciones.length) ? est.objeciones.map((o) => `
          <div class="obj-item"><div class="obj-q">${esc(o.objecion)}</div>
            <div class="obj-a"><b>Respuesta:</b> ${esc(o.respuesta)}</div></div>`).join("") : ""}
        ${(est.siguientesPasos && est.siguientesPasos.length) ? `<div class="next-steps"><h4>Siguientes pasos</h4>
          <ol>${est.siguientesPasos.map((p) => `<li>${esc(p)}</li>`).join("")}</ol></div>` : ""}
      </div>`;
  }

  // ---------- historial ----------
  let historyCache = [];

  async function loadHistory() {
    historyCache = await api("/api/history");
    populateHistFilters();
    renderHistList();
  }

  function populateHistFilters() {
    const uniq = (arr) => [...new Set(arr.filter(Boolean))].sort();
    fillSelect($("#h-elig"), uniq(historyCache.map((h) => h.elegibilidad)), "Toda elegibilidad");
    fillSelect($("#h-canal"), uniq(historyCache.map((h) => h.canal)), "Todos los canales");
    fillSelect($("#h-rubro"), uniq(historyCache.map((h) => h.rubro)), "Todos los rubros");
  }

  function fillSelect(sel, values, allLabel) {
    const cur = sel.value;
    sel.innerHTML = `<option value="">${esc(allLabel)}</option>` + values.map((v) => `<option>${esc(v)}</option>`).join("");
    if (cur && values.includes(cur)) sel.value = cur;
  }

  function filteredHistory() {
    const q = $("#h-search").value.trim().toLowerCase();
    const el = $("#h-elig").value, ca = $("#h-canal").value, ru = $("#h-rubro").value, or = $("#h-origen").value;
    return historyCache
      .filter((h) => !q || (h.nombreComercio || "").toLowerCase().includes(q))
      .filter((h) => !el || h.elegibilidad === el)
      .filter((h) => !ca || h.canal === ca)
      .filter((h) => !ru || h.rubro === ru)
      .filter((h) => !or || h.origen === or);
  }

  function fmtDate(iso) {
    try {
      return new Date(iso).toLocaleString("es-PA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
    } catch (e) { return iso || ""; }
  }

  function renderHistList() {
    const el = $("#hist-list");
    if (!historyCache.length) {
      el.innerHTML = '<div class="empty-note">Aún no hay leads evaluados. Evalúa uno (manual o por link) y aparecerá aquí.</div>';
      return;
    }
    const list = filteredHistory();
    if (!list.length) { el.innerHTML = '<div class="empty-note">Ningún lead con esos filtros.</div>'; return; }
    el.innerHTML = list.map((h) => `
      <button class="hist-item" data-id="${esc(h.id)}">
        <div class="hi-top"><span class="hi-name">${esc(h.nombreComercio)}</span>
          <span class="origin-badge ${h.origen === "Link" ? "link" : "manual"}">${esc(h.origen)}</span></div>
        <div class="hi-meta">${esc(h.rubro)} · ${esc(h.canal)}</div>
        <div class="hi-foot"><span class="elig-badge sm ${eligMeta(h.elegibilidad).cls}">${esc(h.elegibilidad)}</span>
          <span class="hi-date">${esc(fmtDate(h.fecha))}</span></div>
      </button>`).join("");
    el.querySelectorAll(".hist-item").forEach((b) => b.addEventListener("click", () => openHistory(b.dataset.id)));
  }

  function openHistory(id) {
    const h = historyCache.find((x) => x.id === id);
    if (!h) return;
    document.querySelectorAll(".hist-item").forEach((b) => b.classList.toggle("active", b.dataset.id === id));
    if (h.origen === "Link") renderLinkResults(h.resultado, $("#hist-detail"));
    else renderManualResults(h.resultado, $("#hist-detail"), h.nombreComercio);
  }

  // ---------- base de conocimiento ----------
  const opts = (sel, list) => list.map((v) => `<option ${v === sel ? "selected" : ""}>${esc(v)}</option>`).join("");

  function renderKb() {
    $("#kb-body").innerHTML = rubrosSection() + servicesSection() + anglesSection() + objectionsSection();
    wireKb();
  }

  function sectionShell(sec, title, headCols, rowsHtml) {
    return `
      <div class="kb-section" id="kb-${sec}">
        <div class="kb-head"><h2>${esc(title)}</h2>
          <div class="kb-actions">
            <span class="saved-flash" data-flash></span>
            <button class="btn btn-sm" data-add="${sec}">Agregar</button>
            <button class="btn btn-primary btn-sm" data-save="${sec}">Guardar</button>
          </div>
        </div>
        <div class="table-wrap"><table class="kb">
          <thead><tr>${headCols.map((c) => `<th>${esc(c)}</th>`).join("")}<th></th></tr></thead>
          <tbody>${rowsHtml}</tbody>
        </table></div>
      </div>`;
  }

  const delCell = (sec, i) => `<td><button class="btn btn-ghost kb-del" data-del="${sec}" data-i="${i}" title="Quitar">✕</button></td>`;

  function rubrosSection() {
    const rows = state.kb.rubros.map((r, i) => `
      <tr data-row="${i}">
        <td><input data-f="name" value="${esc(r.name)}"></td>
        <td><label class="switch"><input type="checkbox" data-f="allowed" ${r.allowed ? "checked" : ""}><span class="track"></span></label></td>
        <td><input data-f="nota" value="${esc(r.nota || "")}"></td>
        ${delCell("rubros", i)}
      </tr>`).join("");
    return sectionShell("rubros", "Rubros permitidos / no permitidos", ["Rubro", "Permitido", "Nota"], rows);
  }

  function servicesSection() {
    const rows = state.kb.services.map((s, i) => `
      <tr data-row="${i}">
        <td><input data-f="code" value="${esc(s.code || "")}" style="width:110px"></td>
        <td><input data-f="name" value="${esc(s.name || "")}" style="width:130px"></td>
        <td><textarea data-f="descripcion">${esc(s.descripcion || "")}</textarea></td>
        <td><input data-f="canales" value="${esc((s.canales || []).join(", "))}" placeholder="Todos" style="width:120px"></td>
        <td><input data-f="tamanos" value="${esc((s.tamanos || []).join(", "))}" placeholder="Todos" style="width:120px"></td>
        <td><input data-f="needs" value="${esc((s.needs || []).join(", "))}" style="width:150px"></td>
        <td><input type="number" data-f="peso" value="${s.peso ?? 10}" style="width:60px"></td>
        ${delCell("services", i)}
      </tr>`).join("");
    return sectionShell("services", "Catálogo de servicios PF",
      ["Código", "Nombre", "Descripción", "Canales", "Tamaños", "Necesidades", "Peso"], rows);
  }

  function anglesSection() {
    const rows = state.kb.angles.map((a, i) => `
      <tr data-row="${i}">
        <td><select data-f="canal" style="width:120px">${opts(a.canal, ANGLE_CANALES)}</select></td>
        <td><textarea data-f="angulo">${esc(a.angulo || "")}</textarea></td>
        ${delCell("angles", i)}
      </tr>`).join("");
    return sectionShell("angles", "Ángulos de venta (por canal)", ["Canal", "Ángulo sugerido"], rows);
  }

  function objectionsSection() {
    const rows = state.kb.objections.map((o, i) => `
      <tr data-row="${i}">
        <td><select data-f="trigger" style="width:120px">${opts(o.trigger, TRIGGERS)}</select></td>
        <td><input data-f="objecion" value="${esc(o.objecion || "")}"></td>
        <td><textarea data-f="respuesta">${esc(o.respuesta || "")}</textarea></td>
        ${delCell("objections", i)}
      </tr>`).join("");
    return sectionShell("objections", "Objeciones y respuestas", ["Se activa con", "Objeción", "Respuesta"], rows);
  }

  const LIST_FIELDS = { services: ["canales", "tamanos", "needs"] };

  // lee el contenido actual del DOM de una sección hacia una lista de objetos
  function readSection(sec) {
    const rows = document.querySelectorAll(`#kb-${sec} tbody tr[data-row]`);
    return [...rows].map((tr, i) => {
      const prev = state.kb[sec][i];
      const obj = { id: (prev && prev.id) || `${sec}-${Date.now()}-${i}` };
      tr.querySelectorAll("[data-f]").forEach((inp) => {
        const f = inp.dataset.f;
        if (inp.type === "checkbox") obj[f] = inp.checked;
        else if (f === "peso") obj[f] = Number(inp.value) || 0;
        else if ((LIST_FIELDS[sec] || []).includes(f)) obj[f] = inp.value.split(",").map((x) => x.trim()).filter(Boolean);
        else obj[f] = inp.value.trim();
      });
      return obj;
    });
  }

  function wireKb() {
    document.querySelectorAll("[data-add]").forEach((b) => b.addEventListener("click", () => {
      const sec = b.dataset.add;
      const list = readSection(sec);
      list.push({ ...structuredClone(BLANK[sec]), id: `${sec}-${Date.now()}` });
      state.kb[sec] = list;
      renderKb();
    }));
    document.querySelectorAll("[data-del]").forEach((b) => b.addEventListener("click", () => {
      const sec = b.dataset.del, i = Number(b.dataset.i);
      const list = readSection(sec);
      list.splice(i, 1);
      state.kb[sec] = list;
      renderKb();
    }));
    document.querySelectorAll("[data-save]").forEach((b) => b.addEventListener("click", async () => {
      const sec = b.dataset.save;
      const list = readSection(sec);
      const res = await api(`/api/kb/${sec}`, {
        method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(list),
      });
      state.kb[sec] = res;
      if (sec === "rubros") refreshRubroDatalist();
      const flash = $(`#kb-${sec} [data-flash]`);
      if (flash) { flash.textContent = "Guardado ✓"; setTimeout(() => { flash.textContent = ""; }, 1800); }
    }));
  }

  function refreshRubroDatalist() {
    const dl = $("#rubro-list");
    dl.innerHTML = "";
    state.kb.rubros.forEach((r) => { const o = document.createElement("option"); o.value = r.name; dl.append(o); });
  }

  // ---------- arranque ----------
  async function init() {
    document.querySelectorAll(".topnav-item").forEach((b) => b.addEventListener("click", () => switchView(b.dataset.view)));
    const kb = await api("/api/kb");
    state.kb = { rubros: kb.rubros || [], services: kb.services || [], angles: kb.angles || [], objections: kb.objections || [] };
    refreshRubroDatalist();
    $("#need-chips").innerHTML = NEED_CHIPS.map((n) => `<button type="button" class="need-chip" data-need="${esc(n)}">+ ${esc(n)}</button>`).join("");
    document.querySelectorAll("[data-need]").forEach((b) => b.addEventListener("click", () => {
      const t = $("#f-necesidad");
      t.value = (t.value.trim() ? t.value.trim() + ". " : "") + b.dataset.need;
      t.focus();
    }));
    $("#lead-form").addEventListener("submit", onEvaluate);
    $("#link-form").addEventListener("submit", onAnalyzeLink);
    ["#h-search", "#h-elig", "#h-canal", "#h-rubro", "#h-origen"].forEach((sel) =>
      $(sel).addEventListener("input", renderHistList));

    // Deep-link opcional: #kb / #link / #historial abren esa vista; parámetros
    // prellenan y evalúan el lead manual (útil para compartir o demostrar).
    const hashView = (location.hash || "").replace("#", "");
    if (["kb", "link", "historial"].includes(hashView)) switchView(hashView);
    const q = new URLSearchParams(location.search);
    if ([...q.keys()].length) {
      if (q.get("nombre")) $("#f-nombre").value = q.get("nombre");
      if (q.get("rubro")) $("#f-rubro").value = q.get("rubro");
      if (q.get("tamano")) $("#f-tamano").value = q.get("tamano");
      if (q.get("volumen")) $("#f-volumen").value = q.get("volumen");
      if (q.get("canal")) $("#f-canal").value = q.get("canal");
      if (q.get("necesidad")) $("#f-necesidad").value = q.get("necesidad");
      $("#lead-form").requestSubmit();
    }
  }

  init().catch((err) => {
    console.error(err);
    document.body.insertAdjacentHTML("beforeend",
      `<div style="position:fixed;bottom:12px;left:12px;background:#FDECEC;color:#B42318;padding:12px 16px;border-radius:12px;font-size:13px;max-width:340px">` +
      `No pudimos cargar Brújula PF. Reintenta en un momento.<span style="display:block;color:#7d889b;font-size:11.5px;margin-top:4px">${esc(err.message)}</span></div>`);
  });
})();
