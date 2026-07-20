package com.paguelofacil.adquirencia.domain;

import java.time.LocalDateTime;

/** Alerta disparada por la evaluación de una regla sobre la ventana actual. */
public record Alert(
        String id,
        String ruleId,
        String ruleName,
        Severity severity,
        String message,
        String merchantId,
        String merchantName,
        Processor processor,
        LocalDateTime triggeredAt,
        boolean acknowledged) {

    public enum Severity { WARNING, SERIOUS, CRITICAL }

    public Alert acknowledge() {
        return new Alert(id, ruleId, ruleName, severity, message, merchantId, merchantName,
                processor, triggeredAt, true);
    }
}
