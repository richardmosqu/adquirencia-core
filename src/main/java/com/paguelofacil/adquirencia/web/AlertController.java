package com.paguelofacil.adquirencia.web;

import com.paguelofacil.adquirencia.domain.ActionPlan;
import com.paguelofacil.adquirencia.domain.Alert;
import com.paguelofacil.adquirencia.domain.AlertRule;
import com.paguelofacil.adquirencia.service.AlertService;
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
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/alerts")
    public List<Alert> activeAlerts() {
        return alertService.activeAlerts();
    }

    @GetMapping("/alerts/{id}")
    public ResponseEntity<AlertService.AlertDetail> detail(@PathVariable String id) {
        return alertService.detail(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/alerts/{id}/ack")
    public Map<String, Object> acknowledge(@PathVariable String id) {
        return Map.of("acknowledged", alertService.acknowledge(id));
    }

    @GetMapping("/action-plans")
    public List<ActionPlan> actionPlans() {
        return alertService.actionPlans();
    }

    @PostMapping("/action-plans")
    public ActionPlan createActionPlan(@RequestBody ActionPlan plan) {
        return alertService.createActionPlan(plan);
    }

    @DeleteMapping("/action-plans/{id}")
    public ResponseEntity<Void> deleteActionPlan(@PathVariable String id) {
        return alertService.deleteActionPlan(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(409).build();
    }

    @GetMapping("/alert-rules")
    public List<AlertRule> rules() {
        return alertService.rules();
    }

    @PostMapping("/alert-rules")
    public AlertRule create(@RequestBody AlertRule rule) {
        return alertService.createRule(rule);
    }

    @PutMapping("/alert-rules/{id}")
    public ResponseEntity<AlertRule> update(@PathVariable String id, @RequestBody AlertRule patch) {
        return alertService.updateRule(id, patch)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/alert-rules/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return alertService.deleteRule(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
