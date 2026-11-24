/*
 * FILE: DatiUtente.java
 * A COSA SERVE: i dati di un utente così come li vuole UtenteService per creare o modificare una scheda (e per controllarne le regole).
 * È un record (oggetto di soli dati, immutabile) che appartiene al livello dei service e NON conosce il web: i dati arrivano dal
 * modulo delle pagine (UtenteForm.comeDati()) o dalla richiesta della API (UtenteApiController) e vengono trasformati qui, così il
 * service non dipende né dalle classi delle pagine né da quelle della API.
 * I ruoli e i corsi sono indicati per id. La password è il testo in chiaro scritto da chi compila (vuota o null = non cambiarla).
 */
package it.palestra.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record DatiUtente(String nome, String cognome, String email, String password,
                         String indirizzo, String cap, String citta,
                         List<Long> ruoliIds, List<Long> corsiIds) {

    // Costruttore compatto: una lista assente diventa vuota e le liste si copiano in sola lettura, così il record non cambia
    // se chi l'ha creato modifica poi la sua lista.
    public DatiUtente {
        ruoliIds = ruoliIds == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(ruoliIds));
        corsiIds = corsiIds == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(corsiIds));
    }
}
