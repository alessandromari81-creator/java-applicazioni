/*
 * FILE: RefreshToken.java
 * A COSA SERVE: un "refresh token" emesso a un utente (tabella "refresh_token"). Serve a ottenere un nuovo access token
 * quando quello vecchio scade, senza rifare il login. Si salva solo l'HASH del token (mai il token), con le date di
 * creazione, scadenza ed eventuale revoca.
 * PARAGONE LARAVEL: una riga di personal_access_tokens (Sanctum).
 */
package it.palestra.model;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Di chi è il token. @OnDelete(CASCADE) dice a Hibernate (e alla tabella creata nei test) che eliminando l'utente il
    // DATABASE elimina da solo i suoi token: coincide con "ON DELETE CASCADE" della migration V7.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Utente utente;

    // Hash SHA-256 del token (64 caratteri esadecimali). Il token in chiaro non si salva mai.
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    // Instant = un istante preciso sull'asse del tempo (indipendente dal fuso orario); nel database timestamptz.
    @Column(nullable = false)
    private Instant creatoIl;

    @Column(nullable = false)
    private Instant scadeIl;

    // null = utilizzabile. Si valorizza quando il token viene USATO (rotazione) o REVOCATO (logout, sospetto furto).
    private Instant revocatoIl;

    protected RefreshToken() {} // richiesto da JPA

    public RefreshToken(Utente utente, String tokenHash, Instant creatoIl, Instant scadeIl) {
        this.utente = utente;
        this.tokenHash = tokenHash;
        this.creatoIl = creatoIl;
        this.scadeIl = scadeIl;
    }

    // Segna il token come non più utilizzabile (ricorda quando).
    public void revoca(Instant quando) {
        this.revocatoIl = quando;
    }

    public Long getId() { return id; }
    public Utente getUtente() { return utente; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCreatoIl() { return creatoIl; }
    public Instant getScadeIl() { return scadeIl; }
    public Instant getRevocatoIl() { return revocatoIl; }
}
