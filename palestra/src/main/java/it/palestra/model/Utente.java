/*
 * FILE: Utente.java
 * A COSA SERVE: rappresenta una persona iscritta alla palestra (tabella "utente") e
 * sa quali corsi frequenta.
 * PARAGONE LARAVEL: Model `Utente` con la relazione belongsToMany(Corso::class).
 * NON è l'admin che fa login: quello è definito in SecurityConfig.
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

    // unique = true -> indice UNIQUE nel DB: due utenti non possono avere la stessa email.
    @Column(nullable = false, unique = true)
    private String email;

    // Relazione molti-a-molti: un utente ha molti corsi, un corso ha molti utenti.
    // @JoinTable descrive la tabella pivot "utente_corso" con le due colonne che puntano
    // alle due tabelle (in Laravel: $this->belongsToMany(Corso::class, 'utente_corso')).
    // Utente è il lato "proprietario": per assegnare/togliere corsi si modifica questo Set.
    // Set = collezione senza duplicati (un utente non può avere due volte lo stesso corso).
    @ManyToMany
    @JoinTable(name = "utente_corso",
            joinColumns = @JoinColumn(name = "utente_id"),
            inverseJoinColumns = @JoinColumn(name = "corso_id"))
    private Set<Corso> corsi = new HashSet<>();

    // Getter e setter: leggono/scrivono i campi privati. Spring/Thymeleaf li usano
    // automaticamente (non c'è un id setter: l'id lo assegna il database).
    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCognome() { return cognome; }
    public void setCognome(String cognome) { this.cognome = cognome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Set<Corso> getCorsi() { return corsi; }
    public void setCorsi(Set<Corso> corsi) { this.corsi = corsi; }
}
