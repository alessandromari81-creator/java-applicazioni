/*
 * FILE: PasswordService.java
 * A COSA SERVE: tutto ciò che riguarda la password di un utente già esistente, separato da UtenteService (che gestisce le schede):
 * confrontare la password scritta con quella salvata e impostarne una nuova. Lo usano il cambio password dell'account, il recupero
 * della password e la conferma della registrazione.
 * Nel database finisce sempre e solo l'hash bcrypt, mai il testo.
 */
package it.palestra.service;

import it.palestra.exception.RisorsaNonTrovataException;
import it.palestra.model.Utente;
import it.palestra.repository.UtenteRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PasswordService {

    private final UtenteRepository utenti;
    private final PasswordEncoder encoder;
    private final RefreshTokenService refreshTokens;

    // Dependency injection: Spring passa il repository, l'encoder (bcrypt) e il servizio dei refresh token.
    public PasswordService(UtenteRepository utenti, PasswordEncoder encoder, RefreshTokenService refreshTokens) {
        this.utenti = utenti;
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
    }

    // true se la password scritta corrisponde a quella dell'utente con questa email (si confrontano gli hash: la password in chiaro non è
    // salvata da nessuna parte). Una password oltre i 72 byte non può essere quella giusta (bcrypt non ne accetta di più): si risponde
    // false invece di far lanciare un errore a bcrypt.
    @Transactional(readOnly = true)
    public boolean passwordCorrisponde(String email, String password) {
        if (!RegolePassword.entraNelLimiteDiBcrypt(password)) return false;
        return utenti.findByEmailIgnoreCase(email)
                .map(u -> u.getPasswordHash() != null && encoder.matches(password, u.getPasswordHash()))
                .orElse(false);
    }

    // Imposta la nuova password: nel database finisce solo l'hash bcrypt. Basta cambiare il campo: a fine transazione Hibernate fa
    // l'UPDATE da solo. Il controllo di quella attuale lo fa il controller PRIMA di arrivare qui (con passwordCorrisponde).
    public void cambiaPassword(String email, String nuovaPassword) {
        Utente u = utenti.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RisorsaNonTrovataException("Utente " + email + " non trovato"));
        u.setPasswordHash(encoder.encode(nuovaPassword));
        u.revocaTokenAccesso();      // i token di accesso emessi con la vecchia password non valgono più...
        refreshTokens.revocaTuttiDi(u.getId());    // ...e nemmeno i refresh token: nessuno può più rinnovare l'accesso con la vecchia
    }
}
