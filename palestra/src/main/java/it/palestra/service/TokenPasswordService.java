/*
 * FILE: TokenPasswordService.java
 * A COSA SERVE: emette e controlla i link per scegliere la password inviati per email a utenti già esistenti («password dimenticata» e invito
 * dell'amministratore). Regole:
 *   - il token è casuale e nel database c'è solo il suo hash;
 *   - scade (RESET: app.token.reset-minuti, ATTIVAZIONE: app.token.attivazione-ore);
 *   - è MONOUSO: quando si usa la riga viene cancellata, insieme a tutti gli altri link di quell'utente;
 *   - per ogni utente e tipo ne esiste uno solo: una nuova richiesta sostituisce la precedente (il vecchio link smette di funzionare);
 *   - se ne è appena stato emesso uno (meno di app.token.pausa-minuti) non se ne emette un altro: non si rispedisce l'email a raffica.
 * Il token vero esce da qui una sola volta, per finire nell'email: non si può più ricavare dal database.
 */
package it.palestra.service;

import it.palestra.exception.TokenNonValidoException;
import it.palestra.model.TipoTokenPassword;
import it.palestra.model.TokenPassword;
import it.palestra.model.Utente;
import it.palestra.repository.TokenPasswordRepository;
import it.palestra.repository.UtenteRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TokenPasswordService {

    private final TokenPasswordRepository tokens;
    private final UtenteRepository utenti;
    private final Clock clock;
    private final Duration durataReset;
    private final Duration durataAttivazione;
    private final Duration pausa;

    public TokenPasswordService(TokenPasswordRepository tokens, UtenteRepository utenti, Clock clock,
                                @Value("${app.token.reset-minuti}") long resetMinuti,
                                @Value("${app.token.attivazione-ore}") long attivazioneOre,
                                @Value("${app.token.pausa-minuti}") long pausaMinuti) {
        this.tokens = tokens;
        this.utenti = utenti;
        this.clock = clock;
        this.durataReset = Duration.ofMinutes(resetMinuti);
        this.durataAttivazione = Duration.ofHours(attivazioneOre);
        this.pausa = Duration.ofMinutes(pausaMinuti);
    }

    // Emette un nuovo token del tipo indicato per l'utente (sostituendo quello precedente dello stesso tipo) e lo restituisce: è la stringa da mettere nel link.
    public String emetti(Long utenteId, TipoTokenPassword tipo) {
        Utente utente = utenti.getReferenceById(utenteId);
        tokens.eliminaDiUtenteETipo(utenteId, tipo);
        String token = GeneratoreToken.nuovo();
        Instant ora = clock.instant();
        Duration durata = tipo == TipoTokenPassword.RESET ? durataReset : durataAttivazione;
        tokens.save(new TokenPassword(utente, tipo, GeneratoreToken.hash(token), ora, ora.plus(durata)));
        return token;
    }

    // true se per questo utente e tipo è già stato emesso un token da meno del tempo di pausa (quindi non va rispedita un'altra email).
    @Transactional(readOnly = true)
    public boolean inPausa(Long utenteId, TipoTokenPassword tipo) {
        return tokens.findFirstByUtenteIdAndTipoOrderByCreatoIlDesc(utenteId, tipo)
                .map(t -> t.getCreatoIl().plus(pausa).isAfter(clock.instant()))
                .orElse(false);
    }

    // Il token se esiste ed è ancora valido (non scaduto); vuoto altrimenti. NON lo consuma: serve a mostrare il modulo senza bruciare il link
    // (alcuni programmi di posta aprono i link in anteprima: se bastasse aprirlo, il link sarebbe già inutilizzabile quando l'utente ci clicca).
    @Transactional(readOnly = true)
    public Optional<TokenPassword> trovaValido(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(GeneratoreToken.hash(token.trim()))
                .filter(t -> t.getScadeIl().isAfter(clock.instant()));
    }

    // Usa il token: se è valido restituisce l'utente a cui appartiene e CANCELLA tutti i link di quell'utente; altrimenti TokenNonValidoException.
    public Utente consuma(String token) {
        TokenPassword valido = trovaValido(token).orElseThrow(TokenNonValidoException::new);
        Utente utente = valido.getUtente();
        tokens.eliminaDiUtente(utente.getId());
        return utente;
    }

    // Pulizia notturna: cancella i token scaduti. Restituisce quanti.
    public int eliminaScaduti() {
        return tokens.eliminaScadutiPrima(clock.instant());
    }
}
