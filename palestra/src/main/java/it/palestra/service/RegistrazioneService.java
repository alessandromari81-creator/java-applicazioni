/*
 * FILE: RegistrazioneService.java
 * A COSA SERVE: la registrazione libera in due tempi.
 *   1) richiedi(modulo): la persona compila il modulo. NON si crea nessun utente: i dati si salvano in "registrazione_in_attesa" e parte un'email con il link
 *      di conferma. Una sola registrazione in attesa per indirizzo: un nuovo invio la sostituisce (il vecchio link smette di valere); se l'email è
 *      partita da pochi minuti non ne parte un'altra, ma i dati si aggiornano comunque a quelli dell'ultimo invio (il primo link resta valido). Se l'indirizzo ha già un account con password si invia invece un avviso «hai già un account».
 *   2) conferma(link, password): aperto il link e scelta la password, si crea l'Utente con ruolo ISCRITTO e la registrazione in attesa si cancella (monouso).
 *      Se nel frattempo esiste una scheda con quell'email creata dall'amministratore SENZA password, la registrazione la ATTIVA (imposta la password e i
 *      consensi, completa l'indirizzo se mancava) senza cambiare nome e cognome e mantenendo corsi e abbonamenti già registrati.
 * Perché la tabella a parte: così «utente» contiene solo persone vere e un indirizzo sbagliato (o di qualcun altro) non «occupa» l'email di nessuno.
 * Il chiamante (controller) risponde SEMPRE nello stesso modo a richiedi(), qualunque cosa sia successa: non si rivela se un'email è già registrata.
 */
package it.palestra.service;

import it.palestra.exception.RegolaViolataException;
import it.palestra.exception.TokenNonValidoException;
import it.palestra.model.RegistrazioneInAttesa;
import it.palestra.model.Ruolo;
import it.palestra.model.Utente;
import it.palestra.repository.RegistrazioneInAttesaRepository;
import it.palestra.repository.RuoloRepository;
import it.palestra.repository.UtenteRepository;
import it.palestra.web.RegistrazioneForm;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RegistrazioneService {

    // Com'è finita la conferma: account nuovo creato, scheda dell'amministratore attivata, oppure l'account esisteva già con una password.
    public enum Esito { CREATO, ATTIVATO, GIA_ESISTENTE }

    private final RegistrazioneInAttesaRepository registrazioni;
    private final UtenteRepository utenti;
    private final RuoloRepository ruoli;
    private final PasswordEncoder encoder;
    private final NotificheEmail notifiche;
    private final UtenteService utenteService;
    private final Clock clock;
    private final TransactionTemplate transazione;
    private final Duration durata;
    private final Duration pausa;
    private final String versionePrivacy;

    public RegistrazioneService(RegistrazioneInAttesaRepository registrazioni, UtenteRepository utenti, RuoloRepository ruoli,
                                PasswordEncoder encoder, NotificheEmail notifiche, UtenteService utenteService, Clock clock,
                                PlatformTransactionManager gestoreTransazioni,
                                @Value("${app.token.registrazione-ore}") long durataOre,
                                @Value("${app.token.pausa-minuti}") long pausaMinuti,
                                @Value("${app.privacy.versione}") String versionePrivacy) {
        this.registrazioni = registrazioni;
        this.utenti = utenti;
        this.ruoli = ruoli;
        this.encoder = encoder;
        this.notifiche = notifiche;
        this.utenteService = utenteService;
        this.clock = clock;
        this.transazione = new TransactionTemplate(gestoreTransazioni);
        this.durata = Duration.ofHours(durataOre);
        this.pausa = Duration.ofMinutes(pausaMinuti);
        this.versionePrivacy = versionePrivacy;
    }

    // Primo tempo: salva (o sostituisce) la registrazione in attesa e invia l'email di conferma. Non è @Transactional: la registrazione si salva e si
    // conferma PRIMA di spedire l'email, così il link funziona subito (per questo si usa un TransactionTemplate solo per il salvataggio).
    public void richiedi(RegistrazioneForm modulo) {
        String email = modulo.getEmail().trim().toLowerCase(Locale.ROOT);

        Optional<Utente> esistente = utenti.findByEmailIgnoreCase(email);
        if (esistente.isPresent() && esistente.get().isConPassword()) {
            notifiche.accountGiaEsistente(email, esistente.get().getNome());      // niente conferma: solo il modo di recuperare l'accesso
            return;
        }

        String token = transazione.execute(stato -> salvaInAttesa(modulo, email));
        if (token != null) {
            notifiche.confermaRegistrazione(email, modulo.getNome().trim(), token);
        }
    }

    // Salva la registrazione in attesa e restituisce il token da mettere nel link; null se per questo indirizzo è appena partita un'altra email
    // (meno di app.token.pausa-minuti fa): in quel caso NON si rispedisce l'email e il primo link resta valido, ma i dati della registrazione si aggiornano
    // comunque a quelli dell'ultimo invio (la persona potrebbe aver rimandato il modulo per correggere un errore, ad esempio nel cognome).
    private String salvaInAttesa(RegistrazioneForm modulo, String email) {
        Instant ora = clock.instant();
        RegistrazioneInAttesa registrazione = registrazioni.findByEmail(email).orElse(null);
        if (registrazione != null && registrazione.getUltimoInvioIl().plus(pausa).isAfter(ora)) {
            registrazione.aggiornaDati(modulo.getNome().trim(), modulo.getCognome().trim(), modulo.getIndirizzo().trim(),
                    modulo.getCap().trim(), modulo.getCitta().trim(), ora, versionePrivacy,
                    modulo.isConsensoMarketing() ? ora : null);
            registrazioni.save(registrazione);
            return null;
        }
        if (registrazione == null) {
            registrazione = new RegistrazioneInAttesa(email);
        }
        String token = GeneratoreToken.nuovo();
        registrazione.compila(modulo.getNome().trim(), modulo.getCognome().trim(), modulo.getIndirizzo().trim(),
                modulo.getCap().trim(), modulo.getCitta().trim(), ora, versionePrivacy,
                modulo.isConsensoMarketing() ? ora : null, GeneratoreToken.hash(token), ora, ora.plus(durata));
        registrazioni.save(registrazione);
        return token;
    }

    // La registrazione in attesa del link, se esiste ed è ancora valida. NON la consuma (serve a mostrare il modulo della password senza bruciare il link).
    @Transactional(readOnly = true)
    public Optional<RegistrazioneInAttesa> trovaValida(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return registrazioni.findByTokenHash(GeneratoreToken.hash(token.trim()))
                .filter(r -> r.getScadeIl().isAfter(clock.instant()));
    }

    // Secondo tempo: l'utente ha aperto il link e scelto la password. Tutto in una transazione: o si fa tutto o niente.
    @Transactional
    public Esito conferma(String token, String password) {
        utenteService.erroreLunghezzaPassword(password).ifPresent(messaggio -> {
            throw new RegolaViolataException(messaggio);
        });
        RegistrazioneInAttesa r = trovaValida(token).orElseThrow(TokenNonValidoException::new);
        registrazioni.delete(r);                                      // monouso: da ora il link non esiste più

        Optional<Utente> esistente = utenti.findByEmailIgnoreCase(r.getEmail());
        if (esistente.isPresent()) {
            Utente u = esistente.get();
            if (u.isConPassword()) {
                return Esito.GIA_ESISTENTE;
            }
            // Scheda creata dall'amministratore senza password: la si attiva. Nome, cognome, corsi e abbonamenti restano quelli dell'amministratore;
            // l'indirizzo si completa solo se mancava.
            u.setPasswordHash(encoder.encode(password));
            if (u.getIndirizzo() == null) u.setIndirizzo(r.getIndirizzo());
            if (u.getCap() == null) u.setCap(r.getCap());
            if (u.getCitta() == null) u.setCitta(r.getCitta());
            applicaConsensi(u, r);
            return Esito.ATTIVATO;
        }

        Ruolo iscritto = ruoli.findByNome(Ruolo.ISCRITTO)
                .orElseThrow(() -> new IllegalStateException("Il ruolo ISCRITTO non esiste nel database"));
        Utente nuovo = new Utente();
        nuovo.setNome(r.getNome());
        nuovo.setCognome(r.getCognome());
        nuovo.setEmail(r.getEmail());
        nuovo.setIndirizzo(r.getIndirizzo());
        nuovo.setCap(r.getCap());
        nuovo.setCitta(r.getCitta());
        nuovo.setPasswordHash(encoder.encode(password));
        Set<Ruolo> ruoliIniziali = new HashSet<>();
        ruoliIniziali.add(iscritto);
        nuovo.setRuoli(ruoliIniziali);
        applicaConsensi(nuovo, r);
        utenti.saveAndFlush(nuovo);        // flush subito: un conflitto sull'email (due conferme insieme) emerge qui e non a fine transazione
        return Esito.CREATO;
    }

    // Copia sull'utente i consensi dati nel modulo di registrazione (informativa e, se dato, marketing).
    private void applicaConsensi(Utente u, RegistrazioneInAttesa r) {
        u.accettaPrivacy(r.getPrivacyAccettataIl(), r.getPrivacyVersione());
        if (r.getConsensoMarketingIl() != null) {
            u.impostaConsensoMarketing(r.getConsensoMarketingIl());
        }
    }

    // Pulizia notturna: cancella le registrazioni con il link scaduto. Restituisce quante.
    @Transactional
    public int eliminaScadute() {
        return registrazioni.eliminaScadutePrima(clock.instant());
    }
}
