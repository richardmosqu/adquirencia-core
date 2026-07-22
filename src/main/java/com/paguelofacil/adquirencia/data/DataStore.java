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

    /**
     * Catálogo oficial de códigos de rechazo (DR) de PagueloFacil, tomado del
     * documento maestro "Códigos de rechazo — Referencia operativa" (act.
     * 18/06/2026). {@link #DR_CODES} mapea cada código a su motivo y
     * {@link #DR_ACTIONS} a su plan de acción recomendado.
     */
    public static final Map<String, String> DR_CODES = new LinkedHashMap<>();
    public static final Map<String, String> DR_ACTIONS = new LinkedHashMap<>();

    // Planes de acción oficiales (columna "Plan de acción" del documento).
    public static final String ACT_APPROVED = "Ninguna acción, transacción aprobada";
    public static final String ACT_ISSUER = "Contactar al banco emisor";
    public static final String ACT_PF = "Contactar a PagueloFacil";
    public static final String ACT_AUTH = "El cliente debe completar la autenticación";
    public static final String ACT_CARD = "El cliente debe verificar los datos de la tarjeta";

    private static void dr(String code, String reason, String action) {
        DR_CODES.put(code, reason);
        DR_ACTIONS.put(code, action);
    }

    static {
        dr("0", "Aprobada", ACT_APPROVED);
        dr("00", "Sin respuesta del banco emisor", ACT_ISSUER);
        dr("1", "Referir al emisor / retener tarjeta (perdida)", ACT_ISSUER);
        dr("2", "Referir al emisor / retener tarjeta (robada)", ACT_ISSUER);
        dr("3", "Aceptante o comercio inválido", ACT_ISSUER);
        dr("3D0", "Falla 3DS — el cliente canceló el challenge", ACT_AUTH);
        dr("3D1", "El emisor rechaza la autenticación y pide no autorizar", ACT_AUTH);
        dr("4", "Llamar al emisor / retener tarjeta", ACT_ISSUER);
        dr("5", "Denegada (Do Not Honor)", ACT_ISSUER);
        dr("6", "Error", ACT_ISSUER);
        dr("7", "Retener tarjeta (condición especial)", ACT_ISSUER);
        dr("8", "Aprobada con identificación", ACT_ISSUER);
        dr("9", "Tarjeta vencida", ACT_CARD);
        dr("10", "Aprobada por monto parcial", ACT_ISSUER);
        dr("11", "Denegada (Do Not Honor)", ACT_ISSUER);
        dr("12", "Transacción inválida", ACT_ISSUER);
        dr("13", "Fondos insuficientes / monto inválido", ACT_ISSUER);
        dr("14", "Tarjeta inválida o no registrada", ACT_CARD);
        dr("15", "Emisor no existe", ACT_ISSUER);
        dr("19", "Reintente la transacción", ACT_ISSUER);
        dr("20", "Respuesta inválida", ACT_ISSUER);
        dr("21", "No se realizó ninguna acción", ACT_ISSUER);
        dr("22", "Sospecha de mal funcionamiento", ACT_ISSUER);
        dr("25", "Registro no encontrado", ACT_ISSUER);
        dr("30", "Tarjeta restringida / error de formato", ACT_PF);
        dr("31", "Banco no soportado", ACT_ISSUER);
        dr("33", "Tarjeta vencida, retener", ACT_CARD);
        dr("34", "Sospecha de fraude, retener", ACT_ISSUER);
        dr("36", "Tarjeta restringida", ACT_ISSUER);
        dr("38", "Excedió intentos de PIN", ACT_ISSUER);
        dr("39", "Sin cuenta de crédito", ACT_ISSUER);
        dr("41", "Tarjeta perdida, retener", ACT_ISSUER);
        dr("43", "Tarjeta robada, retener", ACT_ISSUER);
        dr("51", "Fondos insuficientes", ACT_ISSUER);
        dr("54", "Tarjeta vencida", ACT_CARD);
        dr("55", "PIN incorrecto", ACT_CARD);
        dr("57", "Transacción no permitida al emisor", ACT_ISSUER);
        dr("58", "Transacción no permitida en la terminal", ACT_ISSUER);
        dr("59", "Sospecha de fraude", ACT_ISSUER);
        dr("60", "Contactar al adquirente", ACT_ISSUER);
        dr("61", "Excede límite de monto", ACT_ISSUER);
        dr("62", "Tarjeta restringida", ACT_ISSUER);
        dr("63", "Violación de seguridad", ACT_ISSUER);
        dr("65", "Excede límite de transacciones diarias", ACT_ISSUER);
        dr("75", "Excedió intentos de PIN permitidos", ACT_ISSUER);
        dr("76", "No se ubicó registro previo", ACT_PF);
        dr("77", "Error de conciliación", ACT_PF);
        dr("78", "Contactar al emisor / tarjeta bloqueada o inválida", ACT_ISSUER);
        dr("79", "CVV2 incorrecto", ACT_CARD);
        dr("80", "Lote duplicado", ACT_PF);
        dr("82", "CVV/ICVV incorrecto", ACT_CARD);
        dr("85", "Sin razón para rechazar (verificación OK)", ACT_ISSUER);
        dr("89", "ID de terminal inválido", ACT_PF);
        dr("90", "Cierre en proceso (cutoff)", ACT_PF);
        dr("91", "Emisor o switch fuera de servicio", ACT_ISSUER);
        dr("92", "Institución no encontrada / no se puede enrutar", ACT_PF);
        dr("93", "Transacción no permitida (violación de ley)", ACT_ISSUER);
        dr("94", "Mensaje o transacción duplicada", ACT_PF);
        dr("96", "Error de sistema / timeout interno", ACT_PF);
        dr("97", "Excede cantidad de retiros permitidos", ACT_ISSUER);
        dr("CE", "Error de comunicación", ACT_PF);
        dr("DB", "Error de acceso a base de datos", ACT_PF);
        dr("DR", "Rechazada por el motor de riesgo", ACT_PF);
        dr("ET", "Error de terminal / BIN", ACT_PF);
        dr("IA", "Monto inválido", ACT_PF);
        dr("IT", "Terminal inválida", ACT_PF);
        dr("NA", "Sistema no disponible", ACT_PF);
        dr("ND", "Reingrese la transacción", ACT_PF);
        dr("NV", "Transacción inválida", ACT_PF);
        dr("RR", "Error interno del web service", ACT_PF);
        dr("TO", "Reingrese la transacción (timeout)", ACT_PF);
        dr("WE", "Error interno del web service", ACT_PF);
        dr("XR", "Falla en la lectura de la respuesta", ACT_PF);

        // Alias de compatibilidad con los códigos sintéticos de la data demo.
        dr("05", "Denegada (Do Not Honor)", ACT_ISSUER);
        dr("3DS", "Falla de autenticación 3DS (cliente canceló el challenge)", ACT_AUTH);
        dr("CFG", "Error interno / credencial mal configurada", ACT_PF);
    }

    private final List<Merchant> merchants = new ArrayList<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<AlertRule> alertRules = new CopyOnWriteArrayList<>();
    private final List<Project> projects = new ArrayList<>();
    private final List<Credential> credentials = new CopyOnWriteArrayList<>();
    private final List<PaymentPoint> paymentPoints = new ArrayList<>();
    private final List<DocumentItem> documents = new ArrayList<>();
    private final List<Acquirer> acquirers = new CopyOnWriteArrayList<>();
    private final List<ActionPlan> actionPlans = new CopyOnWriteArrayList<>();

    private long txSeq = 1;

    @PostConstruct
    void seed() {
        Random rnd = new Random(42);
        seedMerchants();
        seedTransactions(rnd);
        seedActionPlans();
        seedAlertRules();
        seedProjects();
        seedCredentials(rnd);
        seedPaymentPoints(rnd);
        seedDocuments();
        seedAcquirers();
    }

    private void seedMerchants() {
        merchants.add(new Merchant("28714", "Supermercado Andes", "Retail / Supermercado", "Ciudad de Panamá"));
        merchants.add(new Merchant("28715", "Farmacia Vital", "Salud / Farmacia", "Ciudad de Panamá"));
        merchants.add(new Merchant("28716", "Hotel Casco Antiguo", "Turismo / Hotelería", "Ciudad de Panamá"));
        merchants.add(new Merchant("28717", "Restaurante Mar Azul", "Alimentos y bebidas", "Panamá Pacífico"));
        merchants.add(new Merchant("28722", "Tienda ModaPlus", "Retail / Moda", "Albrook"));
        merchants.add(new Merchant("28719", "Academia Lumen", "Educación", "David"));
        merchants.add(new Merchant("28720", "Gimnasio FitPro", "Deporte / Suscripciones", "Ciudad de Panamá"));
        merchants.add(new Merchant("28721", "Ferretería El Tornillo", "Retail / Ferretería", "Colón"));
        merchants.add(new Merchant("28723", "Viajes Istmo", "Turismo / Agencia", "Ciudad de Panamá"));
        merchants.add(new Merchant("28724", "Clínica Salud Plus", "Salud / Clínica", "Santiago"));
        merchants.add(new Merchant("28725", "Librería Atenea", "Retail / Librería", "Ciudad de Panamá"));
        merchants.add(new Merchant("28726", "TechStore PTY", "Retail / Electrónica", "Costa del Este"));
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
                    if (m.id().equals("28722") && back == 0) {
                        approveProb -= 0.35;
                    }
                    boolean approved = rnd.nextDouble() < approveProb;

                    String drCode = null;
                    if (!approved) {
                        drCode = pickDrCode(rnd, threeDs, proc == Processor.EVERTEC && evertecDownToday,
                                m.id().equals("28722") && back == 0);
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
            case "28714" -> new MerchantProfile(38, 42.0, 0.45, 0.55, 0.92, 0.012,
                    new double[]{0.55, 0.05, 0.05, 0.30, 0.05});
            case "28715" -> new MerchantProfile(26, 18.5, 0.40, 0.50, 0.93, 0.008,
                    new double[]{0.60, 0.05, 0.10, 0.20, 0.05});
            case "28716" -> new MerchantProfile(14, 210.0, 0.35, 0.70, 0.90, 0.030,
                    new double[]{0.45, 0.05, 0.25, 0.15, 0.10});
            case "28717" -> new MerchantProfile(20, 34.0, 0.50, 0.45, 0.91, 0.015,
                    new double[]{0.50, 0.02, 0.28, 0.15, 0.05});
            case "28722" -> new MerchantProfile(22, 55.0, 0.45, 0.60, 0.90, 0.045,
                    new double[]{0.40, 0.05, 0.15, 0.35, 0.05});
            case "28719" -> new MerchantProfile(9, 125.0, 0.30, 0.55, 0.92, 0.010,
                    new double[]{0.25, 0.45, 0.20, 0.05, 0.05});
            case "28720" -> new MerchantProfile(12, 45.0, 0.35, 0.50, 0.89, 0.020,
                    new double[]{0.15, 0.65, 0.10, 0.05, 0.05});
            case "28721" -> new MerchantProfile(11, 68.0, 0.55, 0.40, 0.92, 0.010,
                    new double[]{0.60, 0.02, 0.18, 0.15, 0.05});
            case "28723" -> new MerchantProfile(8, 320.0, 0.40, 0.75, 0.88, 0.035,
                    new double[]{0.35, 0.05, 0.35, 0.15, 0.10});
            case "28724" -> new MerchantProfile(10, 95.0, 0.35, 0.55, 0.93, 0.008,
                    new double[]{0.45, 0.20, 0.25, 0.05, 0.05});
            case "28725" -> new MerchantProfile(7, 22.0, 0.45, 0.45, 0.93, 0.010,
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

    /**
     * Planes de acción de ejemplo. Cada regla se enlaza a uno y ese plan aparece
     * como "Plan de acción recomendado" en el detalle de la alerta.
     */
    private void seedActionPlans() {
        actionPlans.add(new ActionPlan("AP-1", "Contención de rechazos",
                "Reducir la tasa de rechazo revisando causa raíz por código DR.",
                List.of("Identificar los códigos DR más frecuentes del periodo.",
                        "Confirmar si el pico es de un emisor, un BIN o un comercio puntual.",
                        "Contactar al comercio afectado y validar datos de cobro / reintentos.",
                        "Escalar al emisor o a PagueloFacil según el plan de acción del código.")));
        actionPlans.add(new ActionPlan("AP-2", "Recuperación de canal",
                "Restablecer el enrutamiento cuando un canal degrada su volumen.",
                List.of("Verificar el estado del canal (PowerTranz / Evertec) y su disponibilidad.",
                        "Re-enrutar el tráfico al canal sano mientras se estabiliza.",
                        "Abrir ticket con el procesador y monitorear la recuperación.",
                        "Confirmar normalización comparando contra el promedio de 7 días.")));
        actionPlans.add(new ActionPlan("AP-3", "Revisión de autenticación 3DS",
                "Atender fallas de autenticación 3DS que afectan la aprobación.",
                List.of("Revisar la matriz de fallas 3DS y los emisores involucrados.",
                        "Validar la configuración del challenge y el flujo del comercio.",
                        "Acompañar al cliente para completar correctamente la autenticación.",
                        "Ajustar reglas de 3DS por comercio si el patrón persiste.")));
        actionPlans.add(new ActionPlan("AP-4", "Saneamiento de credenciales",
                "Corregir errores internos y credenciales mal configuradas.",
                List.of("Ubicar las credenciales/DBAs en estado de error de configuración.",
                        "Regenerar y reenviar la credencial de forma segura.",
                        "Reprocesar las transacciones afectadas por el error interno.",
                        "Documentar la causa y cerrar con PagueloFacil.")));
        actionPlans.add(new ActionPlan("AP-5", "Gestión de reembolsos",
                "Controlar un índice de reembolsos por encima de lo esperado.",
                List.of("Analizar los reembolsos del periodo por comercio y motivo.",
                        "Contactar al comercio para entender la causa (disputas, servicio).",
                        "Definir acciones de mitigación y seguimiento.",
                        "Monitorear el índice en los siguientes 7 días.")));
    }

    private void seedAlertRules() {
        alertRules.add(new AlertRule("R-STD-1", "Tasa de rechazo global sobre 20%",
                AlertRule.Scope.GLOBAL, null, AlertRule.Condition.REJECT_RATE_ABOVE, 20, null, null, "AP-1", true, true));
        alertRules.add(new AlertRule("R-STD-2", "Caída de volumen por canal sobre 35%",
                AlertRule.Scope.GLOBAL, null, AlertRule.Condition.CHANNEL_VOLUME_DROP, 35, null, null, "AP-2", true, true));
        alertRules.add(new AlertRule("R-STD-3", "Fallas 3DS sobre 8%",
                AlertRule.Scope.GLOBAL, null, AlertRule.Condition.THREE_DS_FAILURE_RATE_ABOVE, 8, null, null, "AP-3", true, true));
        alertRules.add(new AlertRule("R-STD-4", "Código 91 (emisor no disponible) recurrente",
                AlertRule.Scope.GLOBAL, null, AlertRule.Condition.DR_CODE_RECURRENT, 25, "91", null, "AP-1", true, true));
        alertRules.add(new AlertRule("R-STD-5", "Errores internos de configuración sobre 10 al día",
                AlertRule.Scope.GLOBAL, null, AlertRule.Condition.INTERNAL_ERRORS_ABOVE, 10, "CFG", null, "AP-4", true, true));
        alertRules.add(new AlertRule("R-M005-1", "ModaPlus: rechazo diario sobre 25%",
                AlertRule.Scope.MERCHANT, "28722", AlertRule.Condition.REJECT_RATE_ABOVE, 25, null, null, "AP-1", false, true));
        alertRules.add(new AlertRule("R-M009-1", "Viajes Istmo: reembolsos sobre 5%",
                AlertRule.Scope.MERCHANT, "28723", AlertRule.Condition.REFUND_RATE_ABOVE, 5, null, null, "AP-5", false, true));
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

    /** Estados del ciclo de vida de una credencial (en orden del pipeline). */
    public static final String ST_SOLICITADO = "Solicitado";
    public static final String ST_EN_ESPERA = "En espera de credenciales";
    public static final String ST_RECIBIDO = "Recibido";
    public static final String ST_EN_PRUEBAS = "En pruebas";
    public static final String ST_VISTO_BUENO = "Visto bueno enviado";
    public static final String ST_APROBADO = "Aprobado por BAC";
    public static final String ST_HABILITADO = "Habilitado/Configurado";

    /**
     * Credenciales de demostración repartidas por todo el ciclo de vida, con
     * banco (BAC / Towerbank), datos sensibles y control de pruebas por marca.
     */
    private void seedCredentials(Random rnd) {
        // Solicitadas (recién pedidas al banco)
        credentials.add(newCred("C-001", "28716", "BAC", Processor.POWERTRANZ, ST_SOLICITADO, 3));
        credentials.add(newCred("C-002", "28723", "BAC", Processor.POWERTRANZ, ST_SOLICITADO, 6));

        // En espera de credenciales (el banco procesa la afiliación ~30 días)
        credentials.add(newCred("C-003", "28714", "Towerbank", Processor.EVERTEC, ST_EN_ESPERA, 20));
        Credential c4 = newCred("C-004", "28720", "BAC", Processor.POWERTRANZ, ST_EN_ESPERA, 28);
        c4.setObservaciones("Correo de seguimiento enviado a BAC; esperan respuesta esta semana.");
        credentials.add(c4);

        // Recibidas (llegaron por Mimecast, registradas, aún sin probar)
        Credential c5 = newCred("C-005", "28715", "BAC", Processor.POWERTRANZ, ST_RECIBIDO, 35);
        withSecrets(c5, "PT-28715-3DS", "Fv#2026bac", "AdmFV*2026", 1500, 25000);
        credentials.add(c5);
        Credential c6 = newCred("C-006", "28726", "Towerbank", Processor.EVERTEC, ST_RECIBIDO, 33);
        withSecrets(c6, "EV-28726-STD", "Ts$Tower26", "AdmTS*2026", 3000, 60000);
        credentials.add(c6);

        // En pruebas (validando por marca en el core real; falta emitir reembolsos)
        Credential c7 = newCred("C-007", "28722", "BAC", Processor.POWERTRANZ, ST_EN_PRUEBAS, 40);
        withSecrets(c7, "PT-28722-3DS", "Mp!2026bac", "AdmMP*2026", 2000, 40000);
        c7.setPruebaMc("OK"); c7.setCodigoOperacionMc("OP-778120");
        c7.setPruebaVisa("OK"); c7.setCodigoOperacionVisa("OP-778144");
        c7.setPruebaAmex("Pendiente");
        c7.setReembolsosPruebas(false);
        c7.setConfigError(true); // escenario: alimenta la alerta de errores internos
        c7.setObservaciones("MC y VISA aprobadas. Falta probar AMEX y emitir los reembolsos de las pruebas al día siguiente.");
        credentials.add(c7);

        Credential c8 = newCred("C-008", "28717", "BAC", Processor.POWERTRANZ, ST_EN_PRUEBAS, 42);
        withSecrets(c8, "PT-28717-3DS", "Ma@2026bac", "AdmMA*2026", 1200, 20000);
        c8.setPruebaMc("OK"); c8.setCodigoOperacionMc("OP-779301");
        c8.setPruebaVisa("Pendiente"); c8.setPruebaAmex("Pendiente");
        c8.setReembolsosPruebas(false);
        credentials.add(c8);

        // Visto bueno enviado (pruebas OK y reembolsos hechos; esperando al banco)
        Credential c9 = newCred("C-009", "28724", "BAC", Processor.POWERTRANZ, ST_VISTO_BUENO, 55);
        withSecrets(c9, "PT-28724-3DS", "Cs%2026bac", "AdmCS*2026", 2500, 45000);
        allTestsOk(c9);
        c9.setObservaciones("Visto bueno enviado a BAC el lunes. Aprobación estimada en 1–2 semanas.");
        credentials.add(c9);

        // Aprobado por BAC (aprobada, lista para habilitar/configurar)
        Credential c10 = newCred("C-010", "28719", "Towerbank", Processor.EVERTEC, ST_APROBADO, 70);
        withSecrets(c10, "EV-28719-STD", "Al&2026tow", "AdmAL*2026", 1800, 30000);
        allTestsOk(c10);
        c10.setAprobadoPorBac(true);
        credentials.add(c10);

        // Habilitada/Configurada (migrada a producción, expediente cerrado)
        Credential c11 = newCred("C-011", "28725", "BAC", Processor.POWERTRANZ, ST_HABILITADO, 90);
        withSecrets(c11, "PT-28725-3DS", "Lb*2026bac", "AdmLB*2026", 2200, 38000);
        allTestsOk(c11);
        c11.setAprobadoPorBac(true);
        c11.setFechaMigracion(LocalDate.now().minusDays(8));
        c11.setObservaciones("Migrada y configurada en el enrutamiento del comercio. Expediente cerrado.");
        credentials.add(c11);
    }

    private Credential newCred(String id, String merchantId, String bank, Processor proc,
                               String status, int solicitudDaysAgo) {
        Credential c = new Credential();
        c.setId(id);
        c.setMerchantId(merchantId);
        c.setBank(bank);
        c.setProcessor(proc);
        c.setStatus(status);
        c.setFechaSolicitud(LocalDate.now().minusDays(solicitudDaysAgo));
        c.setAfiliado(afiliadoFor(merchantId));
        c.setTarjetas("MC, VISA, AMEX");
        c.setMonedas("USD");
        c.setThreeDs(proc == Processor.POWERTRANZ);
        c.setRebill(false);
        return c;
    }

    private void withSecrets(Credential c, String ptId, String pass, String adminPw,
                             double perTx, double monthly) {
        c.setPowertranzId(ptId);
        c.setContrasena(pass);
        c.setPwAdminSite(adminPw);
        c.setLimitePorTrx(perTx);
        c.setLimiteMensual(monthly);
    }

    private void allTestsOk(Credential c) {
        c.setPruebaMc("OK"); c.setCodigoOperacionMc("OP-" + (700000 + Math.abs(c.getId().hashCode()) % 90000));
        c.setPruebaVisa("OK"); c.setCodigoOperacionVisa("OP-" + (600000 + Math.abs(c.getId().hashCode()) % 90000));
        c.setPruebaAmex("OK"); c.setCodigoOperacionAmex("OP-" + (500000 + Math.abs(c.getId().hashCode()) % 90000));
        c.setReembolsosPruebas(true);
    }

    private String afiliadoFor(String merchantId) {
        String name = merchants.stream().filter(m -> m.id().equals(merchantId))
                .map(Merchant::name).findFirst().orElse(merchantId);
        String base = name.toUpperCase().replace(" ", "").replaceAll("[^A-Z0-9]", "");
        return base.substring(0, Math.min(10, base.length())) + "*PTY";
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
                "Códigos de rechazo", "Referencia operativa oficial: cada código de rechazo con su motivo "
                        + "y plan de acción recomendado. Ábrelo para consultarlo o imprimirlo.",
                today));
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
    public List<ActionPlan> actionPlans() { return actionPlans; }
}
