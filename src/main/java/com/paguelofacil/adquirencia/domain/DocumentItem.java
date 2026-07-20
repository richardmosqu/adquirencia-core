package com.paguelofacil.adquirencia.domain;

import java.time.LocalDate;

/** Documento operativo del área: guías, códigos de rechazo, procedimientos. */
public record DocumentItem(
        String id,
        String title,
        String category,    // Códigos de rechazo | Guías por procesador | Procedimientos | Certificaciones
        String description,
        LocalDate updatedAt) {
}
