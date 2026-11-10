/*
 * FILE: TokenPassword.java
 * A COSA SERVE: una riga della tabella token_password (migration V12): un link per scegliere la password inviato per email a un utente già esistente.
 * Nel database c'è solo l'HASH SHA-256 del token (la stringa vera sta solo nell'email), con la scadenza. È MONOUSO: quando si usa la riga viene
 * cancellata. Se si elimina l'utente spariscono anche i suoi token (ON DELETE CASCADE).
 */
package it.palestra.model;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "token_password")
public class TokenPassword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // L'utente a cui appartiene il link. LAZY: si legge dal database solo quando serve.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Utente utente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoTokenPassword tipo;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant creatoIl;

    @Column(nullable = false)
    private Instant scadeIl;

    protected TokenPassword() {} // richiesto da JPA

    public TokenPassword(Utente utente, TipoTokenPassword tipo, String tokenHash, Instant creatoIl, Instant scadeIl) {
        this.utente = utente;
        this.tipo = tipo;
        this.tokenHash = tokenHash;
        this.creatoIl = creatoIl;
        this.scadeIl = scadeIl;
    }

    public Long getId() { return id; }
    public Utente getUtente() { return utente; }
    public TipoTokenPassword getTipo() { return tipo; }
    public String getTokenHash() { return tokenHash; }
    public Instant getCreatoIl() { return creatoIl; }
    public Instant getScadeIl() { return scadeIl; }
}
