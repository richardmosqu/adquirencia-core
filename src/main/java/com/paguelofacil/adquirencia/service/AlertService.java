package com.paguelofacil.adquirencia.service;

import com.paguelofacil.adquirencia.data.DataStore;
import com.paguelofacil.adquirencia.domain.Alert;
import com.paguelofacil.adquirencia.domain.AlertRule;
import com.paguelofacil.adquirencia.domain.Merchant;
import com.paguelofacil.adquirencia.domain.Processor;
import com.paguelofacil.adquirencia.domain.Transaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Evalúa las reglas configuradas contra la ventana del día actual (hoy) y
 * expone el CRUD de reglas. Las alertas se recalculan en cada consulta; el
 * "ack" se conserva en memoria por id determinista de alerta.
 */
@Service
public class AlertService {

    private final DataStore store;
    private final Set<String> acknowledged = ConcurrentHashMap.newKeySet();
    private final AtomicInteger ruleSeq = new AtomicInteger(100);

    public AlertService(DataStore store) {
        this.store = store;
    }

    // ---------- reglas ----------

    public List<AlertRule> rules() {
        return store.alertRules();
    }

    public AlertRule createRule(AlertRule rule) {
        rule.setId("R-USR-" + ruleSeq.incrementAndGet());
        if (rule.getScope() == null) {
            rule.setScope(rule.getMerchantId() == null ? AlertRule.Scope.GLOBAL : AlertRule.Scope.MERCHANT);
        }
        store.alertRules().add(rule);
        return rule;
    }

    public Optional<AlertRule> updateRule(String id, AlertRule patch) {
        Optional<AlertRule> found = store.alertRules().stream()
                .filter(r -> r.getId().equals(id)).findFirst();
        found.ifPresent(r -> {
            if (patch.getName() != null) r.setName(patch.getName());
            if (patch.getCondition() != null) r.setCondition(patch.getCondition());
            if (patch.getThreshold() > 0) r.setThreshold(patch.getThreshold());
            if (patch.getDrCode() != null) r.setDrCode(patch.getDrCode());
            r.setEnabled(patch.isEnabled());
        });
        return found;
    }

    public boolean deleteRule(String id) {
        return store.alertRules().removeIf(r -> r.getId().equals(id) && !r.isStandard());
    }

    // ---------- evaluación ----------

    public List<Alert> activeAlerts() {
        List<Alert> alerts = new ArrayList<>();
        LocalDate today = LocalDate.now();

        List<Transaction> todaySales = store.transactions().stream()
                .filter(t -> t.type() == Transaction.TxType.SALE)
                .filter(t -> t.timestamp().toLocalDate().equals(today))
                .toList();

        for (AlertRule rule : store.alertRules()) {
            if (!rule.isEnabled()) continue;
            List<Transaction> scope = rule.getScope() == AlertRule.Scope.MERCHANT
                    ? todaySales.stream().filter(t -> t.merchantId().equals(rule.getMerchantId())).toList()
                    : todaySales;
            evaluate(rule, scope, today).ifPresent(alerts::add);
        }

        alerts.sort(Comparator.comparing((Alert a) -> a.severity().ordinal()).reversed());
        return alerts;
    }

    public boolean acknowledge(String alertId) {
        return acknowledged.add(alertId);
    }

    private Optional<Alert> evaluate(AlertRule rule, List<Transaction> sales, LocalDate today) {
        return switch (rule.getCondition()) {
            case REJECT_RATE_ABOVE -> {
                long declined = sales.stream().filter(t -> !t.approved()).count();
                double pct = pct(declined, sales.size());
                yield sales.size() >= 20 && pct > rule.getThreshold()
                        ? Optional.of(build(rule, severityByExcess(pct, rule.getThreshold()),
                                String.format("Tasa de rechazo de hoy en %.1f%% (umbral %.0f%%, %d de %d trx).",
                                        pct, rule.getThreshold(), declined, sales.size()), null))
                        : Optional.empty();
            }
            case DECLINED_COUNT_ABOVE -> {
                long declined = sales.stream().filter(t -> !t.approved()).count();
                yield declined > rule.getThreshold()
                        ? Optional.of(build(rule, severityByExcess(declined, rule.getThreshold()),
                                String.format("%d transacciones rechazadas hoy (umbral %.0f).",
                                        declined, rule.getThreshold()), null))
                        : Optional.empty();
            }
            case REFUND_RATE_ABOVE -> {
                // Reembolsos de los últimos 7 días sobre volumen aprobado del mismo periodo.
                LocalDate from = today.minusDays(6);
                List<Transaction> window = store.transactions().stream()
                        .filter(t -> rule.getScope() == AlertRule.Scope.GLOBAL
                                || t.merchantId().equals(rule.getMerchantId()))
                        .filter(t -> !t.timestamp().toLocalDate().isBefore(from))
                        .toList();
                BigDecimal vol = window.stream()
                        .filter(t -> t.type() == Transaction.TxType.SALE && t.approved())
                        .map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal ref = window.stream()
                        .filter(t -> t.type() == Transaction.TxType.REFUND)
                        .map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                double pct = vol.signum() == 0 ? 0 : ref.doubleValue() * 100.0 / vol.doubleValue();
                yield pct > rule.getThreshold()
                        ? Optional.of(build(rule, severityByExcess(pct, rule.getThreshold()),
                                String.format("Índice de reembolsos (7 días) en %.1f%% (umbral %.0f%%).",
                                        pct, rule.getThreshold()), null))
                        : Optional.empty();
            }
            case THREE_DS_FAILURE_RATE_ABOVE -> {
                List<Transaction> with3ds = sales.stream().filter(Transaction::threeDs).toList();
                long failures = with3ds.stream()
                        .filter(t -> !t.approved() && "3DS".equals(t.drCode())).count();
                double pct = pct(failures, with3ds.size());
                yield with3ds.size() >= 20 && pct > rule.getThreshold()
                        ? Optional.of(build(rule, severityByExcess(pct, rule.getThreshold()),
                                String.format("Fallas de autenticación 3DS en %.1f%% de %d trx con 3DS (umbral %.0f%%).",
                                        pct, with3ds.size(), rule.getThreshold()), null))
                        : Optional.empty();
            }
            case DR_CODE_RECURRENT -> {
                String code = rule.getDrCode() == null ? "05" : rule.getDrCode();
                long count = sales.stream()
                        .filter(t -> !t.approved() && code.equals(t.drCode())).count();
                yield count > rule.getThreshold()
                        ? Optional.of(build(rule, severityByExcess(count, rule.getThreshold()),
                                String.format("Código DR %s repetido %d veces hoy (umbral %.0f). %s",
                                        code, count, rule.getThreshold(),
                                        com.paguelofacil.adquirencia.data.DataStore.DR_CODES
                                                .getOrDefault(code, "")), null))
                        : Optional.empty();
            }
            case CHANNEL_VOLUME_DROP -> {
                // Compara trx de hoy por canal contra el promedio de los 7 días anteriores.
                Optional<Alert> worst = Optional.empty();
                for (Processor p : Processor.values()) {
                    if (rule.getProcessor() != null && rule.getProcessor() != p) continue;
                    long todayCount = sales.stream().filter(t -> t.processor() == p).count();
                    LocalDate from = today.minusDays(7);
                    double avg = store.transactions().stream()
                            .filter(t -> t.type() == Transaction.TxType.SALE && t.processor() == p)
                            .filter(t -> {
                                LocalDate d = t.timestamp().toLocalDate();
                                return !d.isBefore(from) && d.isBefore(today);
                            })
                            .count() / 7.0;
                    if (avg < 10) continue;
                    double drop = (avg - todayCount) * 100.0 / avg;
                    if (drop > rule.getThreshold()) {
                        Alert a = build(rule, severityByExcess(drop, rule.getThreshold()),
                                String.format("Canal %s con caída de %.0f%% en trx de hoy vs promedio 7 días (%d vs %.0f).",
                                        p.getLabel(), drop, todayCount, avg), p);
                        if (worst.isEmpty()) worst = Optional.of(a);
                    }
                }
                yield worst;
            }
            case INTERNAL_ERRORS_ABOVE -> {
                long cfg = sales.stream()
                        .filter(t -> !t.approved() && "CFG".equals(t.drCode())).count();
                long badCredentials = store.credentials().stream()
                        .filter(c -> "Error de configuración".equals(c.getStatus())).count();
                long total = cfg + badCredentials;
                yield total > rule.getThreshold()
                        ? Optional.of(build(rule, severityByExcess(total, rule.getThreshold()),
                                String.format("%d errores internos hoy (%d rechazos CFG + %d credenciales mal configuradas).",
                                        total, cfg, badCredentials), null))
                        : Optional.empty();
            }
        };
    }

    private Alert build(AlertRule rule, Alert.Severity severity, String message, Processor processor) {
        String merchantName = null;
        if (rule.getMerchantId() != null) {
            merchantName = store.merchants().stream()
                    .filter(m -> m.id().equals(rule.getMerchantId()))
                    .map(Merchant::name).findFirst().orElse(null);
        }
        String id = "A-" + rule.getId() + "-" + LocalDate.now();
        return new Alert(id, rule.getId(), rule.getName(), severity, message,
                rule.getMerchantId(), merchantName, processor,
                LocalDateTime.now().withSecond(0).withNano(0), acknowledged.contains(id));
    }

    private static double pct(long part, long total) {
        return total == 0 ? 0 : part * 100.0 / total;
    }

    /** Escala la severidad según cuánto se excede el umbral. */
    private static Alert.Severity severityByExcess(double value, double threshold) {
        if (threshold <= 0) return Alert.Severity.WARNING;
        double ratio = value / threshold;
        if (ratio >= 2.0) return Alert.Severity.CRITICAL;
        if (ratio >= 1.4) return Alert.Severity.SERIOUS;
        return Alert.Severity.WARNING;
    }
}
