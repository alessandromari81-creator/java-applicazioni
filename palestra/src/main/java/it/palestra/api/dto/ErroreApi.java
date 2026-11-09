/*
 * FILE: ErroreApi.java
 * A COSA SERVE: la forma di TUTTI gli errori che la API restituisce in JSON, così chi la usa sa sempre cosa aspettarsi:
 *   { "status": 404, "errore": "Non trovato", "messaggio": "Corso 5 non trovato" }
 * Per gli errori sui campi dei dati inviati c'è anche "campi", con i messaggi di ciascun campo:
 *   { "status": 400, "errore": "Dati non validi", "messaggio": "...", "campi": { "nome": ["Il nome è obbligatorio"] } }
 * PARAGONE LARAVEL: il JSON di errore di validazione (422) con "message" ed "errors".
 * È un "record" Java: una classe di soli dati, immutabile, con costruttore e getter generati automaticamente.
 */
package it.palestra.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

// @JsonInclude(NON_NULL): i campi null non compaiono nel JSON (così "campi" c'è solo quando serve).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroreApi(int status, String errore, String messaggio, Map<String, List<String>> campi) {

    // Errore senza dettaglio per campo.
    public static ErroreApi di(int status, String errore, String messaggio) {
        return new ErroreApi(status, errore, messaggio, null);
    }
}
