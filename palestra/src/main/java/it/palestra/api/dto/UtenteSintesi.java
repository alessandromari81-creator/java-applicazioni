/*
 * FILE: UtenteSintesi.java
 * A COSA SERVE: la versione "corta" di un utente (id, nome, cognome), usata dentro altre risposte: l'istruttore di un
 * corso, l'iscritto di un abbonamento. Evita di ripetere tutti i dati dell'utente (e di esporne di inutili).
 */
package it.palestra.api.dto;

import it.palestra.model.Utente;

public record UtenteSintesi(Long id, String nome, String cognome) {

    // Costruisce la sintesi da un'entità (null resta null: es. corso senza istruttore).
    public static UtenteSintesi da(Utente u) {
        return u == null ? null : new UtenteSintesi(u.getId(), u.getNome(), u.getCognome());
    }
}
