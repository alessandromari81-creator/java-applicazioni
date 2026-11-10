/*
 * FILE: RegistrazioneInAttesa.java
 * A COSA SERVE: una riga della tabella registrazione_in_attesa (migration V13): i dati di chi si è registrato da solo ma NON ha ancora confermato l'email.
 * Finché non clicca il link non esiste nessun Utente: quando conferma e sceglie la password si crea l'Utente e questa riga si cancella (il link è monouso).
 * L'email è unica e sempre in minuscolo: una sola registrazione in attesa per indirizzo, un nuovo invio la sostituisce (il vecchio link smette di valere).
 * Il token vero sta solo nell'email: qui c'è l'hash SHA-256.
 */
package it.palestra.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "registrazione_in_attesa")
public class RegistrazioneInAttesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String cognome;

    @Column(nullable = false)
    private String indirizzo;

    @Column(nullable = false, length = 10)
    private String cap;

    @Column(nullable = false, length = 100)
    private String citta;

    // Consensi dati nel modulo: accettazione dell'informativa (sempre presente, è obbligatoria) e consenso facoltativo alle comunicazioni promozionali.
    @Column(nullable = false)
    private Instant privacyAccettataIl;

    @Column(nullable = false, length = 20)
    private String privacyVersione;

    @Column
    private Instant consensoMarketingIl;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant creatoIl;

    @Column(nullable = false)
    private Instant ultimoInvioIl;

    @Column(nullable = false)
    private Instant scadeIl;

    protected RegistrazioneInAttesa() {} // richiesto da JPA

    public RegistrazioneInAttesa(String email) {
        this.email = email;
    }

    // Imposta (o sostituisce) i dati della registrazione e il token del link: si usa sia alla prima richiesta sia quando la persona rimanda il modulo.
    public void compila(String nome, String cognome, String indirizzo, String cap, String citta,
                        Instant privacyAccettataIl, String privacyVersione, Instant consensoMarketingIl,
                        String tokenHash, Instant adesso, Instant scadeIl) {
        this.nome = nome;
        this.cognome = cognome;
        this.indirizzo = indirizzo;
        this.cap = cap;
        this.citta = citta;
        this.privacyAccettataIl = privacyAccettataIl;
        this.privacyVersione = privacyVersione;
        this.consensoMarketingIl = consensoMarketingIl;
        this.tokenHash = tokenHash;
        if (this.creatoIl == null) {
            this.creatoIl = adesso;
        }
        this.ultimoInvioIl = adesso;
        this.scadeIl = scadeIl;
    }

    // Aggiorna SOLO i dati inseriti nel modulo e i consensi, lasciando com'erano il token del link, la scadenza e l'ora dell'ultimo invio. Serve quando la persona
    // rimanda il modulo subito dopo (per correggere un dato) e l'email non va rispedita: il primo link resta valido, ma l'account nascerà con i dati dell'ULTIMO invio.
    public void aggiornaDati(String nome, String cognome, String indirizzo, String cap, String citta,
                             Instant privacyAccettataIl, String privacyVersione, Instant consensoMarketingIl) {
        this.nome = nome;
        this.cognome = cognome;
        this.indirizzo = indirizzo;
        this.cap = cap;
        this.citta = citta;
        this.privacyAccettataIl = privacyAccettataIl;
        this.privacyVersione = privacyVersione;
        this.consensoMarketingIl = consensoMarketingIl;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getNome() { return nome; }
    public String getCognome() { return cognome; }
    public String getIndirizzo() { return indirizzo; }
    public String getCap() { return cap; }
    public String getCitta() { return citta; }
    public Instant getPrivacyAccettataIl() { return privacyAccettataIl; }
    public String getPrivacyVersione() { return privacyVersione; }
    public Instant getConsensoMarketingIl() { return consensoMarketingIl; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCreatoIl() { return creatoIl; }
    public Instant getUltimoInvioIl() { return ultimoInvioIl; }
    public Instant getScadeIl() { return scadeIl; }
}
