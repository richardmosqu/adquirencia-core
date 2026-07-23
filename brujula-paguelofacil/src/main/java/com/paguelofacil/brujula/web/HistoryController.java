package com.paguelofacil.brujula.web;

import com.paguelofacil.brujula.data.LeadHistory;
import com.paguelofacil.brujula.domain.LeadHistoryEntry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Historial en memoria de leads evaluados (manual y por link). */
@RestController
@RequestMapping("/api")
public class HistoryController {

    private final LeadHistory history;

    public HistoryController(LeadHistory history) {
        this.history = history;
    }

    @GetMapping("/history")
    public List<LeadHistoryEntry> history() {
        return history.all();
    }
}
