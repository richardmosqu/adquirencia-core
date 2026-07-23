package com.paguelofacil.brujula.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.paguelofacil.brujula.domain.LeadAnalysis;
import com.paguelofacil.brujula.domain.LeadHistoryEntry;
import com.paguelofacil.brujula.domain.LeadInput;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Historial en memoria de los leads evaluados. Se pierde al reiniciar la app
 * (suficiente para la demo). Los más recientes quedan primero.
 */
@Component
public class LeadHistory {

    private final List<LeadHistoryEntry> entries = new CopyOnWriteArrayList<>();
    private final AtomicInteger seq = new AtomicInteger(0);

    public List<LeadHistoryEntry> all() {
        return entries;
    }

    public LeadHistoryEntry addManual(LeadInput in, LeadAnalysis a) {
        String elig = a.eligibility() != null ? a.eligibility().estado() : "";
        return add(in.nombreComercio(), in.rubro(), in.canal(), elig, "Manual", a);
    }

    public LeadHistoryEntry addLink(JsonNode a) {
        return add(txt(a, "nombreComercio"), txt(a, "rubro"), txt(a, "canal"),
                a.path("elegibilidad").path("estado").asText(""), "Link", a);
    }

    private LeadHistoryEntry add(String nombre, String rubro, String canal,
                                 String elig, String origen, Object resultado) {
        LeadHistoryEntry e = new LeadHistoryEntry(
                "H-" + seq.incrementAndGet(),
                blankOr(nombre, "Comercio sin nombre"),
                blankOr(rubro, "—"),
                blankOr(canal, "—"),
                blankOr(elig, "—"),
                LocalDateTime.now().withNano(0),
                origen,
                resultado);
        entries.add(0, e);
        return e;
    }

    private static String txt(JsonNode n, String field) {
        return n == null ? "" : n.path(field).asText("");
    }

    private static String blankOr(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }
}
