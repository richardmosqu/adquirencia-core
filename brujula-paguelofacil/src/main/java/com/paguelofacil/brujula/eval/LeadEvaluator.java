package com.paguelofacil.brujula.eval;

import com.paguelofacil.brujula.domain.LeadAnalysis;
import com.paguelofacil.brujula.domain.LeadInput;

/**
 * Contrato de evaluación de un lead. Hoy lo implementa {@link RulesLeadEvaluator}
 * (motor de reglas determinista). En el futuro se puede enchufar una
 * implementación con IA (ver {@code AiLeadEvaluator}) SIN reescribir la app:
 * basta con exponer otra implementación de esta interfaz.
 */
public interface LeadEvaluator {

    LeadAnalysis evaluate(LeadInput input);
}
