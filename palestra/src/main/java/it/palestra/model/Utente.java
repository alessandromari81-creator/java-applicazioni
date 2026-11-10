/*
 * FILE: Utente.java
 * A COSA SERVE: rappresenta una persona (tabella "utente"): un amministratore del pannello, un istruttore
 * o un iscritto alla palestra. Cosa può fare lo decidono i suoi RUOLI. Sa anche quali corsi frequenta.
 * PARAGONE LARAVEL: il model User, con le relazioni belongsToMany(Role) e belongsToMany(Corso).
 */
package it.palestra.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Utente {

    // Chiave primaria auto-increment.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String cognome;

    // unique = true -> indice UNIQUE nel DB. L'email è anche lo username per il login.
    @Column(nullable = false, unique = true)
    private String email;

    // Hash bcrypt della password, mai la password in chiaro. Può essere null: chi non
    // deve accedere al pannello (iscritti, istruttori) non ha password e quindi non può fare login.
    @Column
    private String passwordHash;

    // Numero di "versione" dei token di accesso (colonna versione_token, migration V8). Ogni JWT emesso contiene il valore che questo
    // campo aveva in quel momento; aumentandolo (revocaTokenAccesso) tutti i JWT già emessi per questo utente smettono di valere.
    @Column(nullable = false)
    private int versioneToken;

    // Relazione molti-a-molti con i corsi: tabella pivot "utente_corso".
    // Utente è il lato "proprietario": per iscrivere/disiscrivere si modifica questo Set.
    // Set = collezione senza duplicati.
    @ManyToMany
    @JoinTable(name = "utente_corso",
            joinColumns = @JoinColumn(name = "utente_id"),
            inverseJoinColumns = @JoinColumn(name = "corso_id"))
    private Set<Corso> corsi = new HashSet<>();

    // Relazione molti-a-molti con i ruoli: pivot "utente_ruolo". Un utente può averne più d'uno.
    @ManyToMany
    @JoinTable(name = "utente_ruolo",
            joinColumns = @JoinColumn(name = "utente_id"),
            inverseJoinColumns = @JoinColumn(name = "ruolo_id"))
    private Set<Ruolo> ruoli = new HashSet<>();

    // CORSI CHE L'UTENTE TIENE COME ISTRUTTORE (relazione UNO-A-MOLTI: un istruttore tiene più corsi).
    // mappedBy = "istruttore": la colonna vera (istruttore_id) sta nella tabella corso ed è gestita dal campo
    // "istruttore" della classe Corso; qui è solo il lato "di lettura" della stessa relazione.
    // Per assegnare un istruttore si imposta
    // corso.setIstruttore(utente), NON si aggiunge a questo Set. Resta vuoto per chi non è istruttore.
    @OneToMany(mappedBy = "istruttore")
    private Set<Corso> corsiInsegnati = new HashSet<>();

    // Getter e setter: leggono/scrivono i campi privati (l'id lo assegna il database, quindi niente setter).
    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCognome() { return cognome; }
    public void setCognome(String cognome) { this.cognome = cognome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Set<Corso> getCorsi() { return corsi; }
    public void setCorsi(Set<Corso> corsi) { this.corsi = corsi; }
    public int getVersioneToken() { return versioneToken; }

    // Revoca in un colpo solo tutti i token di accesso (JWT) già emessi per questo utente: da usare su un'entità GESTITA da
    // Hibernate (dentro una transazione), così l'UPDATE parte da solo a fine transazione.
    public void revocaTokenAccesso() { this.versioneToken++; }

    public Set<Ruolo> getRuoli() { return ruoli; }
    public void setRuoli(Set<Ruolo> ruoli) { this.ruoli = ruoli; }
    public Set<Corso> getCorsiInsegnati() { return corsiInsegnati; }

    // Comodo nei template e nel service: true se l'utente ha il ruolo ADMIN.
    public boolean isAdmin() {
        return ruoli.stream().anyMatch(r -> Ruolo.ADMIN.equals(r.getNome()));
    }

    // True se l'utente ha il ruolo ISCRITTO (quindi può avere degli abbonamenti).
    public boolean isIscritto() {
        return ruoli.stream().anyMatch(r -> Ruolo.ISCRITTO.equals(r.getNome()));
    }

    // True se l'utente ha il ruolo ISTRUTTORE (quindi può essere assegnato come istruttore di un corso).
    public boolean isIstruttore() {
        return ruoli.stream().anyMatch(r -> Ruolo.ISTRUTTORE.equals(r.getNome()));
    }
}
