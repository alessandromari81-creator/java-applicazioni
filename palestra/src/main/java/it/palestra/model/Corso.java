/*
 * FILE: Corso.java
 * A COSA SERVE: rappresenta un corso della palestra (Yoga, Spinning...) ed è mappato
 * sulla tabella "corso" del database.
 * Le colonne della tabella sono dichiarate come campi della classe (JPA/Hibernate parte dalla classe e la collega alla tabella).
 *
 * RELAZIONI DEL CORSO CON GLI UTENTI (sono due, diverse, e non vanno confuse):
 *  - ISTRUTTORE (questa classe, campo "istruttore"): chi TIENE il corso. Uno solo, facoltativo.
 *  - ISCRITTI (nella classe Utente, campo "corsi"): chi FREQUENTA il corso. Molti.
 * REGOLA: lo stesso utente non può essere insieme istruttore e iscritto dello stesso corso.
 */
package it.palestra.model;

import jakarta.persistence.*;

// @Entity = "questa classe corrisponde a una tabella" (la tabella si chiama "corso").
@Entity
public class Corso {

    // @Id = chiave primaria. IDENTITY = auto-increment gestito dal database.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // nullable = false -> colonna NOT NULL.
    @Column(nullable = false)
    private String nome;

    private String descrizione;

    // Durata in minuti. "int" è un numero intero.
    private int durataMinuti;

    // Salva nel DB il NOME dell'enum ("YOGA"...) come testo, non la posizione numerica
    // (con ORDINAL basterebbe cambiare l'ordine dell'enum per rovinare i dati).
    @Enumerated(EnumType.STRING)
    private Livello livello;

    // ISTRUTTORE del corso: un utente con ruolo ISTRUTTORE. Relazione MOLTI-A-UNO: molti corsi possono avere lo
    // stesso istruttore (e lato Utente diventa "uno-a-molti": un istruttore tiene più corsi).
    // @JoinColumn = la colonna istruttore_id della tabella corso (chiave esterna verso utente).
    // LAZY = l'istruttore si legge dal database solo quando serve (per questo alcuni repository lo caricano
    // subito con @EntityGraph). Può essere null: corso senza istruttore.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "istruttore_id")
    private Utente istruttore;

    // Costruttore vuoto richiesto da JPA: lo usa Hibernate per creare gli oggetti
    // quando legge dal DB. "protected" = non lo usiamo noi dal resto del codice.
    protected Corso() {}

    // Costruttore "normale" che usiamo noi per creare un corso (es. in CorsoService.crea).
    public Corso(String nome, String descrizione, int durataMinuti, Livello livello) {
        this.nome = nome;               // this = l'oggetto che si sta creando
        this.descrizione = descrizione;
        this.durataMinuti = durataMinuti;
        this.livello = livello;
    }

    // Getter: metodi per LEGGERE i campi privati.
    // Thymeleaf li usa nei template: ${c.nome} chiama getNome().
    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getDescrizione() { return descrizione; }
    public int getDurataMinuti() { return durataMinuti; }
    public Livello getLivello() { return livello; }

    // Setter: servono per la MODIFICA di un corso esistente (l'id invece non si cambia mai).
    public void setNome(String nome) { this.nome = nome; }
    public void setDescrizione(String descrizione) { this.descrizione = descrizione; }
    public void setDurataMinuti(int durataMinuti) { this.durataMinuti = durataMinuti; }
    public void setLivello(Livello livello) { this.livello = livello; }

    public Utente getIstruttore() { return istruttore; }
    public void setIstruttore(Utente istruttore) { this.istruttore = istruttore; }

    // equals/hashCode: definiscono quando due oggetti Corso sono "lo stesso corso".
    // Servono perché i corsi stanno in un Set (insieme senza duplicati): due Corso con
    // lo stesso id sono lo stesso corso. Senza questo, Java confronterebbe gli indirizzi
    // in memoria e potrebbe considerare diversi due oggetti uguali caricati in momenti diversi.
    @Override
    public boolean equals(Object o) {
        return o instanceof Corso c && id != null && id.equals(c.id);
    }

    // hashCode costante: scelta voluta per le entità JPA, perché l'id cambia
    // (da null a un numero) quando l'oggetto viene salvato la prima volta.
    @Override
    public int hashCode() { return getClass().hashCode(); }
}
