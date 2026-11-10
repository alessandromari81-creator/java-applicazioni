/*
 * FILE: SessioneService.java
 * A COSA SERVE: coordina le "sessioni" della API: login (email + password -> access token + refresh token), rinnovo (refresh
 * token -> nuova coppia di token) e logout (revoca dei token). Usa AutenticazioneService per le credenziali,
 * JwtService per l'access token e RefreshTokenService per il refresh token.
 */
package it.palestra.service;

import it.palestra.exception.RefreshTokenNonValidoException;
import it.palestra.model.Utente;
import it.palestra.repository.UtenteRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SessioneService {

    // La coppia di token restituita al client, con le due durate in secondi.
    public record Sessione(String accessToken, long accessScadeTraSecondi,
                           String refreshToken, long refreshScadeTraSecondi) {}

    private final AutenticazioneService autenticazione;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final UtenteRepository utenti;

    public SessioneService(AutenticazioneService autenticazione, JwtService jwt,
                           RefreshTokenService refreshTokens, UtenteRepository utenti) {
        this.autenticazione = autenticazione;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.utenti = utenti;
    }

    // LOGIN: controlla email e password (BadCredentialsException se sbagliate -> 401) ed emette i due token.
    public Sessione login(String email, String password) {
        autenticazione.autentica(email, password);
        Utente utente = utenti.findWithRuoliByEmailIgnoreCase(email)
                .orElseThrow(() -> new RefreshTokenNonValidoException("utente non trovato dopo l'autenticazione"));
        return emettiPer(utente);
    }

    // RINNOVO: con un refresh token valido emette una NUOVA coppia di token e invalida il refresh token usato (rotazione).
    // I ruoli del nuovo access token sono quelli ATTUALI dell'utente: se nel frattempo ha perso un ruolo, il nuovo token
    // non lo ha più. noRollbackFor: vedi RefreshTokenService.consuma (la revoca in caso di riuso deve restare salvata).
    @Transactional(noRollbackFor = RefreshTokenNonValidoException.class)
    public Sessione rinnova(String refreshToken) {
        Utente utente = refreshTokens.consuma(refreshToken);
        // Chi non ha più una password non può più accedere alla API (né rinnovare): stessa regola del login.
        if (utente.getPasswordHash() == null) {
            throw new RefreshTokenNonValidoException("l'utente non ha più una password");
        }
        return emettiPer(utente);
    }

    // LOGOUT: revoca il refresh token e, se era attivo, anche TUTTI gli access token già emessi per quell'utente (versione + 1: da quel
    // momento il controllo di ValidatoreRevocaToken li rifiuta). Effetto: dopo il logout l'access token non vale più subito, senza
    // aspettare i 60 minuti. Gli altri dispositivi dello stesso utente ricevono un 401 e si rinnovano col proprio refresh token.
    public void logout(String refreshToken) {
        refreshTokens.revoca(refreshToken).ifPresent(utente -> utente.revocaTokenAccesso());
    }

    // Emette access token (con i ruoli dell'utente) e refresh token.
    private Sessione emettiPer(Utente utente) {
        List<String> ruoli = utente.getRuoli().stream().map(ruolo -> ruolo.getNome()).sorted().toList();
        JwtService.TokenEmesso access = jwt.emetti(utente.getEmail(), ruoli, utente.getVersioneToken());
        RefreshTokenService.Emesso refresh = refreshTokens.emetti(utente);
        return new Sessione(access.token(), access.scadeTraSecondi(), refresh.token(), refresh.scadeTraSecondi());
    }
}
