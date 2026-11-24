/*
 * FILE: DatiAbbonamento.java
 * A COSA SERVE: i dati per creare un abbonamento così come li vuole AbbonamentoService (e per controllarne le regole): l'iscritto,
 * il tipo e la data di inizio. La data di fine e il prezzo NON ci sono: li calcola il service dal tipo. È un record (oggetto di soli
 * dati, immutabile) del livello dei service: non conosce il web. I dati arrivano dal modulo delle pagine (AbbonamentoForm.comeDati())
 * o dalla API (AbbonamentoApiController).
 */
package it.palestra.service;

import it.palestra.model.TipoAbbonamento;
import java.time.LocalDate;

public record DatiAbbonamento(Long utenteId, TipoAbbonamento tipo, LocalDate dataInizio) {
}
