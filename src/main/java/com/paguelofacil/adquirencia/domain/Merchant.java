package com.paguelofacil.adquirencia.domain;

/** Comercio afiliado que procesa a través de PagueloFacil. */
public record Merchant(
        String id,
        String name,
        String category,
        String city) {
}
