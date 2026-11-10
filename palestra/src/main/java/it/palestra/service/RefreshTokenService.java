/*
 * FILE: RefreshTokenService.java
 * A COSA SERVE: emette, valida, ruota e revoca i refresh token della API. È qui la logica di sicurezza dei rinnovi:
 *   - il token è una stringa CASUALE e imprevedibile (32 byte da un generatore sicuro), non un JWT;
 *   - nel database si salva solo il suo hash SHA-256;
 *   - ROTAZIONE: ogni volta che un token viene usato diventa non più valido e se ne emette uno nuovo;
 *   - RIUSO = sospetto furto: se si presenta un token già usato/revocato, si revocano TUTTI i token di quell'utente.
 */
package it.palestra.service;

import it.palestra.exception.RefreshTokenNonValidoException;
import it.palestra.model.RefreshToken;
import it.palestra.model.Utente;
import it.palestra.repository.RefreshTokenRepository;
import it.palestra.repository.UtenteRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RefreshTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);

    // SecureRandom = generatore di numeri casuali crittograficamente sicuro (random() normale è PREVEDIBILE: non va
    // mai usato per i segreti). È thread-safe, quindi se ne usa uno solo.
    private static final SecureRandom CASUALE = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final UtenteRepository utenti;
    private final Clock clock;
    private final Duration durata;

    public RefreshTokenService(RefreshTokenRepository repository, UtenteRepository utenti, Clock clock,
                               @Value("${app.jwt.refresh-durata-giorni}") long durataGiorni) {
        this.repository = repository;
        this.utenti = utenti;
        this.clock = clock;
        this.durata = Duration.ofDays(durataGiorni);
    }

    // Un refresh token appena emesso: il valore in chiaro (si vede UNA sola volta) e tra quanti secondi scade.
    public record Emesso(String token, long scadeTraSecondi) {}

    // Crea un nuovo refresh token per l'utente: genera la stringa casuale, salva l'hash e restituisce il token in chiaro.
    public Emesso emetti(Utente utente) {
        byte[] byteCasuali = new byte[32];                   // 32 byte = 256 bit: impossibile da indovinare
        CASUALE.nextBytes(byteCasuali);
        // Base64 "URL-safe" senza padding: solo lettere, numeri, "-" e "_", comodo dentro un JSON o un indirizzo.
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(byteCasuali);

        Instant ora = clock.instant();
        repository.save(new RefreshToken(utente, hash(token), ora, ora.plus(durata)));
        return new Emesso(token, durata.toSeconds());
    }

    // Usa un refresh token per un rinnovo: lo controlla, lo REVOCA (rotazione) e restituisce l'utente a cui appartiene.
    // Se non è valido lancia RefreshTokenNonValidoException (-> 401).
    //
    // noRollbackFor: di norma un'eccezione fa ANNULLARE la transazione (rollback). Qui no: nel caso del "riuso" prima
    // di lanciare l'eccezione abbiamo revocato tutti i token dell'utente, e quella revoca deve restare salvata. Senza
    // noRollbackFor verrebbe annullata insieme alla transazione, e il rilevamento del furto non servirebbe a niente.
    @Transactional(noRollbackFor = RefreshTokenNonValidoException.class)
    public Utente consuma(String token) {
        RefreshToken salvato = repository.findByTokenHash(hash(token))
                .orElseThrow(() -> rifiuta("token sconosciuto"));
        Instant ora = clock.instant();

        if (salvato.getRevocatoIl() != null) {
            // Già usato o revocato e qualcuno lo ripresenta: o è un errore del client, o il token è stato rubato (il ladro
            // e il proprietario ne hanno una copia ciascuno e uno dei due arriva per secondo). Per prudenza chiudiamo
            // TUTTE le sessioni dell'utente: dovrà rifare il login.
            Long utenteId = salvato.getUtente().getId();
            int revocati = repository.revocaTuttiDi(utenteId, ora);
            utenti.incrementaVersioneToken(utenteId);       // revoca anche gli access token già emessi (vedi ValidatoreRevocaToken)
            throw rifiuta("token già usato o revocato (possibile riuso): revocati tutti i token attivi dell'utente ("
                    + revocati + ") e i suoi access token");
        }
        if (!ora.isBefore(salvato.getScadeIl())) {
            throw rifiuta("token scaduto");
        }

        salvato.revoca(ora);                // ROTAZIONE: questo token non varrà più; ne verrà emesso uno nuovo
        return salvato.getUtente();
    }

    // Revoca un refresh token (logout). Silenziosa: se il token non esiste o è già revocato non fa nulla e non segnala
    // errori, così chi chiama non può usarla per scoprire quali token esistono.
    // Restituisce l'utente SOLO se il token era ancora attivo e l'ha revocato adesso: in quel caso il chiamante revoca anche gli
    // access token dell'utente. Se il token era già usato non si tocca niente (chi ha un vecchio token rubato non deve poter
    // far uscire l'utente dalle sue sessioni).
    public Optional<Utente> revoca(String token) {
        Optional<RefreshToken> trovato = repository.findByTokenHash(hash(token));
        if (trovato.isEmpty() || trovato.get().getRevocatoIl() != null) {
            return Optional.empty();
        }
        trovato.get().revoca(clock.instant());
        return Optional.of(trovato.get().getUtente());
    }

    // Revoca TUTTI i refresh token ancora attivi di un utente: serve quando cambia la sua password (chi avesse rubato un refresh
    // token non deve poter continuare a rinnovare l'accesso). Non svuota la memoria di Hibernate, così si può chiamare in mezzo
    // ad altre modifiche.
    public int revocaTuttiDi(Long utenteId) {
        return repository.revocaTuttiPerUtente(utenteId, clock.instant());
    }

    // PULIZIA: cancella i refresh token scaduti (vedi PuliziaProgrammata, che la esegue ogni notte). Restituisce quanti ne ha cancellati.
    public int eliminaScaduti() {
        return repository.eliminaScadutiPrima(clock.instant());
    }

    // Costruisce l'eccezione e scrive il MOTIVO nel log del server (mai nella risposta al client).
    private RefreshTokenNonValidoException rifiuta(String motivo) {
        LOG.warn("Rinnovo con refresh token rifiutato: {}", motivo);
        return new RefreshTokenNonValidoException(motivo);
    }

    // SHA-256 in esadecimale. È a senso unico: dall'hash non si risale al token. Nel database si cerca per hash.
    // (Per i token casuali e lunghi basta un hash veloce come SHA-256; per le password, che sono corte e indovinabili,
    // serve invece un hash LENTO come bcrypt.)
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);   // non succede: è presente in ogni JVM
        }
    }
}
