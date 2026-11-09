/*
 * FILE: DataResponse.java
 * A COSA SERVE: risposta semplice con una sola data, usata da "primo giorno libero" per un rinnovo:
 *   { "data": "2026-12-09" }
 */
package it.palestra.api.dto;

import java.time.LocalDate;

public record DataResponse(LocalDate data) {
}
