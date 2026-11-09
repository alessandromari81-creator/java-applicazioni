/*
 * FILE: AbbonamentoRequest.java
 * A COSA SERVE: il JSON per creare un abbonamento. L'iscritto è nell'indirizzo (POST /api/utenti/{id}/abbonamenti), qui
 * si indicano solo tipo e data di inizio (formato aaaa-mm-gg). Data di fine e prezzo si calcolano dal tipo.
 *   { "tipo": "TRIMESTRALE", "dataInizio": "2026-12-09" }
 */
package it.palestra.api.dto;

import it.palestra.model.TipoAbbonamento;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record AbbonamentoRequest(
        @NotNull(message = "Il tipo è obbligatorio") TipoAbbonamento tipo,
        @NotNull(message = "La data di inizio è obbligatoria") LocalDate dataInizio) {
}
