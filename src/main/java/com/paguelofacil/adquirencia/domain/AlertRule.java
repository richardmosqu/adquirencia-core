package com.paguelofacil.adquirencia.domain;

/**
 * Regla configurable de alerta. Las reglas estándar ({@code standard=true})
 * son plantillas que aplican por defecto; las reglas por comercio llevan
 * {@code merchantId} y alcance MERCHANT.
 */
public class AlertRule {

    public enum Scope { GLOBAL, MERCHANT }

    public enum Condition {
        REJECT_RATE_ABOVE("Tasa de rechazo mayor a umbral (%)"),
        DECLINED_COUNT_ABOVE("Cantidad de rechazadas en el día mayor a umbral"),
        REFUND_RATE_ABOVE("Índice de reembolsos mayor a umbral (%)"),
        THREE_DS_FAILURE_RATE_ABOVE("Fallas 3DS mayores a umbral (%)"),
        DR_CODE_RECURRENT("Código DR recurrente supera umbral de ocurrencias"),
        CHANNEL_VOLUME_DROP("Caída de volumen de un canal mayor a umbral (%)"),
        INTERNAL_ERRORS_ABOVE("Errores internos / configuración mayores a umbral");

        private final String label;

        Condition(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private String id;
    private String name;
    private Scope scope;
    private String merchantId;      // solo si scope == MERCHANT
    private Condition condition;
    private double threshold;
    private String drCode;          // solo para DR_CODE_RECURRENT
    private Processor processor;    // solo para CHANNEL_VOLUME_DROP (null = todos)
    private boolean standard;
    private boolean enabled = true;

    public AlertRule() {
    }

    public AlertRule(String id, String name, Scope scope, String merchantId, Condition condition,
                     double threshold, String drCode, Processor processor, boolean standard, boolean enabled) {
        this.id = id;
        this.name = name;
        this.scope = scope;
        this.merchantId = merchantId;
        this.condition = condition;
        this.threshold = threshold;
        this.drCode = drCode;
        this.processor = processor;
        this.standard = standard;
        this.enabled = enabled;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Scope getScope() { return scope; }
    public void setScope(Scope scope) { this.scope = scope; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public Condition getCondition() { return condition; }
    public void setCondition(Condition condition) { this.condition = condition; }
    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
    public String getDrCode() { return drCode; }
    public void setDrCode(String drCode) { this.drCode = drCode; }
    public Processor getProcessor() { return processor; }
    public void setProcessor(Processor processor) { this.processor = processor; }
    public boolean isStandard() { return standard; }
    public void setStandard(boolean standard) { this.standard = standard; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
