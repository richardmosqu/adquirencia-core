package com.paguelofacil.brujula.web;

import com.paguelofacil.brujula.domain.LeadAnalysis;
import com.paguelofacil.brujula.domain.LeadInput;
import com.paguelofacil.brujula.eval.LeadEvaluator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Evalúa un lead y devuelve las tres salidas de Brújula PF. */
@RestController
@RequestMapping("/api")
public class LeadController {

    private final LeadEvaluator evaluator;

    // Se inyecta por interfaz: hoy RulesLeadEvaluator, mañana podría ser IA.
    public LeadController(LeadEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @PostMapping("/evaluate")
    public LeadAnalysis evaluate(@RequestBody LeadInput input) {
        return evaluator.evaluate(input);
    }
}
