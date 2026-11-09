/*
 * FILE: CorsoSintesi.java
 * A COSA SERVE: la versione "corta" di un corso (id e nome), usata nella risposta di un utente per elencare i corsi che
 * frequenta e quelli che insegna.
 */
package it.palestra.api.dto;

import it.palestra.model.Corso;

public record CorsoSintesi(Long id, String nome) {

    public static CorsoSintesi da(Corso c) {
        return new CorsoSintesi(c.getId(), c.getNome());
    }
}
