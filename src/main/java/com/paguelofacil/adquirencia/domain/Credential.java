package com.paguelofacil.adquirencia.domain;

import java.time.LocalDate;

/**
 * Credencial / DBA de un comercio, con todo su ciclo de vida (desde que se
 * solicita al banco hasta que se habilita en producción) y el control de
 * pruebas por marca de tarjeta. Es un registro mutable: el módulo de
 * Credenciales solo TRACKEA el proceso; las pruebas reales se hacen en el core
 * de PagueloFacil, no aquí.
 *
 * <p>Estados del pipeline (en orden):
 * Solicitado → En espera de credenciales → Recibido → En pruebas →
 * Visto bueno enviado → Aprobado por BAC → Habilitado/Configurado.</p>
 */
public class Credential {

    private String id;
    private String merchantId;
    private String bank;            // BAC | Towerbank | ...
    private Processor processor;    // PowerTranz | Evertec
    private String afiliado;        // nombre de afiliación en el banco
    private LocalDate fechaSolicitud;
    private String status;          // uno del pipeline
    private boolean rebill;

    // Credenciales sensibles (se muestran en el popup "Ver credenciales")
    private String powertranzId;
    private String contrasena;
    private String tarjetas;        // "MC, VISA, AMEX"
    private String monedas;         // "USD"
    private boolean threeDs;

    // Control de pruebas por marca: estado "Pendiente" | "OK" | "Falló" + código
    private String pruebaMc = "Pendiente";
    private String codigoOperacionMc;
    private String pruebaVisa = "Pendiente";
    private String codigoOperacionVisa;
    private String pruebaAmex = "Pendiente";
    private String codigoOperacionAmex;
    private boolean reembolsosPruebas;   // reembolsos de pruebas emitidos

    private boolean aprobadoPorBac;
    private double limitePorTrx;
    private double limiteMensual;
    private String notas;
    private LocalDate fechaMigracion;
    private String observaciones;

    private boolean configError;    // uso interno: alimenta la alerta de errores internos

    public Credential() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getBank() { return bank; }
    public void setBank(String bank) { this.bank = bank; }
    public Processor getProcessor() { return processor; }
    public void setProcessor(Processor processor) { this.processor = processor; }
    public String getAfiliado() { return afiliado; }
    public void setAfiliado(String afiliado) { this.afiliado = afiliado; }
    public LocalDate getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDate fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isRebill() { return rebill; }
    public void setRebill(boolean rebill) { this.rebill = rebill; }
    public String getPowertranzId() { return powertranzId; }
    public void setPowertranzId(String powertranzId) { this.powertranzId = powertranzId; }
    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }
    public String getTarjetas() { return tarjetas; }
    public void setTarjetas(String tarjetas) { this.tarjetas = tarjetas; }
    public String getMonedas() { return monedas; }
    public void setMonedas(String monedas) { this.monedas = monedas; }
    public boolean isThreeDs() { return threeDs; }
    public void setThreeDs(boolean threeDs) { this.threeDs = threeDs; }
    public String getPruebaMc() { return pruebaMc; }
    public void setPruebaMc(String pruebaMc) { this.pruebaMc = pruebaMc; }
    public String getCodigoOperacionMc() { return codigoOperacionMc; }
    public void setCodigoOperacionMc(String codigoOperacionMc) { this.codigoOperacionMc = codigoOperacionMc; }
    public String getPruebaVisa() { return pruebaVisa; }
    public void setPruebaVisa(String pruebaVisa) { this.pruebaVisa = pruebaVisa; }
    public String getCodigoOperacionVisa() { return codigoOperacionVisa; }
    public void setCodigoOperacionVisa(String codigoOperacionVisa) { this.codigoOperacionVisa = codigoOperacionVisa; }
    public String getPruebaAmex() { return pruebaAmex; }
    public void setPruebaAmex(String pruebaAmex) { this.pruebaAmex = pruebaAmex; }
    public String getCodigoOperacionAmex() { return codigoOperacionAmex; }
    public void setCodigoOperacionAmex(String codigoOperacionAmex) { this.codigoOperacionAmex = codigoOperacionAmex; }
    public boolean isReembolsosPruebas() { return reembolsosPruebas; }
    public void setReembolsosPruebas(boolean reembolsosPruebas) { this.reembolsosPruebas = reembolsosPruebas; }
    public boolean isAprobadoPorBac() { return aprobadoPorBac; }
    public void setAprobadoPorBac(boolean aprobadoPorBac) { this.aprobadoPorBac = aprobadoPorBac; }
    public double getLimitePorTrx() { return limitePorTrx; }
    public void setLimitePorTrx(double limitePorTrx) { this.limitePorTrx = limitePorTrx; }
    public double getLimiteMensual() { return limiteMensual; }
    public void setLimiteMensual(double limiteMensual) { this.limiteMensual = limiteMensual; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public LocalDate getFechaMigracion() { return fechaMigracion; }
    public void setFechaMigracion(LocalDate fechaMigracion) { this.fechaMigracion = fechaMigracion; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public boolean isConfigError() { return configError; }
    public void setConfigError(boolean configError) { this.configError = configError; }
}
