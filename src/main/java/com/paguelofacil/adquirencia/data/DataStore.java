package com.paguelofacil.adquirencia.data;

import com.paguelofacil.adquirencia.domain.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Almacén en memoria con datos de demostración deterministas.
 *
 * <p>Es el único punto que habría que reemplazar para conectar el Core a las
 * fuentes reales (reportes/APIs de PowerTranz y Evertec y la base interna de
 * transacciones): el resto de la aplicación consume estas listas a través de
 * los servicios.</p>
 */
@Component
public class DataStore {

    public static final int HISTORY_DAYS = 90;

    /** Códigos DR (decline reason) típicos y su descripción operativa. */
    public static final Map<String, String> DR_CODES = new LinkedHashMap<>();

    static {
        DR_CODES.put("05", "Do not honor — rechazo genérico del emisor");
        DR_CODES.put("51", "Fondos insuficientes");
        DR_CODES.put("54", "Tarjeta vencida");
        DR_CODES.put("91", "Emisor no disponible / timeout");
        DR_CODES.put("63", "Violación de seguridad");
        DR_CODES.put("12", "Transacción inválida");
        DR_CODES.put("3DS", "Autenticación 3DS fallida");
        DR_CODES.put("CFG", "Error interno / credencial mal configurada");
    }

    private final List<Merchant> merchants = new ArrayList<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<AlertRule> alertRules = new CopyOnWriteArrayList<>();
    private final List<Project> projects = new ArrayList<>();
    private final List<Credential> credentials = new CopyOnWriteArrayList<>();
    private final List<PaymentPoint> paymentPoints = new ArrayList<>();
    private final List<DocumentItem> documents = new ArrayList<>();
    private final List<Acquirer> acquirers = new CopyOnWriteArrayList<>();

    private long txSeq = 1;

    @PostConstruct
    void seed() {
        Random rnd = new Random(42);
        seedMerchants();
        seedTransactions(rnd);
        seedAlertRules();
        seedProjects();
        seedCredentials(rnd);
        seedPaymentPoints(rnd);
        seedDocuments();
        seedAcquirers();
    }

    private void seedMerchants() {
        merchants.add(new Merchant("M-001", "Supermercado Andes", "Retail / Supermercado", "Ciudad de Panamá"));
        merchants.add(new Merchant("M-002", "Farmacia Vital", "Salud / Farmacia", "Ciudad de Panamá"));
        merchants.add(new Merchant("M-003", "Hotel Casco Antiguo", "Turismo / Hotelería", "Ciudad de Panamá"));
        merchants.add(new Merchant("M-004", "Restaurante Mar Azul", "Alimentos y bebidas", "Panamá Pacífico"));
        merchants.add(new Merchant("M-005", "Tienda ModaPlus", "Retail / Moda", "Albrook"));
        merchants.add(new Merchant("M-006", "Academia Lumen", "Educación", "David"));
        merchants.add(new Merchant("M-007", "Gimnasio FitPro", "Deporte / Suscripciones", "Ciudad de Panamá"));
        merchants.add(new Merchant("M-008", "Ferretería El Tornillo", "Retail / Ferretería", "Colón"));
        merchants.add(new Merchant("M-009", "Viajes Istmo", "Turismo / Agencia", "Ciudad de Panamá"));
        merchants.add(new Merchant("M-010", "Clínica Salud Plus", "Salud / Clínica", "Santiago"));
        merchants.add(new Merchant("M-011", "Librería Atenea", "Retail / Librería", "Ciudad de Panamá"));
        merchants.add(new Merchant("M-012", "TechStore PTY", "Retail / Electrónica", "Costa del Este"));
    }

    /**
     * Genera ~90 días de transacciones con perfiles por comercio y dos
     * escenarios que disparan alertas: pico de rechazos hoy en Tienda ModaPlus
     * y caída de volumen del canal Evertec hoy.
     */
    private void seedTransactions(Random rnd) {
        LocalDate today = LocalDate.now();

        for (Merchant m : merchants) {
            MerchantProfile p = profileFor(m.id());
            for (int back = HISTORY_DAYS - 1; back >= 0; back--) {
                LocalDate day = today.minusDays(back);
                double weekday = switch (day.getDayOfWeek()) {
                    case SATURDAY -> 1.25;
                    case SUNDAY -> 0.7;
                    case FRIDAY -> 1.2;
                    default -> 1.0;
                };
                // Crecimiento suave del negocio a lo largo de la historia.
                double growth = 1.0 + 0.25 * (HISTORY_DAYS - back) / (double) HISTORY_DAYS;
                int count = (int) Math.round(p.dailyBase * weekday * growth * (0.75 + rnd.nextDouble() * 0.5));

                for (int i = 0; i < count; i++) {
                    boolean evertec = rnd.nextDouble() < p.evertecShare;
                    // Escenario: caída del canal Evertec hoy (se enruta menos y falla más).
                    boolean evertecDownToday = back == 0;
                    if (evertec && evertecDownToday && rnd.nextDouble() < 0.55) {
                        evertec = false;
                    }
                    Processor proc = evertec ? Processor.EVERTEC : Processor.POWERTRANZ;

                    ServiceType service = pickService(rnd, p);
                    boolean threeDs = rnd.nextDouble() < p.threeDsShare;

                    double approveProb = p.baseApproval + (threeDs ? 0.03 : -0.02);
                    if (proc == Processor.EVERTEC && evertecDownToday) {
                        approveProb -= 0.30; // el canal degradado rechaza mucho más (código 91)
                    }
                    // Escenario: pico de rechazos hoy en Tienda ModaPlus.
                    if (m.id().equals("M-005") && back == 0) {
                        approveProb -= 0.35;
                    }
                    boolean approved = rnd.nextDouble() < approveProb;

                    String drCode = null;
                    if (!approved) {
                        drCode = pickDrCode(rnd, threeDs, proc == Processor.EVERTEC && evertecDownToday,
                                m.id().equals("M-005") && back == 0);
                    }

                    BigDecimal amount = amount(rnd, p);
                    LocalDateTime ts = day.atTime(7 + rnd.nextInt(16), rnd.nextInt(60), rnd.nextInt(60));
                    transactions.add(new Transaction(txSeq++, m.id(), proc, service,
                            Transaction.TxType.SALE, threeDs, approved, drCode, amount, ts));

                    // Reembolsos: proporción pequeña de las ventas aprobadas.
                    if (approved && rnd.nextDouble() < p.refundRate) {
                        LocalDate refundDay = day.plusDays(1 + rnd.nextInt(3));
                        if (!refundDay.isAfter(today)) {
                            LocalDateTime rts = refundDay.atTime(9 + rnd.nextInt(9), rnd.nextInt(60), rnd.nextInt(60));
                            transactions.add(new Transaction(txSeq++, m.id(), proc, service,
                                    Transaction.TxType.REFUND, threeDs, true, null, amount, rts));
                        }
                    }
                }
            }
        }
        transactions.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));
    }

    private ServiceType pickService(Random rnd, MerchantProfile p) {
        double r = rnd.nextDouble();
        double acc = 0;
        for (int i = 0; i < p.serviceWeights.length; i++) {
            acc += p.serviceWeights[i];
            if (r < acc) {
                return ServiceType.values()[i];
            }
        }
        return ServiceType.AUTH_CAPTURE;
    }

    private String pickDrCode(Random rnd, boolean threeDs, boolean evertecDown, boolean merchantSpike) {
        if (evertecDown && rnd.nextDouble() < 0.7) {
            return "91";
        }
        if (merchantSpike && rnd.nextDouble() < 0.45) {
            return "05";
        }
        if (threeDs && rnd.nextDouble() < 0.18) {
            return "3DS";
        }
        double r = rnd.nextDouble();
        if (r < 0.34) return "05";
        if (r < 0.58) return "51";
        if (r < 0.70) return "54";
        if (r < 0.80) return "91";
        if (r < 0.88) return "63";
        if (r < 0.95) return "12";
        return "CFG";
    }

    private BigDecimal amount(Random rnd, MerchantProfile p) {
        double v = p.avgTicket * (0.35 + Math.abs(rnd.nextGaussian()) * 0.8);
        return BigDecimal.valueOf(Math.max(2.5, v)).setScale(2, RoundingMode.HALF_UP);
    }

    private MerchantProfile profileFor(String merchantId) {
        return switch (merchantId) {
            case "M-001" -> new MerchantProfile(38, 42.0, 0.45, 0.55, 0.92, 0.012,
                    new double[]{0.55, 0.05, 0.05, 0.30, 0.05});
            case "M-002" -> new MerchantProfile(26, 18.5, 0.40, 0.50, 0.93, 0.008,
                    new double[]{0.60, 0.05, 0.10, 0.20, 0.05});
            case "M-003" -> new MerchantProfile(14, 210.0, 0.35, 0.70, 0.90, 0.030,
                    new double[]{0.45, 0.05, 0.25, 0.15, 0.10});
            case "M-004" -> new MerchantProfile(20, 34.0, 0.50, 0.45, 0.91, 0.015,
                    new double[]{0.50, 0.02, 0.28, 0.15, 0.05});
            case "M-005" -> new MerchantProfile(22, 55.0, 0.45, 0.60, 0.90, 0.045,
                    new double[]{0.40, 0.05, 0.15, 0.35, 0.05});
            case "M-006" -> new MerchantProfile(9, 125.0, 0.30, 0.55, 0.92, 0.010,
                    new double[]{0.25, 0.45, 0.20, 0.05, 0.05});
            case "M-007" -> new MerchantProfile(12, 45.0, 0.35, 0.50, 0.89, 0.020,
                    new double[]{0.15, 0.65, 0.10, 0.05, 0.05});
            case "M-008" -> new MerchantProfile(11, 68.0, 0.55, 0.40, 0.92, 0.010,
                    new double[]{0.60, 0.02, 0.18, 0.15, 0.05});
            case "M-009" -> new MerchantProfile(8, 320.0, 0.40, 0.75, 0.88, 0.035,
                    new double[]{0.35, 0.05, 0.35, 0.15, 0.10});
            case "M-010" -> new MerchantProfile(10, 95.0, 0.35, 0.55, 0.93, 0.008,
                    new double[]{0.45, 0.20, 0.25, 0.05, 0.05});
            case "M-011" -> new MerchantProfile(7, 22.0, 0.45, 0.45, 0.93, 0.010,
                    new double[]{0.50, 0.02, 0.18, 0.25, 0.05});
            default -> new MerchantProfile(16, 240.0, 0.50, 0.65, 0.90, 0.025,
                    new double[]{0.45, 0.05, 0.10, 0.30, 0.10});
        };
    }

    /**
     * Perfil de comportamiento de un comercio para la generación de datos.
     * serviceWeights sigue el orden de {@link ServiceType}.
     */
    private record MerchantProfile(
            int dailyBase,
            double avgTicket,
            double evertecShare,
            double threeDsShare,
            double baseApproval,
            double refundRate,
            double[] serviceWeights) {
    }

    private void seedAlertRules() {
        alertRules.add(new AlertRule("R-STD-1", "Tasa de rechazo global sobre 20%",
                AlertRule.Scope.GLOBAL, null, AlertRule.Condition.REJECT_RATE_ABOVE, 20, null, null, true, true));
        alertRules.add(new AlertRule("R-STD-2", "Caída de volumen por canal sobre 35%",
                AlertRule.Scope.GLOBAL, null, AlertRule.Condition.CHANNEL_VOLUME_DROP, 35, null, null, true, true));
        alertRules.add(new AlertRule("R-STD-3", "Fallas 3DS sobre 8%",
                AlertRule.Scope.GLOBAL, null, AlertRule.Condition.THREE_DS_FAILURE_RATE_ABOVE, 8, null, null, true, true));
        alertRules.add(new AlertRule("R-STD-4", "Código 91 (emisor no disponible) recurrente",
                AlertRule.Scope.GLOBAL, null, AlertRule.Condition.DR_CODE_RECURRENT, 25, "91", null, true, true));
        alertRules.add(new AlertRule("R-STD-5", "Errores internos de configuración sobre 10 al día",
                AlertRule.Scope.GLOBAL, null, AlertRule.Condition.INTERNAL_ERRORS_ABOVE, 10, "CFG", null, true, true));
        alertRules.add(new AlertRule("R-M005-1", "ModaPlus: rechazo diario sobre 25%",
                AlertRule.Scope.MERCHANT, "M-005", AlertRule.Condition.REJECT_RATE_ABOVE, 25, null, null, false, true));
        alertRules.add(new AlertRule("R-M009-1", "Viajes Istmo: reembolsos sobre 5%",
                AlertRule.Scope.MERCHANT, "M-009", AlertRule.Condition.REFUND_RATE_ABOVE, 5, null, null, false, true));
    }

    private void seedProjects() {
        LocalDate today = LocalDate.now();
        projects.add(new Project("P-001", "Migración de comercios a 3DS 2.2",
                "Actualizar la autenticación de los comercios de mayor volumen a 3DS 2.2 en PowerTranz.",
                "En curso", "Richard Mosqueda", today.minusDays(3)));
        projects.add(new Project("P-002", "Conciliación automática Evertec",
                "Automatizar la conciliación diaria de liquidaciones del canal Evertec contra la base interna.",
                "En curso", "Ahiezer Dominguez", today.minusDays(6)));
        projects.add(new Project("P-003", "Catálogo unificado de códigos DR",
                "Mapear los códigos de rechazo de todos los procesadores a un catálogo único con acciones sugeridas.",
                "En curso", "Richard Mosqueda", today.minusDays(1)));
        projects.add(new Project("P-004", "Onboarding express de credenciales",
                "Reducir el tiempo de alta de DBAs y credenciales de 5 días a 48 horas.",
                "Por iniciar", "Equipo Adquirencia", today.minusDays(12)));
        projects.add(new Project("P-005", "Certificación PCI DSS 4.0",
                "Acompañamiento de la recertificación anual y evidencias del área.",
                "En pausa", "Ahiezer Dominguez", today.minusDays(20)));
        projects.add(new Project("P-006", "Dashboard de adquirencia (este Core)",
                "Panel central del área: procesamiento, alertas, credenciales, puntos de pago y documentos.",
                "En curso", "Richard Mosqueda", today));
    }

    private void seedCredentials(Random rnd) {
        String[] types = {"MID", "TID", "API Key", "Terminal 3DS"};
        LocalDateTime now = LocalDateTime.now();
        int seq = 1;
        for (Merchant m : merchants) {
            int n = 1 + rnd.nextInt(3);
            for (int i = 0; i < n; i++) {
                Processor proc = rnd.nextBoolean() ? Processor.POWERTRANZ : Processor.EVERTEC;
                String status = switch (rnd.nextInt(8)) {
                    case 0 -> "Pendiente";
                    case 1 -> "Vencida";
                    default -> "Activa";
                };
                credentials.add(new Credential(String.format("C-%03d", seq++), m.id(), proc,
                        dbaFor(m.name(), i), types[rnd.nextInt(types.length)], status,
                        now.minusDays(rnd.nextInt(45)).minusHours(rnd.nextInt(12))));
            }
        }
        // Escenario: una credencial mal configurada, visible en alertas de errores internos.
        credentials.add(new Credential(String.format("C-%03d", seq), "M-005", Processor.EVERTEC,
                "MODAPLUS*ONLINE", "API Key", "Error de configuración", now.minusHours(6)));
    }

    private String dbaFor(String merchantName, int idx) {
        String base = merchantName.toUpperCase()
                .replace(" ", "")
                .replaceAll("[^A-Z0-9]", "");
        base = base.substring(0, Math.min(10, base.length()));
        return base + (idx == 0 ? "*PTY" : "*WEB" + idx);
    }

    private void seedPaymentPoints(Random rnd) {
        String[] devices = {"POS Android", "POS clásico", "mPOS", "SoftPOS"};
        String[] banks = {"Towerbank", "BAC"};
        int seq = 1;
        for (Merchant m : merchants) {
            int n = rnd.nextInt(4); // no todos los comercios tienen POS físico
            for (int i = 0; i < n; i++) {
                String status = switch (rnd.nextInt(10)) {
                    case 0 -> "Sin conexión";
                    case 1 -> "En reparación";
                    case 2 -> "Por instalar";
                    default -> "Operativo";
                };
                paymentPoints.add(new PaymentPoint(String.format("PP-%03d", seq++), m.id(),
                        banks[rnd.nextInt(banks.length)], devices[rnd.nextInt(devices.length)],
                        "SN-" + (100000 + rnd.nextInt(900000)), status));
            }
        }
    }

    private void seedDocuments() {
        LocalDate today = LocalDate.now();
        documents.add(new DocumentItem("D-001", "Catálogo de códigos de rechazo (DR)",
                "Códigos de rechazo", "Códigos DR por procesador con causa, acción sugerida y responsable.",
                today.minusDays(4)));
        documents.add(new DocumentItem("D-002", "Guía de integración PowerTranz",
                "Guías por procesador", "Parámetros de enrutamiento, credenciales y ambientes de PowerTranz.",
                today.minusDays(15)));
        documents.add(new DocumentItem("D-003", "Guía de integración Evertec",
                "Guías por procesador", "Flujos de autorización, captura y conciliación con Evertec.",
                today.minusDays(9)));
        documents.add(new DocumentItem("D-004", "Procedimiento ante caída de canal",
                "Procedimientos", "Pasos de contingencia y re-enrutamiento cuando un canal degrada.",
                today.minusDays(2)));
        documents.add(new DocumentItem("D-005", "Procedimiento de reenvío de credenciales",
                "Procedimientos", "Cómo regenerar y reenviar credenciales/DBAs de forma segura.",
                today.minusDays(30)));
        documents.add(new DocumentItem("D-006", "Matriz de fallas 3DS",
                "Códigos de rechazo", "Errores frecuentes de autenticación 3DS y su resolución.",
                today.minusDays(7)));
        documents.add(new DocumentItem("D-007", "Checklist de certificación de comercio",
                "Certificaciones", "Requisitos para certificar un comercio nuevo en cada procesador.",
                today.minusDays(21)));
        documents.add(new DocumentItem("D-008", "Ficha de datos del comercio prospecto",
                "Afiliación", "Formulario que llena el comercio con su volumen y transacciones "
                        + "nacionales e internacionales para evaluar su rentabilidad por adquirente.",
                today));
    }

    /**
     * Adquirentes de ejemplo con costos de referencia. Cada adquirente nos
     * reporta sus propios costos; se editan desde la calculadora de rentabilidad
     * y cualquiera que se agregue aquí aparece automáticamente como opción.
     */
    private void seedAcquirers() {
        acquirers.add(new Acquirer("AQ-001", "PowerTranz", 0.0195, 0.0290, 0.10, 0));
        acquirers.add(new Acquirer("AQ-002", "Evertec", 0.0210, 0.0275, 0.12, 150));
    }

    public List<Merchant> merchants() { return merchants; }
    public List<Transaction> transactions() { return transactions; }
    public List<AlertRule> alertRules() { return alertRules; }
    public List<Project> projects() { return projects; }
    public List<Credential> credentials() { return credentials; }
    public List<PaymentPoint> paymentPoints() { return paymentPoints; }
    public List<DocumentItem> documents() { return documents; }
    public List<Acquirer> acquirers() { return acquirers; }
}
