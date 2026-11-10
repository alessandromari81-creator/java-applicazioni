/*
 * FILE: UtenteResponse.java
 * A COSA SERVE: il JSON con cui la API mostra un utente: dati anagrafici, ruoli, corsi frequentati e corsi tenuti.
 * NON contiene la password né il suo hash: c'è solo "haPassword" (vero/falso) per sapere se può fare login.
 * Per questo si usa un DTO e non l'entità Utente, che l'hash invece lo contiene.
 */
package it.palestra.api.dto;

import it.palestra.model.Corso;
import it.palestra.model.Utente;
import java.util.Comparator;
import java.util.List;

public record UtenteResponse(Long id, String nome, String cognome, String email, boolean haPassword,
                             String indirizzo, String cap, String citta, List<String> ruoli, List<CorsoSintesi> corsi, List<CorsoSintesi> corsiInsegnati) {

    // Costruisce la risposta da un utente con ruoli e corsi già caricati (vedi EntityGraph in UtenteRepository).
    // Gli elenchi sono ordinati: così la risposta è sempre uguale per gli stessi dati (utile nei test e ai client).
    public static UtenteResponse da(Utente u) {
        return new UtenteResponse(
                u.getId(), u.getNome(), u.getCognome(), u.getEmail(),
                u.getPasswordHash() != null,
                u.getIndirizzo(), u.getCap(), u.getCitta(),
                u.getRuoli().stream().map(ruolo -> ruolo.getNome()).sorted().toList(),
                sintesi(u.getCorsi()),
                sintesi(u.getCorsiInsegnati()));
    }

    private static List<CorsoSintesi> sintesi(java.util.Collection<Corso> corsi) {
        return corsi.stream().sorted(Comparator.comparing(corso -> corso.getNome())).map(CorsoSintesi::da).toList();
    }
}
