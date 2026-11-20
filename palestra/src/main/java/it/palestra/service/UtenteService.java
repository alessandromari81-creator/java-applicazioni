/*
 * FILE: UtenteService.java
 * A COSA SERVE: contiene la logica dell'applicazione sugli utenti (creare, modificare, eliminare, ruoli,
 * password, email duplicate, protezione dell'ultimo admin, regole sugli istruttori, cambio password).
 * Il controller riceve la richiesta HTTP e delega qui; il service parla con i repository.
 * PARAGONE LARAVEL: una classe in app/Services (in Spring è la regola: Controller -> Service -> Repository).
 */
package it.palestra.service;

import it.palestra.exception.RegolaViolataException;
import it.palestra.exception.RisorsaNonTrovataException;
import it.palestra.model.Corso;
import it.palestra.model.Ruolo;
import it.palestra.model.Utente;
import it.palestra.repository.AbbonamentoRepository;
import it.palestra.repository.CorsoRepository;
import it.palestra.repository.RuoloRepository;
import it.palestra.repository.UtenteRepository;
import it.palestra.pagina.RichiestaPagina;
import it.palestra.web.UtenteForm;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// @Service = Spring crea UNA istanza e la inietta dove serve.
// @Transactional = ogni metodo gira in una transazione DB: o riesce tutto o niente (come DB::transaction()),
// e a fine metodo salva le modifiche alle entità. Un'eccezione annulla (rollback) tutto.
@Service
@Transactional
public class UtenteService {

    private final UtenteRepository utenti;
    private final CorsoRepository corsi;
    private final RuoloRepository ruoli;
    private final AbbonamentoRepository abbonamenti;
    private final PasswordEncoder encoder;
    private final RefreshTokenService refreshTokens;
    private final Clock clock;

    // Dependency injection: Spring vede i parametri del costruttore e passa gli oggetti giusti.
    public UtenteService(UtenteRepository utenti, CorsoRepository corsi, RuoloRepository ruoli,
                         AbbonamentoRepository abbonamenti, PasswordEncoder encoder,
                         RefreshTokenService refreshTokens, Clock clock) {
        this.utenti = utenti;
        this.corsi = corsi;
        this.ruoli = ruoli;
        this.abbonamenti = abbonamenti;
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    // UNA PAGINA di utenti (con corsi frequentati, ruoli e corsi tenuti), eventualmente filtrata dalla ricerca.
    // "QUERY IN DUE PASSI": 1) si chiede al database solo la pagina di utenti (LIMIT/OFFSET veri, senza collezioni);
    // 2) si caricano quegli utenti, e solo quelli, con le loro relazioni. Se si caricasse tutto in una query con le JOIN,
    // ogni utente avrebbe più righe (ruolo x corso) e il database non saprebbe tagliare "20 utenti": Hibernate leggerebbe
    // TUTTA la tabella e paginerebbe in memoria.
    @Transactional(readOnly = true)
    public Page<Utente> elenco(RichiestaPagina richiesta) {
        String q = richiesta.q();
        Page<Utente> pagina = q == null
                ? utenti.findAll(richiesta.pageable())
                : utenti.findByNomeContainingIgnoreCaseOrCognomeContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        q, q, q, richiesta.pageable());

        List<Long> ids = pagina.getContent().stream().map(u -> u.getId()).toList();
        if (ids.isEmpty()) return pagina;                                   // pagina vuota: niente passo 2

        // Passo 2. La query non garantisce l'ordine: si rimette nell'ordine della pagina (quello del passo 1).
        Map<Long, Utente> caricati = utenti.findConRelazioniByIdIn(ids).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u));
        List<Utente> nellOrdineGiusto = ids.stream().map(id -> caricati.get(id)).toList();
        return new PageImpl<>(nellOrdineGiusto, richiesta.pageable(), pagina.getTotalElements());
    }

    // Un utente per id (con corsi, ruoli e corsi tenuti), oppure 404 se non esiste (come findOrFail()).
    @Transactional(readOnly = true)
    public Utente trova(Long id) {
        return utenti.findWithRelazioniById(id)
                .orElseThrow(() -> new RisorsaNonTrovataException("Utente " + id + " non trovato"));
    }

    // Tutti i corsi e tutti i ruoli: servono per mostrare le checkbox nel form.
    @Transactional(readOnly = true)
    public List<Corso> tuttiICorsi() {
        return corsi.findAll();
    }

    // CACHE: @Cacheable("ruoli") = "la prima volta esegui il metodo e salva il risultato nella cache chiamata ruoli; le volte
    // dopo restituisci quello salvato SENZA entrare nel metodo (quindi senza query)".
    // Perché proprio qui: i ruoli sono 3, si leggono ad ogni apertura del form utente e ad ogni richiesta API che li mappa, e
    // cambiano solo con una migration. La chiave è unica (metodo senza parametri). Gli oggetti Ruolo sono immutabili (nessun setter),
    // quindi condividerli tra richieste è sicuro. Non c'è @CacheEvict perché nell'app nessuna funzione modifica i ruoli: se un
    // giorno si aggiungesse "crea/elimina ruolo", va messo lì @CacheEvict(value = "ruoli", allEntries = true).
    @Cacheable("ruoli")
    @Transactional(readOnly = true)
    public List<Ruolo> tuttiIRuoli() {
        return ruoli.findAllByOrderByIdAsc();
    }

    // Gli id dei corsi che l'utente TIENE come istruttore (vuoto se è nuovo o non insegna).
    // Il form li usa per DISATTIVARE quelle checkbox: un istruttore non può iscriversi al corso che tiene.
    @Transactional(readOnly = true)
    public Set<Long> idCorsiInsegnati(Long utenteId) {
        if (utenteId == null) return Set.of();
        return trova(utenteId).getCorsiInsegnati().stream().map(corso -> corso.getId()).collect(Collectors.toSet());
    }

    // true se l'email è già usata da UN ALTRO utente. idDaIgnorare = id dell'utente che stiamo modificando
    // (null in creazione): tenere la propria email non è un duplicato.
    @Transactional(readOnly = true)
    public boolean emailInUso(String email, Long idDaIgnorare) {
        return utenti.findByEmailIgnoreCase(email)
                .map(u -> !u.getId().equals(idDaIgnorare))
                .orElse(false);
    }

    // Controlla le regole sulla password nel form utente e restituisce il messaggio d'errore, oppure Optional.vuoto.
    //  - se è scritta: da 8 caratteri e al massimo 72 BYTE (vedi erroreLunghezzaPassword);
    //  - se tra i ruoli c'è ADMIN e l'utente non ha ancora una password: è obbligatoria (senza, l'admin non
    //    potrebbe fare login). In modifica, lasciarla vuota significa "non cambiarla".
    @Transactional(readOnly = true)
    public Optional<String> erroreSullaPassword(UtenteForm f, Long id) {
        String password = f.getPassword();
        boolean scritta = password != null && !password.isEmpty();
        if (scritta) {
            return erroreLunghezzaPassword(password);
        }
        if (contieneRuolo(f.getRuoliIds(), Ruolo.ADMIN)) {
            boolean giaImpostata = id != null && trova(id).getPasswordHash() != null;
            if (!giaImpostata) {
                return Optional.of("La password è obbligatoria per gli amministratori");
            }
        }
        return Optional.empty();
    }

    // Validazione dei corsi scelti per l'utente (restituisce il messaggio d'errore, oppure vuoto se va bene). Due regole, controllate in quest'ordine:
    //  1) un ISTRUTTORE non può iscriversi come allievo ai corsi che tiene lui stesso;
    //  2) per ISCRIVERE un ISCRITTO a un corso serve un abbonamento ATTIVO OGGI. La regola riguarda solo i corsi che si AGGIUNGONO: quelli a cui l'utente è già
    //     iscritto restano anche se l'abbonamento è scaduto (si mostra solo un avviso) e togliere corsi si può sempre; ma finché non rinnova non si possono
    //     aggiungerne altri. Un utente appena creato non ha ancora abbonamenti: prima si crea, poi si aggiunge l'abbonamento, poi si scelgono i corsi.
    // La regola 2 vale solo per chi ha il ruolo ISCRITTO (gli altri non possono avere abbonamenti).
    @Transactional(readOnly = true)
    public Optional<String> erroreSuiCorsiScelti(UtenteForm f, Long id) {
        if (id != null) {
            String conflitti = trova(id).getCorsiInsegnati().stream()
                    .filter(c -> f.getCorsiIds().contains(c.getId()))
                    .map(corso -> corso.getNome())
                    .sorted()
                    .collect(Collectors.joining(", "));
            if (!conflitti.isEmpty()) {
                return Optional.of("Non puoi iscrivere un istruttore ai corsi che tiene lui stesso: " + conflitti);
            }
        }
        if (contieneRuolo(f.getRuoliIds(), Ruolo.ISCRITTO) && !haAbbonamentoAttivo(id)) {
            Set<Long> giaIscritto = id == null ? Set.of()
                    : trova(id).getCorsi().stream().map(c -> c.getId()).collect(Collectors.toSet());
            String nuovi = corsi.findAllById(f.getCorsiIds()).stream()
                    .filter(c -> !giaIscritto.contains(c.getId()))
                    .map(corso -> corso.getNome())
                    .sorted()
                    .collect(Collectors.joining(", "));
            if (!nuovi.isEmpty()) {
                return Optional.of("Per iscrivere un utente a un corso serve un abbonamento attivo oggi (corsi da aggiungere: " + nuovi
                        + "). Aggiungi o rinnova l'abbonamento dalla pagina Abbonamenti. I corsi a cui è già iscritto restano.");
            }
        }
        return Optional.empty();
    }

    // true se l'utente ha un abbonamento attivo oggi (la data di oggi viene dal Clock). Un utente non ancora salvato (id null) non ne ha.
    @Transactional(readOnly = true)
    public boolean haAbbonamentoAttivo(Long utenteId) {
        return utenteId != null && abbonamenti.haAbbonamentoAttivo(utenteId, LocalDate.now(clock));
    }

    // Crea un nuovo utente con i dati del form e lo salva (INSERT).
    public Utente crea(UtenteForm form) {
        return utenti.save(applica(new Utente(), form));
    }

    // Aggiorna un utente esistente. Non serve save(): l'entità caricata è "gestita" da Hibernate, che a fine
    // transazione vede le modifiche e fa l'UPDATE da solo. Prima controlla due cose:
    //  1) non si toglie ADMIN all'ultimo amministratore (nessuno potrebbe più entrare nel pannello);
    //  2) non si toglie ISTRUTTORE a chi tiene ancora dei corsi: resterebbero con un "istruttore" che non lo è più;
    //  3) non si toglie ISCRITTO a chi ha abbonamenti registrati: solo gli iscritti possono averne.
    // Se cambiano la password o i ruoli, i token di accesso già emessi dall'utente vengono revocati; se cambia la password anche i suoi
    // refresh token.
    public Utente modifica(Long id, UtenteForm form) {
        Utente u = trova(id);
        if (u.isAdmin() && !contieneRuolo(form.getRuoliIds(), Ruolo.ADMIN)
                && contaAdminConBlocco() <= 1) {
            throw new RegolaViolataException("Non puoi togliere il ruolo ADMIN all'ultimo amministratore");
        }
        if (!u.getCorsiInsegnati().isEmpty() && !contieneRuolo(form.getRuoliIds(), Ruolo.ISTRUTTORE)) {
            throw new RegolaViolataException("Non puoi togliere il ruolo ISTRUTTORE: tiene ancora "
                    + u.getCorsiInsegnati().size() + " corsi. Assegna prima un altro istruttore a quei corsi");
        }
        if (u.isIscritto() && !contieneRuolo(form.getRuoliIds(), Ruolo.ISCRITTO)
                && abbonamenti.countByUtenteId(id) > 0) {
            throw new RegolaViolataException("Non puoi togliere il ruolo ISCRITTO: ha "
                    + abbonamenti.countByUtenteId(id) + " abbonamenti registrati");
        }
        Set<Ruolo> ruoliPrima = new HashSet<>(u.getRuoli());
        boolean passwordCambiata = form.getPassword() != null && !form.getPassword().isEmpty();
        applica(u, form);
        // Se cambiano la password o i ruoli, i token di accesso già emessi non valgono più (es. a un admin tolto il ruolo ADMIN
        // non deve restare l'accesso alla API fino alla scadenza del token).
        if (passwordCambiata || !ruoliPrima.equals(u.getRuoli())) {
            u.revocaTokenAccesso();
        }
        if (passwordCambiata) {
            refreshTokens.revocaTuttiDi(u.getId());     // con una nuova password anche i refresh token vecchi smettono di valere
        }
        return u;
    }

    // Elimina l'utente (DELETE) e le sue righe nelle pivot (corsi e ruoli); i corsi e i ruoli restano.
    // Rifiuta se è l'ultimo admin (nessuno potrebbe più entrare nel pannello), se tiene dei corsi (la colonna
    // corso.istruttore_id punterebbe a un utente che non esiste più, e il database lo vieterebbe) o se ha
    // abbonamenti registrati (lo storico dei pagamenti non si perde).
    public void elimina(Long id) {
        Utente u = trova(id);
        if (!u.getCorsiInsegnati().isEmpty()) {
            throw new RegolaViolataException("Impossibile eliminare: è istruttore di "
                    + u.getCorsiInsegnati().size() + " corsi. Assegna prima un altro istruttore a quei corsi");
        }
        long numeroAbbonamenti = abbonamenti.countByUtenteId(id);
        if (numeroAbbonamenti > 0) {
            throw new RegolaViolataException("Impossibile eliminare: ha " + numeroAbbonamenti
                    + " abbonamenti registrati (restano nello storico)");
        }
        if (u.isAdmin() && contaAdminConBlocco() <= 1) {
            throw new RegolaViolataException("Impossibile eliminare l'ultimo amministratore");
        }
        utenti.delete(u);
    }

    // ----------------------------------------------------------------- CAMBIO PASSWORD

    // true se la password scritta corrisponde a quella dell'utente con questa email (si confrontano gli hash:
    // la password in chiaro non è salvata da nessuna parte). Una password oltre i 72 byte non può essere quella
    // giusta (bcrypt non ne accetta di più): rispondiamo false invece di far lanciare un errore a bcrypt.
    @Transactional(readOnly = true)
    public boolean passwordCorrisponde(String email, String password) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) return false;
        return utenti.findByEmailIgnoreCase(email)
                .map(u -> u.getPasswordHash() != null && encoder.matches(password, u.getPasswordHash()))
                .orElse(false);
    }

    // Imposta la nuova password: nel database finisce solo l'hash bcrypt. Basta cambiare il campo: a fine
    // transazione Hibernate fa l'UPDATE da solo. Il controllo di quella attuale lo fa il controller PRIMA di
    // arrivare qui (con passwordCorrisponde).
    public void cambiaPassword(String email, String nuovaPassword) {
        Utente u = utenti.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RisorsaNonTrovataException("Utente " + email + " non trovato"));
        u.setPasswordHash(encoder.encode(nuovaPassword));
        u.revocaTokenAccesso();      // i token di accesso emessi con la vecchia password non valgono più...
        refreshTokens.revocaTuttiDi(u.getId());    // ...e nemmeno i refresh token: nessuno può più rinnovare l'accesso con la vecchia
    }

    // Regola sulla lunghezza, comune al form utente e al cambio password: almeno 8 caratteri e al massimo 72
    // BYTE (limite di bcrypt, che oltre ignorerebbe il resto; i caratteri accentati occupano più di un byte).
    public Optional<String> erroreLunghezzaPassword(String password) {
        if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return Optional.of("La password deve avere da 8 a 72 caratteri");
        }
        return Optional.empty();
    }

    // ----------------------------------------------------------------- METODI DI SUPPORTO

    // Conta gli admin DOPO aver preso il blocco sulla riga del ruolo ADMIN. Il blocco mette in fila le operazioni che riducono gli
    // admin: la seconda aspetta che la prima faccia commit e poi conta di nuovo, vedendo il risultato della prima. Senza il blocco due
    // richieste insieme leggevano entrambe "ce ne sono due", ognuna toglieva il suo e non restava nessun admin. Il blocco si prende solo
    // quando serve (utente che è admin e che sta perdendo il ruolo o viene eliminato), quindi le altre modifiche non aspettano nessuno.
    // Nota: l'utente `u` è stato letto prima dell'attesa; se nel frattempo l'altra richiesta gli ha già tolto il ruolo, qui si rifiuta
    // comunque (conta 1): è un rifiuto in più innocuo, mai un admin in meno.
    private long contaAdminConBlocco() {
        ruoli.bloccaPerNome(Ruolo.ADMIN);
        return utenti.countByRuoliNome(Ruolo.ADMIN);
    }

    // true se tra gli id dei ruoli indicati c'è quello col nome dato (es. "ADMIN", "ISTRUTTORE").
    private boolean contieneRuolo(List<Long> ruoliIds, String nomeRuolo) {
        return ruoli.findAllById(ruoliIds).stream().anyMatch(r -> nomeRuolo.equals(r.getNome()));
    }

    // Toglie gli spazi ai bordi; un testo vuoto diventa null (così nel database non finiscono stringhe vuote).
    private String vuotoANull(String testo) {
        if (testo == null) return null;
        String pulito = testo.trim();
        return pulito.isEmpty() ? null : pulito;
    }

    // Metodo privato usato sia da crea() sia da modifica(): copia i dati del form dentro l'entità.
    private Utente applica(Utente u, UtenteForm f) {
        u.setNome(f.getNome().trim());          // trim() toglie gli spazi ai bordi
        u.setCognome(f.getCognome().trim());
        u.setEmail(f.getEmail().trim());
        u.setIndirizzo(vuotoANull(f.getIndirizzo()));      // i campi dell'indirizzo sono facoltativi: testo vuoto = nessun valore
        u.setCap(vuotoANull(f.getCap()));
        u.setCitta(vuotoANull(f.getCitta()));
        u.setCorsi(new HashSet<>(corsi.findAllById(f.getCorsiIds())));
        u.setRuoli(new HashSet<>(ruoli.findAllById(f.getRuoliIds())));
        // La password si cambia SOLO se è stata scritta: nel database finisce l'hash bcrypt, mai il testo.
        if (f.getPassword() != null && !f.getPassword().isEmpty()) {
            u.setPasswordHash(encoder.encode(f.getPassword()));
        }
        return u;
    }
}
