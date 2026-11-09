/*
 * FILE: CorsoForm.java
 * A COSA SERVE: dati che arrivano dal form di un corso e regole di validazione (come una
 * FormRequest di Laravel). Non è un'entità: non finisce nel database.
 */
package it.palestra.web;

import it.palestra.model.Corso;
import it.palestra.model.Livello;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CorsoForm {

    // Limite 255 = lunghezza delle colonne VARCHAR del database (vedi UtenteForm).
    @NotBlank(message = "Il nome è obbligatorio")
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String nome;

    // Facoltativa (niente @NotBlank = 'nullable' di Laravel), ma con il limite di lunghezza.
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String descrizione;

    // Integer (con la maiuscola) e non int: può essere null se il campo è vuoto, così
    // @NotNull può dare il messaggio. Con int il valore vuoto darebbe un errore di conversione.
    @NotNull(message = "La durata è obbligatoria")
    @Min(value = 1, message = "La durata deve essere almeno 1 minuto")
    private Integer durataMinuti;

    // Se si sceglie un valore non valido, Spring segnala l'errore da solo.
    @NotNull(message = "Il livello è obbligatorio")
    private Livello livello;

    // Trasforma un Corso del database in un form precompilato (pagina di modifica).
    public static CorsoForm da(Corso c) {
        CorsoForm f = new CorsoForm();
        f.nome = c.getNome();
        f.descrizione = c.getDescrizione();
        f.durataMinuti = c.getDurataMinuti();
        f.livello = c.getLivello();
        return f;
    }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescrizione() { return descrizione; }
    public void setDescrizione(String descrizione) { this.descrizione = descrizione; }
    public Integer getDurataMinuti() { return durataMinuti; }
    public void setDurataMinuti(Integer durataMinuti) { this.durataMinuti = durataMinuti; }
    public Livello getLivello() { return livello; }
    public void setLivello(Livello livello) { this.livello = livello; }
}
