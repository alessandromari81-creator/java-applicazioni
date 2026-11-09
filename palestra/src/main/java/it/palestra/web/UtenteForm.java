/*
 * FILE: UtenteForm.java
 * A COSA SERVE: contiene i dati che arrivano dal form HTML (nome, cognome, email, corsi
 * scelti) e le regole di validazione. Non è un'entità: non finisce nel database.
 * PARAGONE LARAVEL: una FormRequest con il metodo rules(). Separare il form
 * dall'entità evita che l'utente possa modificare campi che non deve (es. l'id).
 */
package it.palestra.web;

import it.palestra.model.Utente;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class UtenteForm {

    // @NotBlank = obbligatorio e non solo spazi ('required' di Laravel). "message" è il testo dell'errore.
    // @Size(max = 255): le colonne del database sono VARCHAR(255). Senza questo limite un testo più
    // lungo supererebbe la validazione e farebbe fallire l'INSERT con un errore 500. Così invece
    // l'utente vede un messaggio vicino al campo (come la regola 'max:255' di Laravel).
    @NotBlank(message = "Il nome è obbligatorio")
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String nome;

    @NotBlank(message = "Il cognome è obbligatorio")
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String cognome;

    // Due regole insieme: obbligatoria e con formato email ('required|email').
    @NotBlank(message = "L'email è obbligatoria")
    @Email(message = "Email non valida")
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String email;

    // Gli id dei corsi spuntati nelle checkbox (può essere vuoto).
    private List<Long> corsiIds = new ArrayList<>();

    // Metodo "factory" statico: trasforma un Utente del database in un form precompilato.
    // Serve nella pagina di modifica per mostrare i valori attuali.
    public static UtenteForm da(Utente u) {
        UtenteForm f = new UtenteForm();
        f.nome = u.getNome();
        f.cognome = u.getCognome();
        f.email = u.getEmail();
        // Per ogni corso dell'utente aggiunge il suo id alla lista (così la checkbox risulta spuntata).
        u.getCorsi().forEach(c -> f.corsiIds.add(c.getId()));
        return f;
    }

    // Getter e setter: Spring li usa per riempire l'oggetto con i dati del form (data binding)
    // e Thymeleaf per rimostrarli. Il nome del campo HTML deve coincidere col nome del campo qui.
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCognome() { return cognome; }
    public void setCognome(String cognome) { this.cognome = cognome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public List<Long> getCorsiIds() { return corsiIds; }
    public void setCorsiIds(List<Long> corsiIds) { this.corsiIds = corsiIds; }
}
