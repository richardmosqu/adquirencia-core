package com.paguelofacil.brujula.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.paguelofacil.brujula.data.LeadHistory;
import com.paguelofacil.brujula.eval.AnalysisException;
import com.paguelofacil.brujula.eval.LinkAnalysisService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Analiza el sitio web de un comercio con Gemini y guarda el lead en el historial. */
@RestController
@RequestMapping("/api")
public class LinkController {

    private final LinkAnalysisService service;
    private final LeadHistory history;

    public LinkController(LinkAnalysisService service, LeadHistory history) {
        this.service = service;
        this.history = history;
    }

    /** Respuesta uniforme: ok + (análisis | mensaje de error amigable). */
    public record LinkResponse(boolean ok, String error, JsonNode analysis) {}

    @PostMapping("/analyze-link")
    public LinkResponse analyze(@RequestBody Map<String, String> body) {
        try {
            JsonNode analysis = service.analyze(body.get("url"));
            history.addLink(analysis);
            return new LinkResponse(true, null, analysis);
        } catch (AnalysisException e) {
            return new LinkResponse(false, e.getMessage(), null);
        } catch (Exception e) {
            return new LinkResponse(false, "No pudimos completar el análisis. Inténtalo de nuevo.", null);
        }
    }
}
