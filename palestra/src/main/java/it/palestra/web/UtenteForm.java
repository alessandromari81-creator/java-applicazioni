/*
 * FILE: UtenteForm.java
 * A COSA SERVE: contiene i dati che arrivano dal form HTML (nome, cognome, email, password, ruoli e corsi
 * scelti) e le regole di validazione. Non è un'entità: non finisce nel database.
 * PARAGONE LARAVEL: una FormRequest con il metodo rules(). Separare il form dall'entità evita che
 * l'utente possa modificare campi che non deve (es. l'id o l'hash della password).
 */
package it.palestra.web;

import it.palestra.model.Utente;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class UtenteForm {

    // @NotBlank = obbligatorio e non solo spazi. "message" è il testo dell'errore.
    // @Size(max = 255): le colonne del database sono VARCHAR(255); senza questo limite un testo più lungo
    // farebbe fallire l'INSERT con un errore 500.
    @NotBlank(message = "Il nome è obbligatorio")
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String nome;

    @NotBlank(message = "Il cognome è obbligatorio")
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String cognome;

    // Due regole insieme: obbligatoria e con formato email ('required|email'). È anche lo username del login.
    @NotBlank(message = "L'email è obbligatoria")
    @Email(message = "Email non valida")
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String email;

    // Password in chiaro SOLO mentre viaggia dal form al service, dove viene cifrata. Non ha regole qui perché
    // dipendono dai ruoli scelti (obbligatoria per gli ADMIN): le controlla UtenteService.erroreSullaPassword().
    private String password;

    // Indirizzo di casa: tutti facoltativi nel pannello (chi si registra da solo li compila nel modulo di registrazione).
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String indirizzo;

    @Size(max = 10, message = "Massimo 10 caratteri")
    private String cap;

    @Size(max = 100, message = "Massimo 100 caratteri")
    private String citta;

    // Gli id dei ruoli e dei corsi spuntati nelle checkbox (possono essere vuoti).
    private List<Long> ruoliIds = new ArrayList<>();
    private List<Long> corsiIds = new ArrayList<>();

    // Metodo "factory" statico: trasforma un Utente del database in un form precompilato (pagina di modifica).
    // La password NON si precompila mai: dal database esce solo l'hash, che non va mostrato.
    public static UtenteForm da(Utente u) {
        UtenteForm f = new UtenteForm();
        f.nome = u.getNome();
        f.cognome = u.getCognome();
        f.email = u.getEmail();
        f.indirizzo = u.getIndirizzo();
        f.cap = u.getCap();
        f.citta = u.getCitta();
        u.getRuoli().forEach(r -> f.ruoliIds.add(r.getId()));
        u.getCorsi().forEach(c -> f.corsiIds.add(c.getId()));
        return f;
    }

    // Getter e setter: Spring li usa per riempire l'oggetto con i dati del form (data binding) e Thymeleaf
    // per rimostrarli. Il nome del campo HTML deve coincidere col nome del campo qui.
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
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public List<Long> getRuoliIds() { return ruoliIds; }
    public void setRuoliIds(List<Long> ruoliIds) { this.ruoliIds = ruoliIds; }
    public List<Long> getCorsiIds() { return corsiIds; }
    public void setCorsiIds(List<Long> corsiIds) { this.corsiIds = corsiIds; }
}
