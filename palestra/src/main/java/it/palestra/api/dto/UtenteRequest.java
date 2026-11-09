/*
 * FILE: UtenteRequest.java
 * A COSA SERVE: il JSON per creare o modificare un utente. I ruoli si indicano per NOME (più leggibile di un numero):
 *   { "nome": "Mario", "cognome": "Rossi", "email": "mario@example.com", "password": "facoltativa-se-non-admin",
 *     "ruoli": ["ISCRITTO"], "corsiIds": [1, 2] }
 * Con PUT (modifica) i dati inviati SOSTITUISCONO quelli esistenti: ruoli e corsi non indicati vengono tolti. La password
 * invece, se omessa, resta invariata. L'hash della password non esce mai dalla API (vedi UtenteResponse).
 */
package it.palestra.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UtenteRequest(
        @NotBlank(message = "Il nome è obbligatorio")
        @Size(max = 255, message = "Massimo 255 caratteri") String nome,

        @NotBlank(message = "Il cognome è obbligatorio")
        @Size(max = 255, message = "Massimo 255 caratteri") String cognome,

        @NotBlank(message = "L'email è obbligatoria")
        @Email(message = "Email non valida")
        @Size(max = 255, message = "Massimo 255 caratteri") String email,

        // Facoltativa: obbligatoria solo per chi ha il ruolo ADMIN (regola controllata da UtenteService).
        String password,

        // Nomi dei ruoli (ADMIN, ISTRUTTORE, ISCRITTO). Assente = nessun ruolo.
        List<String> ruoli,

        // Id dei corsi che l'utente frequenta come iscritto. Assente = nessun corso.
        List<Long> corsiIds) {

    // Liste mai null: se il client non le invia, si trattano come vuote.
    public List<String> ruoliOVuota() { return ruoli == null ? List.of() : ruoli; }
    public List<Long> corsiIdsOVuota() { return corsiIds == null ? List.of() : corsiIds; }
}
