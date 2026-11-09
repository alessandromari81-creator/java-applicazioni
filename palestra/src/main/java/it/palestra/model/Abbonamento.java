/*
 * FILE: Abbonamento.java
 * A COSA SERVE: un abbonamento sottoscritto da un iscritto (tabella "abbonamento"): quale tipo, da quando a
 * quando, a che prezzo. Un utente ne ha molti nel tempo (rinnovi).
 * PARAGONE LARAVEL: un model Eloquent con belongsTo(Utente). Qui però il "calcolo" (data di fine, prezzo, stato)
 * sta DENTRO la classe, vicino ai dati: è la differenza tra un "modello ricco" e uno che contiene solo colonne.
 */
package it.palestra.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
public class Abbonamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // L'iscritto a cui appartiene. MOLTI abbonamenti -> UN utente (come belongsTo in Laravel).
    // LAZY = l'utente si legge dal database solo quando serve (la lista lo carica subito con @EntityGraph).
    // optional = false: un abbonamento senza utente non esiste (la colonna utente_id è NOT NULL).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id", nullable = false)
    private Utente utente;

    // Testo nel database (MENSILE...), come per Corso.livello. length = 20 come la colonna.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAbbonamento tipo;

    // LocalDate = una data senza ora (come Carbon::today()); nel database una colonna DATE.
    // Nomi colonna: data_inizio e data_fine (camelCase -> snake_case automatico).
    @Column(nullable = false)
    private LocalDate dataInizio;

    // Ultimo giorno VALIDO (incluso). Si calcola alla creazione dal tipo, non lo sceglie l'utente.
    @Column(nullable = false)
    private LocalDate dataFine;

    // BigDecimal = numero decimale esatto, il tipo giusto per i soldi (double/float sbagliano gli arrotondamenti).
    // precision 8, scale 2 = fino a 6 cifre intere e 2 decimali, come NUMERIC(8,2) nel database.
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal prezzo;

    protected Abbonamento() {} // richiesto da JPA

    // Crea un abbonamento: servono solo utente, tipo e data di inizio. La data di fine e il prezzo si ricavano dal
    // tipo, così non possono mai essere incoerenti con esso. Il prezzo di listino di OGGI viene copiato qui
    // dentro e non cambia più (anche se in futuro il listino aumenta).
    // Questa classe NON controlla le regole di business (niente passato, niente sovrapposizioni, solo iscritti):
    // quelle sono nel service, che ha accesso al database e alla data di oggi.
    public Abbonamento(Utente utente, TipoAbbonamento tipo, LocalDate dataInizio) {
        this.utente = utente;
        this.tipo = tipo;
        this.dataInizio = dataInizio;
        this.dataFine = tipo.calcolaFine(dataInizio);
        this.prezzo = tipo.getPrezzoListino();
    }

    // Stato rispetto alla data "oggi" (non salvato: vedi StatoAbbonamento). Gli estremi sono INCLUSI: nel giorno
    // di inizio e nel giorno di fine l'abbonamento è ancora ATTIVO.
    public StatoAbbonamento stato(LocalDate oggi) {
        if (oggi.isBefore(dataInizio)) return StatoAbbonamento.FUTURO;
        if (oggi.isAfter(dataFine)) return StatoAbbonamento.SCADUTO;
        return StatoAbbonamento.ATTIVO;
    }

    public Long getId() { return id; }
    public Utente getUtente() { return utente; }
    public TipoAbbonamento getTipo() { return tipo; }
    public LocalDate getDataInizio() { return dataInizio; }
    public LocalDate getDataFine() { return dataFine; }
    public BigDecimal getPrezzo() { return prezzo; }
}
