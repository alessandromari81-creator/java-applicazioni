/*
 * FILE: CorsoRequest.java
 * A COSA SERVE: il JSON che si invia per creare o modificare un corso. Contiene SOLO i campi modificabili: anche se il
 * client aggiungesse "id" o altri campi, vengono ignorati (così non si può modificare per sbaglio, o per attacco, ciò che
 * non deve: protezione dal "mass assignment", come $fillable di Laravel).
 *   { "nome": "Zumba", "descrizione": "Danza", "durataMinuti": 55, "livello": "INTERMEDIO", "istruttoreId": 3 }
 * Le regole di validazione sono le stesse del form delle pagine (CorsoForm).
 */
package it.palestra.api.dto;

import it.palestra.model.Livello;
import it.palestra.web.CorsoForm;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CorsoRequest(
        @NotBlank(message = "Il nome è obbligatorio")
        @Size(max = 255, message = "Massimo 255 caratteri") String nome,

        @Size(max = 255, message = "Massimo 255 caratteri") String descrizione,

        @NotNull(message = "La durata è obbligatoria")
        @Min(value = 1, message = "La durata deve essere almeno 1 minuto") Integer durataMinuti,

        @NotNull(message = "Il livello è obbligatorio") Livello livello,

        // Facoltativo: id dell'istruttore (un utente con ruolo ISTRUTTORE), oppure assente/null per nessun istruttore.
        Long istruttoreId) {

    // Trasforma la richiesta nel "form" che il service già conosce: così la logica (e le regole) sono le STESSE delle
    // pagine, scritte una sola volta.
    public CorsoForm comeForm() {
        CorsoForm form = new CorsoForm();
        form.setNome(nome);
        form.setDescrizione(descrizione);
        form.setDurataMinuti(durataMinuti);
        form.setLivello(livello);
        form.setIstruttoreId(istruttoreId);
        return form;
    }
}
