package com.paguelofacil.adquirencia.domain;

import java.time.LocalDate;

/** Iniciativa en curso del área de adquirencia. */
public record Project(
        String id,
        String name,
        String description,
        String status,      // En curso | En pausa | Completado | Por iniciar
        String owner,
        LocalDate updatedAt) {
}
