package com.paguelofacil.brujula.eval;

/** Error controlado y amigable durante el análisis por link. */
public class AnalysisException extends RuntimeException {
    public AnalysisException(String message) {
        super(message);
    }
}
