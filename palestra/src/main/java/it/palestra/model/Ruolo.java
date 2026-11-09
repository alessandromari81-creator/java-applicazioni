/*
 * FILE: Ruolo.java
 * A COSA SERVE: un ruolo che un utente può avere (ADMIN, ISTRUTTORE, ISCRITTO), tabella "ruolo".
 * I ruoli stanno nel database (e non in un enum Java) perché un utente può averne più d'uno e si possono
 * aggiungerne altri con una migration, senza ricompilare.
 * PARAGONE LARAVEL: il model Role di spatie/laravel-permission.
 */
package it.palestra.model;

import jakarta.persistence.*;

@Entity
public class Ruolo {

    // Nome del ruolo che dà accesso al pannello (costante per non scrivere "ADMIN" a mano in più punti).
    public static final String ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String nome;

    protected Ruolo() {} // richiesto da JPA

    public Ruolo(String nome) {
        this.nome = nome;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }

    // Due ruoli sono lo stesso se hanno lo stesso id (serve ai Set; vedi il commento in Corso).
    @Override
    public boolean equals(Object o) {
        return o instanceof Ruolo r && id != null && id.equals(r.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
