/*
 * FILE: CorsoResponse.java
 * A COSA SERVE: il JSON con cui la API mostra un corso. È un DTO (Data Transfer Object): una classe fatta apposta per la
 * risposta, separata dall'entità Corso. PERCHÉ NON RESTITUIRE L'ENTITÀ: ne uscirebbero anche relazioni e campi interni
 * (e per Utente anche l'hash della password!), con rischio di cicli infiniti (corso -> istruttore -> corsi -> ...) e di
 * errori di caricamento "pigro". Il DTO contiene solo ciò che vogliamo mostrare.
 * PARAGONE LARAVEL: una API Resource (JsonResource).
 */
package it.palestra.api.dto;

import it.palestra.model.Corso;
import it.palestra.model.Livello;

public record CorsoResponse(Long id, String nome, String descrizione, int durataMinuti, Livello livello,
                            UtenteSintesi istruttore) {

    // Costruisce la risposta da un corso (con l'istruttore già caricato dal service: vedi EntityGraph nel repository).
    public static CorsoResponse da(Corso c) {
        return new CorsoResponse(c.getId(), c.getNome(), c.getDescrizione(), c.getDurataMinuti(), c.getLivello(),
                UtenteSintesi.da(c.getIstruttore()));
    }
}
