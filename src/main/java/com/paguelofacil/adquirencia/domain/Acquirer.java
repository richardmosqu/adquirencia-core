package com.paguelofacil.adquirencia.domain;

/**
 * Adquirente (procesador) con los costos que nos cobra a PagueloFacil.
 *
 * <p>Es mutable para soportar el CRUD desde la calculadora de rentabilidad:
 * cada adquirente reporta sus propios costos y estos cambian con el tiempo, por
 * eso deben ser fáciles de editar. Todo monto está en USD y las tasas se guardan
 * como fracción (0.021 = 2.1%).</p>
 */
public class Acquirer {

    private String id;
    private String name;
    private double nationalCostRate;        // fracción, p.ej. 0.021 = 2.1%
    private double internationalCostRate;   // fracción, p.ej. 0.029 = 2.9%
    private double fixedCostPerTx;          // USD por transacción
    private double monthlyFixedCost;        // USD fijo mensual (opcional, default 0)

    public Acquirer() {
    }

    public Acquirer(String id, String name, double nationalCostRate, double internationalCostRate,
                    double fixedCostPerTx, double monthlyFixedCost) {
        this.id = id;
        this.name = name;
        this.nationalCostRate = nationalCostRate;
        this.internationalCostRate = internationalCostRate;
        this.fixedCostPerTx = fixedCostPerTx;
        this.monthlyFixedCost = monthlyFixedCost;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getNationalCostRate() { return nationalCostRate; }
    public void setNationalCostRate(double nationalCostRate) { this.nationalCostRate = nationalCostRate; }
    public double getInternationalCostRate() { return internationalCostRate; }
    public void setInternationalCostRate(double internationalCostRate) { this.internationalCostRate = internationalCostRate; }
    public double getFixedCostPerTx() { return fixedCostPerTx; }
    public void setFixedCostPerTx(double fixedCostPerTx) { this.fixedCostPerTx = fixedCostPerTx; }
    public double getMonthlyFixedCost() { return monthlyFixedCost; }
    public void setMonthlyFixedCost(double monthlyFixedCost) { this.monthlyFixedCost = monthlyFixedCost; }
}
