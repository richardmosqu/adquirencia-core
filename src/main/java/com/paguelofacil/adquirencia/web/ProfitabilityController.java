package com.paguelofacil.adquirencia.web;

import com.paguelofacil.adquirencia.domain.Acquirer;
import com.paguelofacil.adquirencia.service.ProfitabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adquirentes (CRUD) y calculadora de rentabilidad por adquirente. */
@RestController
@RequestMapping("/api")
public class ProfitabilityController {

    private final ProfitabilityService service;

    public ProfitabilityController(ProfitabilityService service) {
        this.service = service;
    }

    @GetMapping("/acquirers")
    public List<Acquirer> acquirers() {
        return service.acquirers();
    }

    @PostMapping("/acquirers")
    public Acquirer create(@RequestBody Acquirer acquirer) {
        return service.createAcquirer(acquirer);
    }

    @PutMapping("/acquirers/{id}")
    public ResponseEntity<Acquirer> update(@PathVariable String id, @RequestBody Acquirer patch) {
        return service.updateAcquirer(id, patch)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/acquirers/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return service.deleteAcquirer(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/profitability/calculate")
    public ProfitabilityService.CalcResponse calculate(@RequestBody ProfitabilityService.CalcRequest request) {
        return service.calculate(request);
    }
}
