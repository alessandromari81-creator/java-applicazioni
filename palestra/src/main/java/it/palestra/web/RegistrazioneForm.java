/*
 * FILE: RegistrazioneForm.java
 * A COSA SERVE: i dati del modulo di registrazione libera, con le regole di validazione. Tutti obbligatori tranne il consenso alle comunicazioni
 * promozionali. L'accettazione dell'informativa sulla privacy è OBBLIGATORIA (senza non si può proseguire); il consenso marketing è libero e separato.
 * Il campo "sito" è una TRAPPOLA per i programmi automatici: nel modulo è nascosto alle persone; se arriva compilato la richiesta viene scartata in silenzio.
 * La password NON è nel modulo: si sceglie dopo aver confermato l'email.
 */
package it.palestra.web;

import it.palestra.service.DatiRegistrazione;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegistrazioneForm {

    @NotBlank(message = "Il nome è obbligatorio")
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String nome;

    @NotBlank(message = "Il cognome è obbligatorio")
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String cognome;

    @NotBlank(message = "L'email è obbligatoria")
    @Email(message = "Email non valida")
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String email;

    @NotBlank(message = "L'indirizzo è obbligatorio")
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String indirizzo;

    @NotBlank(message = "Il CAP è obbligatorio")
    @Pattern(regexp = "^\\s*[0-9]{5}\\s*$", message = "Il CAP deve avere 5 cifre")
    private String cap;

    @NotBlank(message = "La città è obbligatoria")
    @Size(max = 100, message = "Massimo 100 caratteri")
    private String citta;

    // Casella OBBLIGATORIA: «Ho letto l'informativa sulla privacy e accetto».
    @AssertTrue(message = "Per registrarti devi accettare l'informativa sulla privacy")
    private boolean accettoPrivacy;

    // Casella FACOLTATIVA: consenso alle comunicazioni promozionali.
    private boolean consensoMarketing;

    // Trappola anti-programmi automatici: deve restare vuota.
    private String sito;

    // Trasforma il modulo nei dati che RegistrazioneService si aspetta: il service non conosce questa classe (che appartiene alla pagina).
    public DatiRegistrazione comeDati() {
        return new DatiRegistrazione(nome, cognome, email, indirizzo, cap, citta, consensoMarketing);
    }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCognome() { return cognome; }
    public void setCognome(String cognome) { this.cognome = cognome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getIndirizzo() { return indirizzo; }
    public void setIndirizzo(String indirizzo) { this.indirizzo = indirizzo; }
    public String getCap() { return cap; }
    public void setCap(String cap) { this.cap = cap; }
    public String getCitta() { return citta; }
    public void setCitta(String citta) { this.citta = citta; }
    public boolean isAccettoPrivacy() { return accettoPrivacy; }
    public void setAccettoPrivacy(boolean accettoPrivacy) { this.accettoPrivacy = accettoPrivacy; }
    public boolean isConsensoMarketing() { return consensoMarketing; }
    public void setConsensoMarketing(boolean consensoMarketing) { this.consensoMarketing = consensoMarketing; }
    public String getSito() { return sito; }
    public void setSito(String sito) { this.sito = sito; }
}
