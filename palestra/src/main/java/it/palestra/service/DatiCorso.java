/*
 * FILE: DatiCorso.java
 * A COSA SERVE: i dati di un corso così come li vuole CorsoService per creare o modificare un corso (e per controllare la regola
 * sull'istruttore). È un record (oggetto di soli dati, immutabile) del livello dei service: non conosce il web. I dati arrivano dal
 * modulo delle pagine (CorsoForm.comeDati()) o dalla richiesta della API (CorsoRequest.comeDati()).
 * istruttoreId è facoltativo: null = nessun istruttore.
 */
package it.palestra.service;

import it.palestra.model.Livello;

public record DatiCorso(String nome, String descrizione, Integer durataMinuti, Livello livello, Long istruttoreId) {
}
