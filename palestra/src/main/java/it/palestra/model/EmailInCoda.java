/*
 * FILE: EmailInCoda.java
 * A COSA SERVE: una riga della coda delle email (tabella email_in_coda, migration V14): un'email scritta ma non ancora spedita, con il suo stato e i
 * tentativi. La richiesta dell'utente scrive la riga (InvioEmailInCoda); il compito programmato SpedizioneEmailProgrammata la spedisce e, se il server di
 * posta non risponde, la riprova con attese crescenti. Contiene il LINK con il token vero: va cancellata appena l'email parte (vedi la migration).
 * Qui c'è solo la logica dello stato: cosa succede a una riga quando un invio fallisce. Non conosce né il database né il server di posta.
 */
package it.palestra.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "email_in_coda")
public class EmailInCoda {

    // Lunghezza massima del motivo dell'errore salvato (coincide con VARCHAR(255) della migration).
    private static final int LUNGHEZZA_ERRORE = 255;

    // Attesa massima tra due tentativi: l'attesa raddoppia a ogni fallimento (1, 2, 4, 8... minuti) ma non supera quest'ora.
    private static final long ATTESA_MASSIMA_MINUTI = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String destinatario;

    @Column(nullable = false)
    private String oggetto;

    // length alto perché il corpo è un testo lungo (nel database è TEXT); serve anche a far creare a H2 nei test una colonna abbastanza capiente.
    @Column(nullable = false, length = 100000)
    private String corpoHtml;

    @Column(nullable = false, length = 100000)
    private String corpoTesto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatoEmail stato;

    @Column(nullable = false)
    private int tentativi;

    @Column(nullable = false)
    private Instant prossimoTentativo;

    @Column
    private Instant inCorsoDal;

    @Column(length = LUNGHEZZA_ERRORE)
    private String ultimoErrore;

    @Column(nullable = false)
    private Instant creataIl;

    protected EmailInCoda() {}    // richiesto da JPA

    // Una email nuova: DA_INVIARE, nessun tentativo, spedibile subito (da "ora").
    public EmailInCoda(String destinatario, String oggetto, String corpoHtml, String corpoTesto, Instant ora) {
        this.destinatario = destinatario;
        this.oggetto = oggetto;
        this.corpoHtml = corpoHtml;
        this.corpoTesto = corpoTesto;
        this.stato = StatoEmail.DA_INVIARE;
        this.tentativi = 0;
        this.prossimoTentativo = ora;
        this.creataIl = ora;
    }

    // L'invio è fallito: conta il tentativo e ricorda il motivo. Se i tentativi non sono finiti torna DA_INVIARE con un'attesa crescente
    // (vedi attesaDopo); se hanno raggiunto il massimo diventa FALLITA e non si riprova più.
    public void registraFallimento(Instant ora, int massimoTentativi, String errore) {
        this.tentativi++;
        this.ultimoErrore = errore == null ? null : errore.substring(0, Math.min(errore.length(), LUNGHEZZA_ERRORE));
        this.inCorsoDal = null;
        if (this.tentativi >= massimoTentativi) {
            this.stato = StatoEmail.FALLITA;
        } else {
            this.stato = StatoEmail.DA_INVIARE;
            this.prossimoTentativo = ora.plus(attesaDopo(this.tentativi));
        }
    }

    // Quanto aspettare dopo il fallimento numero "tentativi": 1 minuto dopo il primo, 2 dopo il secondo, 4, 8, 16, 32 e poi al massimo 60.
    // È una funzione pura (static): si prova da sola, senza database.
    public static Duration attesaDopo(int tentativi) {
        long minuti = tentativi <= 0 ? 0 : (tentativi > 7 ? ATTESA_MASSIMA_MINUTI : Math.min(ATTESA_MASSIMA_MINUTI, 1L << (tentativi - 1)));
        return Duration.ofMinutes(minuti);
    }

    public Long getId() { return id; }
    public String getDestinatario() { return destinatario; }
    public String getOggetto() { return oggetto; }
    public String getCorpoHtml() { return corpoHtml; }
    public String getCorpoTesto() { return corpoTesto; }
    public StatoEmail getStato() { return stato; }
    public int getTentativi() { return tentativi; }
    public Instant getProssimoTentativo() { return prossimoTentativo; }
    public Instant getInCorsoDal() { return inCorsoDal; }
    public String getUltimoErrore() { return ultimoErrore; }
    public Instant getCreataIl() { return creataIl; }
}
