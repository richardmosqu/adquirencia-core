package com.paguelofacil.adquirencia.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transacción procesada. {@code drCode} solo viene en rechazos; una venta
 * reembolsada mantiene {@code type=SALE} y el reembolso entra como registro
 * {@code type=REFUND} aparte, referenciando el mismo comercio.
 */
public record Transaction(
        long id,
        String merchantId,
        Processor processor,
        ServiceType serviceType,
        TxType type,
        boolean threeDs,
        boolean approved,
        String drCode,
        BigDecimal amount,
        LocalDateTime timestamp) {

    public enum TxType { SALE, REFUND }
}
