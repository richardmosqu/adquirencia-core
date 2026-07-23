package com.paguelofacil.brujula.eval;

import com.paguelofacil.brujula.domain.LeadAnalysis;
import com.paguelofacil.brujula.domain.LeadInput;

/**
 * PLACEHOLDER — implementación futura con IA (LLM).
 *
 * <p>Este es el "seam" para enchufar IA real más adelante sin reescribir la
 * app: implementa la misma interfaz {@link LeadEvaluator}. Cuando exista, se
 * anotaría como {@code @Service} y {@code @Primary} (o se seleccionaría por
 * configuración) para reemplazar a {@link RulesLeadEvaluator}, consumiendo la
 * misma base de conocimiento como contexto/prompt.</p>
 *
 * <p>Hoy NO es un bean de Spring y no se usa; queda documentado el contrato.</p>
 */
public class AiLeadEvaluator implements LeadEvaluator {

    @Override
    public LeadAnalysis evaluate(LeadInput input) {
        // TODO: integrar un modelo (p. ej. Claude) usando la KnowledgeBase como
        // contexto para generar elegibilidad, portafolio y estrategia.
        throw new UnsupportedOperationException("AiLeadEvaluator aún no implementado");
    }
}
