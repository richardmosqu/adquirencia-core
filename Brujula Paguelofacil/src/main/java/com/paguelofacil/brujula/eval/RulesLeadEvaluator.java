package com.paguelofacil.brujula.eval;

import com.paguelofacil.brujula.data.KnowledgeBase;
import com.paguelofacil.brujula.domain.LeadAnalysis;
import com.paguelofacil.brujula.domain.LeadAnalysis.Eligibility;
import com.paguelofacil.brujula.domain.LeadAnalysis.ObjPair;
import com.paguelofacil.brujula.domain.LeadAnalysis.ServiceReco;
import com.paguelofacil.brujula.domain.LeadAnalysis.Strategy;
import com.paguelofacil.brujula.domain.LeadInput;
import com.paguelofacil.brujula.domain.Objection;
import com.paguelofacil.brujula.domain.PfService;
import com.paguelofacil.brujula.domain.Rubro;
import com.paguelofacil.brujula.domain.SalesAngle;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Motor de reglas determinista (offline, sin APIs externas). Cruza los datos
 * del lead con la base de conocimiento comercial para producir las tres
 * salidas: elegibilidad, portafolio priorizado y estrategia comercial.
 */
@Service
public class RulesLeadEvaluator implements LeadEvaluator {

    private static final String NO_ELEGIBLE = "No elegible";
    private static final String ELEGIBLE = "Elegible";
    private static final String CON_CONDICIONES = "Elegible con condiciones";
    private static final double VOLUMEN_ALTO = 100_000; // USD/mes

    private final KnowledgeBase kb;

    public RulesLeadEvaluator(KnowledgeBase kb) {
        this.kb = kb;
    }

    @Override
    public LeadAnalysis evaluate(LeadInput in) {
        String canal = nz(in.canal());
        String tamano = nz(in.tamano());

        Eligibility elig = eligibility(in);
        if (NO_ELEGIBLE.equals(elig.estado())) {
            Strategy stop = new Strategy(
                    "Rubro no elegible: no se recomienda avanzar la venta. Deriva a validación de cumplimiento u ofrece una alternativa permitida.",
                    List.of());
            return new LeadAnalysis(elig, List.of(), stop);
        }

        List<ServiceReco> portafolio = portfolio(in, canal, tamano);
        Strategy estrategia = strategy(canal, tamano);
        return new LeadAnalysis(elig, portafolio, estrategia);
    }

    // ---------- 1) elegibilidad ----------
    private Eligibility eligibility(LeadInput in) {
        List<String> razones = new ArrayList<>();
        List<String> riesgos = new ArrayList<>();
        String rubroTxt = nz(in.rubro());

        Optional<Rubro> match = kb.getRubros().stream()
                .filter(r -> similar(r.getName(), rubroTxt))
                .findFirst();

        String estado;
        if (match.isPresent() && !match.get().isAllowed()) {
            Rubro r = match.get();
            estado = NO_ELEGIBLE;
            razones.add("El rubro “" + r.getName() + "” no está permitido.");
            if (r.getNota() != null && !r.getNota().isBlank()) razones.add(r.getNota());
            riesgos.add("Rubro restringido por política de riesgo / marcas de tarjeta.");
            return new Eligibility(estado, razones, riesgos);
        }

        if (match.isPresent()) {
            estado = ELEGIBLE;
            razones.add("Rubro permitido: " + match.get().getName() + ".");
        } else {
            estado = CON_CONDICIONES;
            razones.add("El rubro “" + rubroTxt + "” no está en el catálogo; requiere validación de cumplimiento.");
            riesgos.add("Rubro no catalogado — validar con el área de riesgo antes de afiliar.");
        }

        // condiciones adicionales
        String canal = nz(in.canal());
        if (canal.equalsIgnoreCase("En línea") || canal.equalsIgnoreCase("Mixto")) {
            riesgos.add("Canal en línea: activar 3DS y monitoreo de fraude.");
        }
        if (in.volumenEstimado() >= VOLUMEN_ALTO) {
            riesgos.add("Volumen alto (" + fmtUsd(in.volumenEstimado()) + "/mes): requiere revisión de KYC y límites.");
            if (ELEGIBLE.equals(estado)) estado = CON_CONDICIONES;
        }
        return new Eligibility(estado, razones, riesgos);
    }

    // ---------- 2) portafolio priorizado ----------
    private List<ServiceReco> portfolio(LeadInput in, String canal, String tamano) {
        String need = nz(in.necesidad()).toLowerCase();
        List<ServiceReco> out = new ArrayList<>();

        for (PfService s : kb.getServices()) {
            Integer channel = channelScore(s.getCanales(), canal);
            if (channel == null) continue; // no encaja con el canal

            int size = sizeScore(s.getTamanos(), tamano);
            int needHits = 0;
            for (String kw : s.getNeeds()) {
                if (!kw.isBlank() && need.contains(kw.toLowerCase())) needHits++;
            }
            int needScore = Math.min(needHits, 2) * 5;
            int score = s.getPeso() + channel + size + needScore;

            out.add(new ServiceReco(s.getCode(), s.getName(), why(s, canal, tamano, needHits > 0), score));
        }
        out.sort(Comparator.comparingInt(ServiceReco::score).reversed());
        return out.size() > 4 ? out.subList(0, 4) : out;
    }

    /** null = el servicio no encaja con el canal (se excluye). */
    private Integer channelScore(List<String> canales, String canal) {
        if (canales == null || canales.isEmpty()) return 2;   // aplica a todos
        if (containsIgnoreCase(canales, canal)) return 6;
        if (canal.equalsIgnoreCase("Mixto")) return 3;        // mixto puede usar servicios de ambos mundos
        return null;
    }

    private int sizeScore(List<String> tamanos, String tamano) {
        if (tamanos == null || tamanos.isEmpty()) return 1;
        return containsIgnoreCase(tamanos, tamano) ? 4 : -3;
    }

    private String why(PfService s, String canal, String tamano, boolean needMatched) {
        List<String> parts = new ArrayList<>();
        if (needMatched) parts.add("cubre la necesidad detectada");
        if (!s.getCanales().isEmpty() && containsIgnoreCase(s.getCanales(), canal)) parts.add("encaja con el canal " + canal.toLowerCase());
        if (!s.getTamanos().isEmpty() && containsIgnoreCase(s.getTamanos(), tamano)) parts.add("ideal para comercios " + tamano.toLowerCase());
        if (parts.isEmpty()) return s.getDescripcion();
        String r = String.join(" · ", parts);
        return Character.toUpperCase(r.charAt(0)) + r.substring(1) + ".";
    }

    // ---------- 3) estrategia comercial ----------
    private Strategy strategy(String canal, String tamano) {
        String angulo = kb.getAngles().stream()
                .filter(a -> canal.equalsIgnoreCase(a.getCanal()))
                .map(SalesAngle::getAngulo).findFirst()
                .orElseGet(() -> kb.getAngles().stream()
                        .filter(a -> "Cualquiera".equalsIgnoreCase(a.getCanal()))
                        .map(SalesAngle::getAngulo).findFirst()
                        .orElse("PagueloFacil se adapta a la operación del comercio con activación rápida."));

        Set<String> triggers = new LinkedHashSet<>(List.of(canal, tamano, "Cualquiera"));
        List<ObjPair> objeciones = new ArrayList<>();
        for (Objection o : kb.getObjections()) {
            if (triggers.stream().anyMatch(t -> t.equalsIgnoreCase(o.getTrigger()))) {
                objeciones.add(new ObjPair(o.getObjecion(), o.getRespuesta()));
            }
            if (objeciones.size() >= 4) break;
        }
        return new Strategy(angulo, objeciones);
    }

    // ---------- utilidades ----------
    private static String nz(String s) { return s == null ? "" : s.trim(); }

    private static boolean containsIgnoreCase(List<String> list, String v) {
        return list.stream().anyMatch(x -> x != null && x.equalsIgnoreCase(v));
    }

    /** Coincidencia flexible de rubro (igual o uno contiene al otro). */
    private static boolean similar(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.toLowerCase().trim(), y = b.toLowerCase().trim();
        if (x.isEmpty() || y.isEmpty()) return false;
        return x.equals(y) || x.contains(y) || y.contains(x);
    }

    private static String fmtUsd(double v) {
        return "$" + String.format("%,.0f", v);
    }
}
