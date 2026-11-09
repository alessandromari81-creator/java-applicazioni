/*
 * FILE: ErroreSuCampoException.java
 * A COSA SERVE: eccezione che i controller della API lanciano quando i dati inviati violano una regola legata a uno o
 * più CAMPI (email già usata, password troppo corta, istruttore senza il ruolo giusto, data nel passato...).
 * GestoreErroriApi la trasforma in una risposta 400 con l'elenco dei campi in errore, nello stesso formato degli errori
 * di validazione automatici (@Valid). Nelle pagine HTML la stessa cosa è BindingResult.rejectValue(...).
 */
package it.palestra.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ErroreSuCampoException extends RuntimeException {

    // Campo -> messaggi. LinkedHashMap mantiene l'ordine di inserimento (utile per risposte sempre uguali).
    private final Map<String, List<String>> campi;

    public ErroreSuCampoException(Map<String, List<String>> campi) {
        super("Dati non validi");
        this.campi = campi;
    }

    // Scorciatoia per un solo campo: throw ErroreSuCampoException.su("email", "Email già registrata");
    public static ErroreSuCampoException su(String campo, String messaggio) {
        Map<String, List<String>> campi = new LinkedHashMap<>();
        campi.put(campo, List.of(messaggio));
        return new ErroreSuCampoException(campi);
    }

    public Map<String, List<String>> getCampi() { return campi; }
}
