package com.paguelofacil.brujula.domain;

import java.util.List;

/** Resultado de la evaluación de un lead: las tres salidas de Brújula PF. */
public record LeadAnalysis(
        Eligibility eligibility,
        List<ServiceReco> portafolio,
        Strategy estrategia) {

    /** 1) Validación de elegibilidad. */
    public record Eligibility(String estado, List<String> razones, List<String> riesgos) {}

    /** 2) Servicio recomendado (portafolio priorizado). */
    public record ServiceReco(String code, String name, String why, int score) {}

    /** 3) Estrategia comercial: ángulo + objeciones con su respuesta. */
    public record Strategy(String angulo, List<ObjPair> objeciones) {}

    public record ObjPair(String objecion, String respuesta) {}
}
