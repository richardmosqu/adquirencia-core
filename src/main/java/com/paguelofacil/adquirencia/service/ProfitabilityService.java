package com.paguelofacil.adquirencia.service;

import com.paguelofacil.adquirencia.data.DataStore;
import com.paguelofacil.adquirencia.domain.Acquirer;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CRUD de adquirentes y calculadora de rentabilidad por adquirente.
 *
 * <p>Modelo de negocio (todos los montos en USD):</p>
 * <ul>
 *   <li>Ingreso PF = volNac * tasa PF nacional + volInt * tasa PF internacional
 *       + trx totales * fee fijo PF.</li>
 *   <li>Costo adquirente = volNac * tasaNac + volInt * tasaInt
 *       + trx totales * fee por trx + fijo mensual.</li>
 *   <li>Ganancia = Ingreso PF − Costo adquirente.</li>
 *   <li>Rentabilidad % = Ganancia / Ingreso PF * 100.</li>
 * </ul>
 *
 * <p>El ingreso PF es igual para todos los adquirentes, así que el más rentable
 * es simplemente el de menor costo (mayor ganancia): el ranking va por ganancia
 * descendente. No existe costo de colaborador en este modelo.</p>
 */
@Service
public class ProfitabilityService {

    private final DataStore store;
    private final AtomicInteger acquirerSeq = new AtomicInteger(100);

    public ProfitabilityService(DataStore store) {
        this.store = store;
    }

    // ---------- CRUD de adquirentes ----------

    public List<Acquirer> acquirers() {
        return store.acquirers();
    }

    public Acquirer createAcquirer(Acquirer a) {
        a.setId("AQ-USR-" + acquirerSeq.incrementAndGet());
        store.acquirers().add(a);
        return a;
    }

    public Optional<Acquirer> updateAcquirer(String id, Acquirer patch) {
        Optional<Acquirer> found = store.acquirers().stream()
                .filter(a -> a.getId().equals(id)).findFirst();
        found.ifPresent(a -> {
            if (patch.getName() != null) a.setName(patch.getName());
            a.setNationalCostRate(patch.getNationalCostRate());
            a.setInternationalCostRate(patch.getInternationalCostRate());
            a.setFixedCostPerTx(patch.getFixedCostPerTx());
            a.setMonthlyFixedCost(patch.getMonthlyFixedCost());
        });
        return found;
    }

    public boolean deleteAcquirer(String id) {
        return store.acquirers().removeIf(a -> a.getId().equals(id));
    }

    // ---------- cálculo de rentabilidad ----------

    /**
     * Datos del comercio prospecto y el pricing de PF a evaluar.
     * {@code pfRate} es la comisión nacional y {@code pfIntlRate} la internacional.
     */
    public record CalcRequest(
            String merchantName,
            double natVol, double natTx,
            double intlVol, double intlTx,
            Double pfRate, Double pfIntlRate, Double pfFee) {
    }

    /** Fila comparativa por adquirente. */
    public record AcquirerResult(
            String acquirerId, String acquirerName,
            double pfRevenue, double acquirerCost, double profit, double profitabilityPct,
            double nationalCost, double internationalCost, double txCost, double monthlyFixed) {
    }

    /** Resultado del cálculo: contexto común + filas ordenadas por ganancia desc. */
    public record CalcResponse(
            String merchantName,
            double totalVol, double totalTx,
            double pfRate, double pfIntlRate, double pfFee, double pfRevenue,
            List<AcquirerResult> rows) {
    }

    public CalcResponse calculate(CalcRequest req) {
        double pfRate = req.pfRate() != null ? req.pfRate() : 0.035;
        // si no se envía, la comisión internacional se asume igual a la nacional
        double pfIntlRate = req.pfIntlRate() != null ? req.pfIntlRate() : pfRate;
        double pfFee = req.pfFee() != null ? req.pfFee() : 0.50;

        double totalVol = req.natVol() + req.intlVol();
        double totalTx = req.natTx() + req.intlTx();
        double pfRevenue = req.natVol() * pfRate + req.intlVol() * pfIntlRate + totalTx * pfFee;

        List<AcquirerResult> rows = store.acquirers().stream()
                .map(a -> {
                    double nationalCost = req.natVol() * a.getNationalCostRate();
                    double internationalCost = req.intlVol() * a.getInternationalCostRate();
                    double txCost = totalTx * a.getFixedCostPerTx();
                    double monthlyFixed = a.getMonthlyFixedCost();
                    double acquirerCost = nationalCost + internationalCost + txCost + monthlyFixed;
                    double profit = pfRevenue - acquirerCost;
                    double profitabilityPct = pfRevenue == 0 ? 0 : profit / pfRevenue * 100.0;
                    return new AcquirerResult(a.getId(), a.getName(),
                            pfRevenue, acquirerCost, profit, profitabilityPct,
                            nationalCost, internationalCost, txCost, monthlyFixed);
                })
                .sorted(Comparator.comparingDouble(AcquirerResult::profit).reversed())
                .toList();

        return new CalcResponse(req.merchantName(), totalVol, totalTx,
                pfRate, pfIntlRate, pfFee, pfRevenue, rows);
    }
}
