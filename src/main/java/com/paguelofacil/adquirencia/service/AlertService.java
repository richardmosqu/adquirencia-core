package com.paguelofacil.adquirencia.service;

import com.paguelofacil.adquirencia.data.DataStore;
import com.paguelofacil.adquirencia.domain.ActionPlan;
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
    private final AtomicInteger planSeq = new AtomicInteger(100);

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
            if (patch.getActionPlanId() != null) r.setActionPlanId(patch.getActionPlanId());
            r.setEnabled(patch.isEnabled());
        });
        return found;
    }

    // ---------- planes de acción ----------

    public List<ActionPlan> actionPlans() {
        return store.actionPlans();
    }

    public ActionPlan createActionPlan(ActionPlan plan) {
        plan.setId("AP-USR-" + planSeq.incrementAndGet());
        store.actionPlans().add(plan);
        return plan;
    }

    public boolean deleteActionPlan(String id) {
        // no se borra un plan que siga enlazado a alguna regla
        boolean inUse = store.alertRules().stream().anyMatch(r -> id.equals(r.getActionPlanId()));
        return !inUse && store.actionPlans().removeIf(p -> p.getId().equals(id));
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

    // ---------- detalle extendido de una alerta ----------

    /** Operación representativa detrás de la alerta (para el detalle). */
    public record Metric(String label, double value, double threshold, String unit) {}

    public record AlertDetail(
            Alert alert,
            String conditionLabel,
            String scopeLabel,
            String operationCode,
            String cardBrand,
            String cardMask,
            BigDecimal sampleAmount,
            LocalDateTime sampleTime,
            BigDecimal processedVolume,
            long txEvaluated,
            long declinedCount,
            Metric metric,
            String magnitudeText,
            String drCode,
            String drDescription,
            ActionPlan actionPlan) {}

    public Optional<AlertDetail> detail(String alertId) {
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
            Optional<Alert> a = evaluate(rule, scope, today);
            if (a.isPresent() && a.get().id().equals(alertId)) {
                return Optional.of(buildDetail(rule, a.get(), scope, today));
            }
        }
        return Optional.empty();
    }

    private AlertDetail buildDetail(AlertRule rule, Alert alert, List<Transaction> scope, LocalDate today) {
        long declined = scope.stream().filter(t -> !t.approved()).count();
        BigDecimal processedVolume = scope.stream().filter(Transaction::approved)
                .map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Transaction sample = pickSample(rule, scope, alert.processor());
        String opCode = sample != null ? String.format("OP-%06d", sample.id()) : "—";
        long cardSeed = sample != null ? sample.id() : Math.abs(alert.id().hashCode());
        String cardBrand = (cardSeed % 2 == 0) ? "Visa" : "Mastercard";
        String cardMask = "•••• •••• •••• " + String.format("%04d", cardSeed % 10000);
        BigDecimal sampleAmount = sample != null ? sample.amount() : null;
        LocalDateTime sampleTime = sample != null ? sample.timestamp() : alert.triggeredAt();

        Metric metric = metric(rule, scope, today, alert.processor());
        String unit = metric.unit();
        String magnitude = metric.threshold() > 0
                ? String.format("%s%s frente a un umbral de %s%s (%.1f×)",
                        fmt(metric.value()), unit, fmt(metric.threshold()), unit,
                        metric.value() / metric.threshold())
                : String.format("%s%s", fmt(metric.value()), unit);

        String drCode = rule.getCondition() == AlertRule.Condition.DR_CODE_RECURRENT ? rule.getDrCode()
                : (sample != null ? sample.drCode() : null);
        String drDesc = drCode != null ? DataStore.DR_CODES.get(drCode) : null;

        String scopeLabel = alert.merchantName() != null ? alert.merchantName()
                : (alert.processor() != null ? "Canal " + alert.processor().getLabel() : "Todos los comercios");

        ActionPlan plan = rule.getActionPlanId() == null ? null
                : store.actionPlans().stream().filter(p -> p.getId().equals(rule.getActionPlanId()))
                        .findFirst().orElse(null);

        return new AlertDetail(alert, rule.getCondition().getLabel(), scopeLabel,
                opCode, cardBrand, cardMask, sampleAmount, sampleTime,
                processedVolume, scope.size(), declined, metric, magnitude, drCode, drDesc, plan);
    }

    /** Elige la transacción representativa detrás de la alerta. */
    private Transaction pickSample(AlertRule rule, List<Transaction> scope, Processor processor) {
        List<Transaction> candidates = switch (rule.getCondition()) {
            case DR_CODE_RECURRENT -> scope.stream()
                    .filter(t -> !t.approved() && rule.getDrCode() != null && rule.getDrCode().equals(t.drCode())).toList();
            case THREE_DS_FAILURE_RATE_ABOVE -> scope.stream()
                    .filter(t -> !t.approved() && "3DS".equals(t.drCode())).toList();
            case INTERNAL_ERRORS_ABOVE -> scope.stream()
                    .filter(t -> !t.approved() && "CFG".equals(t.drCode())).toList();
            case CHANNEL_VOLUME_DROP -> scope.stream()
                    .filter(t -> processor == null || t.processor() == processor).toList();
            default -> scope.stream().filter(t -> !t.approved()).toList();
        };
        if (candidates.isEmpty()) candidates = scope;
        return candidates.stream().max(Comparator.comparing(Transaction::timestamp)).orElse(null);
    }

    /** Métrica principal detrás de la condición (para "Magnitud del problema"). */
    private Metric metric(AlertRule rule, List<Transaction> scope, LocalDate today, Processor processor) {
        double threshold = rule.getThreshold();
        return switch (rule.getCondition()) {
            case REJECT_RATE_ABOVE -> new Metric("Tasa de rechazo",
                    pct(scope.stream().filter(t -> !t.approved()).count(), scope.size()), threshold, "%");
            case DECLINED_COUNT_ABOVE -> new Metric("Rechazos del día",
                    scope.stream().filter(t -> !t.approved()).count(), threshold, "");
            case REFUND_RATE_ABOVE -> {
                LocalDate from = today.minusDays(6);
                List<Transaction> window = store.transactions().stream()
                        .filter(t -> rule.getScope() == AlertRule.Scope.GLOBAL
                                || t.merchantId().equals(rule.getMerchantId()))
                        .filter(t -> !t.timestamp().toLocalDate().isBefore(from)).toList();
                BigDecimal vol = window.stream().filter(t -> t.type() == Transaction.TxType.SALE && t.approved())
                        .map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal ref = window.stream().filter(t -> t.type() == Transaction.TxType.REFUND)
                        .map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                double p = vol.signum() == 0 ? 0 : ref.doubleValue() * 100.0 / vol.doubleValue();
                yield new Metric("Índice de reembolsos (7 días)", p, threshold, "%");
            }
            case THREE_DS_FAILURE_RATE_ABOVE -> {
                List<Transaction> with3ds = scope.stream().filter(Transaction::threeDs).toList();
                long fails = with3ds.stream().filter(t -> !t.approved() && "3DS".equals(t.drCode())).count();
                yield new Metric("Fallas de autenticación 3DS", pct(fails, with3ds.size()), threshold, "%");
            }
            case DR_CODE_RECURRENT -> {
                String code = rule.getDrCode() == null ? "05" : rule.getDrCode();
                long count = scope.stream().filter(t -> !t.approved() && code.equals(t.drCode())).count();
                yield new Metric("Ocurrencias del código " + code, count, threshold, "");
            }
            case CHANNEL_VOLUME_DROP -> {
                long todayCount = scope.stream().filter(t -> processor == null || t.processor() == processor).count();
                LocalDate from = today.minusDays(7);
                double avg = store.transactions().stream()
                        .filter(t -> t.type() == Transaction.TxType.SALE && (processor == null || t.processor() == processor))
                        .filter(t -> {
                            LocalDate d = t.timestamp().toLocalDate();
                            return !d.isBefore(from) && d.isBefore(today);
                        }).count() / 7.0;
                double drop = avg == 0 ? 0 : (avg - todayCount) * 100.0 / avg;
                yield new Metric("Caída de volumen del canal", drop, threshold, "%");
            }
            case INTERNAL_ERRORS_ABOVE -> {
                long cfg = scope.stream().filter(t -> !t.approved() && "CFG".equals(t.drCode())).count();
                long bad = store.credentials().stream().filter(c -> "Error de configuración".equals(c.getStatus())).count();
                yield new Metric("Errores internos", cfg + bad, threshold, "");
            }
        };
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.format("%.0f", v) : String.format("%.1f", v);
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
        String planName = planName(rule.getActionPlanId());
        String id = "A-" + rule.getId() + "-" + LocalDate.now();
        return new Alert(id, rule.getId(), rule.getName(), severity, message,
                rule.getMerchantId(), merchantName, processor,
                rule.getActionPlanId(), planName,
                LocalDateTime.now().withSecond(0).withNano(0), acknowledged.contains(id));
    }

    private String planName(String planId) {
        if (planId == null) return null;
        return store.actionPlans().stream()
                .filter(p -> p.getId().equals(planId))
                .map(ActionPlan::getName).findFirst().orElse(null);
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
