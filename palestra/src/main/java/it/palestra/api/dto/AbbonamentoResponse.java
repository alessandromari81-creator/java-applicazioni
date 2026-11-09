/*
 * FILE: AbbonamentoResponse.java
 * A COSA SERVE: il JSON con cui la API mostra un abbonamento, con lo stato calcolato (FUTURO, ATTIVO, SCADUTO) e il
 * prezzo come numero decimale esatto. Esempio:
 *   { "id": 7, "utente": {"id": 1, "nome": "Mario", "cognome": "Rossi"}, "tipo": "MENSILE",
 *     "dataInizio": "2026-11-09", "dataFine": "2026-12-08", "prezzo": 40.00, "stato": "ATTIVO" }
 */
package it.palestra.api.dto;

import it.palestra.model.Abbonamento;
import it.palestra.model.StatoAbbonamento;
import it.palestra.model.TipoAbbonamento;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AbbonamentoResponse(Long id, UtenteSintesi utente, TipoAbbonamento tipo, LocalDate dataInizio,
                                  LocalDate dataFine, BigDecimal prezzo, StatoAbbonamento stato) {

    // "oggi" serve a calcolare lo stato (che non è salvato nel database): vedi Abbonamento.stato().
    public static AbbonamentoResponse da(Abbonamento a, LocalDate oggi) {
        return new AbbonamentoResponse(a.getId(), UtenteSintesi.da(a.getUtente()), a.getTipo(), a.getDataInizio(),
                a.getDataFine(), a.getPrezzo(), a.stato(oggi));
    }
}
