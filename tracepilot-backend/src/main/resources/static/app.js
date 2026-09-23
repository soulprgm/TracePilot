const state = {
    page: 0,
    size: 15,
    totalPages: 0,
    hours: "24",
    slowThresholdMs: 1000,
    filters: {q: "", serviceName: "", status: "", minDurationMs: ""},
    jaegerUrl: "http://localhost:16686"
};

const elements = {
    refresh: document.querySelector("#refresh-button"),
    window: document.querySelector("#time-window"),
    serviceRows: document.querySelector("#service-rows"),
    traceRows: document.querySelector("#trace-rows"),
    filterForm: document.querySelector("#trace-filters"),
    filterQuery: document.querySelector("#filter-query"),
    filterService: document.querySelector("#filter-service"),
    filterStatus: document.querySelector("#filter-status"),
    filterDuration: document.querySelector("#filter-duration"),
    clearFilters: document.querySelector("#clear-filters"),
    previous: document.querySelector("#page-previous"),
    next: document.querySelector("#page-next"),
    pageLabel: document.querySelector("#page-label"),
    drawer: document.querySelector("#trace-drawer"),
    drawerClose: document.querySelector("#drawer-close"),
    drawerBackdrop: document.querySelector("#drawer-backdrop"),
    traceDetail: document.querySelector("#trace-detail"),
    drawerTitle: document.querySelector("#drawer-title"),
    toast: document.querySelector("#toast")
};

async function fetchJson(url) {
    const response = await fetch(url, {headers: {Accept: "application/json"}});
    if (!response.ok) {
        let message = `${response.status} ${response.statusText}`;
        try {
            const body = await response.json();
            message = body.message || message;
        } catch (_) { /* response is not JSON */ }
        throw new Error(message);
    }
    return response.json();
}

function params(values) {
    const result = new URLSearchParams();
    Object.entries(values).forEach(([key, value]) => {
        if (value !== "" && value !== null && value !== undefined) result.set(key, value);
    });
    return result.toString();
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function formatNumber(value, digits = 0) {
    return new Intl.NumberFormat(undefined, {maximumFractionDigits: digits}).format(Number(value || 0));
}

function formatDuration(value) {
    const ms = Number(value || 0);
    if (ms >= 1000) return `${formatNumber(ms / 1000, 2)} s`;
    if (ms >= 1) return `${formatNumber(ms, 1)} ms`;
    return `${formatNumber(ms * 1000, 1)} μs`;
}

function formatTime(value) {
    if (!value) return "—";
    const date = new Date(value.endsWith?.("Z") ? value : `${value}Z`);
    if (Number.isNaN(date.getTime())) return escapeHtml(value);
    return new Intl.DateTimeFormat(undefined, {
        month: "short", day: "numeric", hour: "2-digit", minute: "2-digit", second: "2-digit"
    }).format(date);
}

function showToast(message, isError = false) {
    elements.toast.textContent = message;
    elements.toast.classList.toggle("error", isError);
    elements.toast.classList.add("visible");
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => elements.toast.classList.remove("visible"), 3500);
}

async function loadHealth() {
    const dot = document.querySelector("#health-dot");
    const label = document.querySelector("#health-label");
    const detail = document.querySelector("#health-detail");
    try {
        const health = await fetchJson("/actuator/health");
        const online = health.status === "UP";
        dot.className = `status-dot ${online ? "online" : "offline"}`;
        label.textContent = online ? "API operational" : "API degraded";
        detail.textContent = online ? "Telemetry ready" : health.status;
    } catch (_) {
        dot.className = "status-dot offline";
        label.textContent = "API unavailable";
        detail.textContent = "Check backend logs";
    }
}

async function loadConfig() {
    try {
        const config = await fetchJson("/api/config");
        state.jaegerUrl = config.jaegerUrl || state.jaegerUrl;
        document.querySelector("#jaeger-link").href = state.jaegerUrl;
    } catch (_) { /* defaults remain usable */ }
}

async function loadSummary() {
    const query = params({slowThresholdMs: state.slowThresholdMs, hours: state.hours});
    const summary = await fetchJson(`/api/analytics/summary?${query}`);
    document.querySelector("#metric-total").textContent = formatNumber(summary.totalTraces);
    document.querySelector("#metric-error-rate").textContent = `${formatNumber(summary.errorRate, 1)}%`;
    document.querySelector("#metric-failed").textContent = formatNumber(summary.failedTraces);
    document.querySelector("#metric-latency").textContent = formatDuration(summary.averageLatencyMs);
    document.querySelector("#metric-slow").textContent = formatNumber(summary.slowTraces);
    document.querySelector("#slow-threshold-label").textContent = `${formatNumber(state.slowThresholdMs)} ms`;
}

async function loadServices() {
    const query = params({hours: state.hours});
    const services = await fetchJson(`/api/analytics/services${query ? `?${query}` : ""}`);
    if (!services.length) {
        elements.serviceRows.innerHTML = '<tr class="empty-row"><td colspan="8">No service data in this time window.</td></tr>';
        return;
    }
    elements.serviceRows.innerHTML = services.map((service) => {
        const latencyWidth = Math.min(100, Math.max(2, (service.p95LatencyMs / 2000) * 100));
        const initial = escapeHtml(service.serviceName.slice(0, 1).toUpperCase());
        return `<tr>
            <td><span class="service-name"><i class="service-glyph">${initial}</i>${escapeHtml(service.serviceName)}</span></td>
            <td class="number">${formatNumber(service.requestCount)}</td>
            <td class="number ${service.failedCount ? "error-number" : ""}">${formatNumber(service.failedCount)}</td>
            <td class="number">${formatNumber(service.errorRate, 1)}%</td>
            <td class="number">${formatDuration(service.averageLatencyMs)}</td>
            <td class="number">${formatDuration(service.p95LatencyMs)}</td>
            <td class="number">${formatDuration(service.p99LatencyMs)}</td>
            <td><div class="latency-track" title="P95 ${formatDuration(service.p95LatencyMs)}"><i style="width:${latencyWidth}%"></i></div></td>
        </tr>`;
    }).join("");
}

async function loadServiceOptions() {
    const services = await fetchJson("/api/traces/services");
    const selected = elements.filterService.value;
    elements.filterService.innerHTML = '<option value="">All services</option>' + services
        .map(service => `<option value="${escapeHtml(service)}">${escapeHtml(service)}</option>`)
        .join("");
    elements.filterService.value = selected;
}

async function loadTraces() {
    elements.traceRows.innerHTML = '<tr class="loading-row"><td colspan="7"><span class="loader"></span> Loading traces</td></tr>';
    const query = params({
        page: state.page,
        size: state.size,
        hours: state.hours,
        ...state.filters
    });
    const page = await fetchJson(`/api/traces?${query}`);
    state.totalPages = page.totalPages;
    document.querySelector("#trace-count").textContent = `${formatNumber(page.totalElements)} records`;
    elements.pageLabel.textContent = `Page ${page.totalPages ? page.number + 1 : 0} of ${page.totalPages}`;
    elements.previous.disabled = page.first || page.totalPages === 0;
    elements.next.disabled = page.last || page.totalPages === 0;

    if (!page.content.length) {
        elements.traceRows.innerHTML = '<tr class="empty-row"><td colspan="7">No traces match these filters.</td></tr>';
        return;
    }
    elements.traceRows.innerHTML = page.content.map(trace => {
        const status = String(trace.status || "UNKNOWN").toUpperCase();
        const statusClass = status === "FAILED" ? "failed" : "success";
        return `<tr>
            <td><span class="status-pill ${statusClass}">${escapeHtml(status)}</span></td>
            <td><span class="trace-id" title="${escapeHtml(trace.traceId)}">${escapeHtml(shortId(trace.traceId))}</span></td>
            <td>${escapeHtml(trace.serviceName)}</td>
            <td><div class="operation" title="${escapeHtml(trace.operationName)}">${escapeHtml(trace.operationName)}</div></td>
            <td class="number">${formatDuration(trace.durationMs)}</td>
            <td>${formatTime(trace.createdAt)}</td>
            <td><button class="view-trace" type="button" data-trace-id="${escapeHtml(trace.traceId)}">Inspect →</button></td>
        </tr>`;
    }).join("");
}

function shortId(id) {
    const value = String(id || "");
    return value.length > 15 ? `${value.slice(0, 8)}…${value.slice(-6)}` : value;
}

async function refreshAll() {
    elements.refresh.classList.add("refreshing");
    elements.refresh.disabled = true;
    const results = await Promise.allSettled([
        loadHealth(), loadConfig(), loadSummary(), loadServices(), loadServiceOptions(), loadTraces()
    ]);
    const failed = results.filter(result => result.status === "rejected");
    document.querySelector("#last-updated").textContent = `Updated ${new Intl.DateTimeFormat(undefined, {hour: "2-digit", minute: "2-digit", second: "2-digit"}).format(new Date())}`;
    elements.refresh.classList.remove("refreshing");
    elements.refresh.disabled = false;
    if (failed.length) showToast(failed[0].reason?.message || "Some dashboard data could not be loaded", true);
}

async function openTrace(traceId) {
    elements.drawer.classList.add("open");
    elements.drawer.setAttribute("aria-hidden", "false");
    document.body.style.overflow = "hidden";
    elements.drawerTitle.textContent = shortId(traceId);
    elements.traceDetail.innerHTML = '<span class="loader"></span> Loading timeline';
    try {
        const trace = await fetchJson(`/api/traces/trace/${encodeURIComponent(traceId)}`);
        renderTraceDetail(trace);
    } catch (error) {
        elements.traceDetail.innerHTML = `<p class="muted">${escapeHtml(error.message)}</p>`;
    }
}

function renderTraceDetail(trace) {
    const start = new Date(trace.startTime).getTime();
    const total = Math.max(0.001, Number(trace.durationMs));
    const serviceLabel = trace.services.map(escapeHtml).join(" · ");
    const spanRows = trace.spans.map(span => {
        const spanStart = new Date(span.startTime).getTime();
        const offset = Math.max(0, ((spanStart - start) / total) * 100);
        const width = Math.max(.6, Math.min(100 - offset, (Number(span.durationMs) / total) * 100));
        const failed = String(span.statusCode).toUpperCase() === "ERROR" || Number(span.httpStatusCode || 0) >= 500;
        return `<div class="span-row">
            <div class="span-meta"><strong title="${escapeHtml(span.operationName)}">${escapeHtml(span.operationName)}</strong><span>${escapeHtml(span.serviceName)} · ${escapeHtml(span.spanKind)}</span></div>
            <div class="span-lane"><i class="span-bar ${failed ? "failed" : ""}" style="left:${offset}%;width:${width}%"></i></div>
            <div class="span-duration">${formatDuration(span.durationMs)}</div>
        </div>`;
    }).join("");
    const attributes = trace.spans.map(span => `<details><summary>${escapeHtml(span.serviceName)} · ${escapeHtml(span.operationName)}</summary><pre>${escapeHtml(JSON.stringify(span.attributes || {}, null, 2))}</pre></details>`).join("");

    elements.drawerTitle.textContent = shortId(trace.traceId);
    elements.traceDetail.innerHTML = `
        <div class="detail-summary">
            <div class="detail-stat"><span>Status</span><strong class="${trace.status === "FAILED" ? "error-number" : ""}">${escapeHtml(trace.status)}</strong></div>
            <div class="detail-stat"><span>Duration</span><strong>${formatDuration(trace.durationMs)}</strong></div>
            <div class="detail-stat"><span>Spans</span><strong>${formatNumber(trace.spanCount)}</strong></div>
            <div class="detail-stat"><span>Services</span><strong>${formatNumber(trace.services.length)}</strong></div>
        </div>
        <div class="timeline-title"><h3>Waterfall</h3><span>${escapeHtml(serviceLabel)}</span></div>
        <div class="timeline">${spanRows}</div>
        <div class="attributes"><div class="timeline-title"><h3>Span attributes</h3><a class="external-link" target="_blank" rel="noreferrer" href="${escapeHtml(state.jaegerUrl.replace(/\/$/, ""))}/trace/${encodeURIComponent(trace.traceId)}">Open in Jaeger ↗</a></div>${attributes}</div>`;
}

function closeDrawer() {
    elements.drawer.classList.remove("open");
    elements.drawer.setAttribute("aria-hidden", "true");
    document.body.style.overflow = "";
}

elements.refresh.addEventListener("click", refreshAll);
elements.window.addEventListener("change", () => {
    state.hours = elements.window.value;
    state.page = 0;
    refreshAll();
});
elements.filterForm.addEventListener("submit", (event) => {
    event.preventDefault();
    state.filters = {
        q: elements.filterQuery.value.trim(),
        serviceName: elements.filterService.value,
        status: elements.filterStatus.value,
        minDurationMs: elements.filterDuration.value
    };
    state.page = 0;
    loadTraces().catch(error => showToast(error.message, true));
});
elements.clearFilters.addEventListener("click", () => {
    elements.filterForm.reset();
    state.filters = {q: "", serviceName: "", status: "", minDurationMs: ""};
    state.page = 0;
    loadTraces().catch(error => showToast(error.message, true));
});
elements.previous.addEventListener("click", () => {
    if (state.page > 0) { state.page -= 1; loadTraces().catch(error => showToast(error.message, true)); }
});
elements.next.addEventListener("click", () => {
    if (state.page + 1 < state.totalPages) { state.page += 1; loadTraces().catch(error => showToast(error.message, true)); }
});
elements.traceRows.addEventListener("click", (event) => {
    const button = event.target.closest("[data-trace-id]");
    if (button) openTrace(button.dataset.traceId);
});
elements.drawerClose.addEventListener("click", closeDrawer);
elements.drawerBackdrop.addEventListener("click", closeDrawer);
document.addEventListener("keydown", event => { if (event.key === "Escape") closeDrawer(); });

document.querySelectorAll(".nav-link").forEach(link => link.addEventListener("click", () => {
    document.querySelectorAll(".nav-link").forEach(item => item.classList.remove("active"));
    link.classList.add("active");
}));

refreshAll();
window.setInterval(refreshAll, 30_000);
