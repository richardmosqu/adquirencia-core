package com.paguelofacil.brujula.domain;

import java.time.LocalDateTime;

/**
 * Entrada del historial de leads evaluados (manual o por link).
 * {@code resultado} guarda el análisis completo para poder reabrirlo:
 * es un {@link LeadAnalysis} (manual) o el JSON del análisis por link.
 */
public record LeadHistoryEntry(
        String id,
        String nombreComercio,
        String rubro,
        String canal,
        String elegibilidad,
        LocalDateTime fecha,
        String origen,        // Manual | Link
        Object resultado) {
}
