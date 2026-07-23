package com.paguelofacil.brujula.domain;

/** Datos del comercio prospecto que ingresa el asesor comercial (lead manual). */
public record LeadInput(
        String nombreComercio,
        String rubro,
        String tamano,             // Pequeño | Mediano | Grande
        double volumenEstimado,    // ventas estimadas mensuales (USD)
        String canal,              // Físico | En línea | Mixto
        String necesidad) {        // texto libre o una opción
}
