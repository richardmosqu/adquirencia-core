package com.paguelofacil.adquirencia.domain;

import java.time.LocalDate;

/** Entrada de la bitácora de seguimiento de un proyecto. */
public record ProjectLog(LocalDate date, String author, String note) {
}
