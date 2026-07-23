package com.paguelofacil.brujula.domain;

/** Datos del comercio prospecto que ingresa el asesor comercial. */
public record LeadInput(
        String rubro,
        String tamano,             // Pequeño | Mediano | Grande
        double volumenEstimado,    // ventas estimadas mensuales (USD)
        String canal,              // Físico | En línea | Mixto
        String necesidad) {        // texto libre o una opción
}
