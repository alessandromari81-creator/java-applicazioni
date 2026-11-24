/*
 * FILE: AbbonamentoService.java
 * A COSA SERVE: la LOGICA DI BUSINESS degli abbonamenti. È qui che stanno le regole:
 *   1) solo chi ha il ruolo ISCRITTO può avere un abbonamento;
 *   2) la data di inizio non può essere nel passato;
 *   3) lo stesso iscritto non può avere due abbonamenti che si sovrappongono nelle date (un rinnovo parte dal
 *      giorno dopo la scadenza);
 *   4) si possono eliminare solo gli abbonamenti NON ancora iniziati (attivi e scaduti restano nello storico).
 * La data di fine e il prezzo non si scelgono: li calcola la classe Abbonamento dal tipo.
 * Il controller delega qui; il service usa i repository.
 */
package it.palestra.service;

import it.palestra.exception.RegolaViolataException;
import it.palestra.exception.RisorsaNonTrovataException;
import it.palestra.model.Abbonamento;
import it.palestra.model.StatoAbbonamento;
import it.palestra.model.Utente;
import it.palestra.model.Ruolo;
import it.palestra.repository.AbbonamentoRepository;
import it.palestra.repository.UtenteRepository;
import it.palestra.pagina.RichiestaPagina;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AbbonamentoService {

    // Formato delle date nei messaggi per l'utente (italiano: giorno/mese/anno).
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AbbonamentoRepository abbonamenti;
    private final UtenteRepository utenti;
    private final Clock clock;

    // Dependency injection: Spring passa i repository e l'orologio (vedi OrologioConfig).
    public AbbonamentoService(AbbonamentoRepository abbonamenti, UtenteRepository utenti, Clock clock) {
        this.abbonamenti = abbonamenti;
        this.utenti = utenti;
        this.clock = clock;
    }

    // La data di oggi, presa dall'orologio dell'applicazione. Tutto il codice usa questa e MAI LocalDate.now():
    // così nei test si può "fermare il tempo" a una data scelta.
    public LocalDate oggi() {
        return LocalDate.now(clock);
    }

    // UNA PAGINA di abbonamenti (con l'utente già caricato), eventualmente filtrata dalla ricerca sull'ISCRITTO
    // (nome, cognome o email).
    @Transactional(readOnly = true)
    public Page<Abbonamento> elenco(RichiestaPagina richiesta) {
        String q = richiesta.q();
        return q == null
                ? abbonamenti.findAll(richiesta.pageable())
                : abbonamenti.findByUtenteNomeContainingIgnoreCaseOrUtenteCognomeContainingIgnoreCaseOrUtenteEmailContainingIgnoreCase(
                        q, q, q, richiesta.pageable());
    }

    // Un abbonamento per id, oppure 404 se non esiste.
    @Transactional(readOnly = true)
    public Abbonamento trova(Long id) {
        return abbonamenti.findConUtenteById(id)
                .orElseThrow(() -> new RisorsaNonTrovataException("Abbonamento " + id + " non trovato"));
    }

    // L'utente di cui si mostra la pagina "i suoi abbonamenti" (con i ruoli già caricati), oppure 404.
    // Si accetta anche chi NON è iscritto: la pagina gli spiega perché non può avere abbonamenti.
    @Transactional(readOnly = true)
    public Utente trovaUtente(Long utenteId) {
        return utenti.findWithRelazioniById(utenteId)
                .orElseThrow(() -> new RisorsaNonTrovataException("Utente " + utenteId + " non trovato"));
    }

    // UNA PAGINA dello storico degli abbonamenti di un utente (di partenza, dal più recente).
    @Transactional(readOnly = true)
    public Page<Abbonamento> abbonamentiDi(Long utenteId, RichiestaPagina richiesta) {
        return abbonamenti.findByUtenteId(utenteId, richiesta.pageable());
    }

    // L'id dell'utente proprietario di un abbonamento (404 se l'abbonamento non esiste). Serve al controller per
    // tornare alla pagina giusta dopo un'eliminazione. È un metodo del service perché leggere l'utente di un
    // abbonamento richiede una transazione aperta (l'utente si carica "pigramente" dal database).
    @Transactional(readOnly = true)
    public Long utenteDi(Long abbonamentoId) {
        return trova(abbonamentoId).getUtente().getId();
    }

    // Gli utenti tra cui scegliere nel form: SOLO chi ha il ruolo ISCRITTO.
    @Transactional(readOnly = true)
    public List<Utente> iscrittiDisponibili() {
        return utenti.findByRuoliNomeOrderByCognomeAscNomeAsc(Ruolo.ISCRITTO);
    }

    // Il primo giorno in cui l'utente può iniziare un nuovo abbonamento senza sovrapporsi: è la logica del RINNOVO.
    //  - se non ha abbonamenti, o sono tutti già scaduti: oggi;
    //  - altrimenti il giorno DOPO la fine dell'ultimo (anche se è un abbonamento futuro).
    // Serve a precompilare la data di inizio e viene indicato nei messaggi d'errore.
    @Transactional(readOnly = true)
    public LocalDate primoGiornoLibero(Long utenteId) {
        LocalDate oggi = oggi();
        if (utenteId == null) return oggi;
        LocalDate ultimaFine = abbonamenti.ultimaDataFine(utenteId);
        return (ultimaFine == null || ultimaFine.isBefore(oggi)) ? oggi : ultimaFine.plusDays(1);
    }

    // Per ognuno degli utenti indicati, l'abbonamento "più significativo" da mostrare in una riga di riepilogo:
    //   1) quello ATTIVO oggi; altrimenti 2) il PROSSIMO che inizierà; altrimenti 3) l'ultimo scaduto.
    // Restituisce una mappa id utente -> abbonamento (gli utenti senza abbonamenti non compaiono).
    // Si passano gli id degli utenti MOSTRATI (quelli della pagina) e si leggono solo i loro abbonamenti: con la
    // paginazione non ha senso caricare tutti gli abbonamenti della tabella per mostrarne venti.
    @Transactional(readOnly = true)
    public Map<Long, Abbonamento> riepilogoPerUtente(Collection<Long> utenteIds) {
        LocalDate oggi = oggi();
        Map<Long, Abbonamento> riepilogo = new HashMap<>();
        if (utenteIds.isEmpty()) return riepilogo;
        for (Abbonamento a : abbonamenti.findByUtenteIdIn(utenteIds)) {
            // getId() sull'utente "segnaposto" non richiede di rileggere il database.
            riepilogo.merge(a.getUtente().getId(), a, (primo, secondo) -> piuSignificativo(primo, secondo, oggi));
        }
        return riepilogo;
    }

    // Controlla che l'utente scelto esista e abbia il ruolo ISCRITTO. Restituisce il messaggio d'errore, oppure
    // Optional vuoto se va bene (o se non è stato scelto nessun utente: ci pensa già @NotNull sul form).
    @Transactional(readOnly = true)
    public Optional<String> erroreSulUtente(DatiAbbonamento f) {
        if (f.utenteId() == null) return Optional.empty();
        Optional<Utente> scelto = utenti.findWithRelazioniById(f.utenteId());
        if (scelto.isEmpty()) return Optional.of("L'utente scelto non esiste");
        if (!scelto.get().isIscritto()) return Optional.of("Solo gli utenti con ruolo ISCRITTO possono avere un abbonamento");
        return Optional.empty();
    }

    // Controlla le regole sulla data di inizio: non nel passato e nessuna sovrapposizione con altri abbonamenti
    // dello stesso utente. Restituisce il messaggio d'errore, oppure Optional vuoto.
    @Transactional(readOnly = true)
    public Optional<String> erroreSullaDataDiInizio(DatiAbbonamento f) {
        if (f.utenteId() == null || f.tipo() == null || f.dataInizio() == null) return Optional.empty();

        LocalDate oggi = oggi();
        if (f.dataInizio().isBefore(oggi)) {
            return Optional.of("La data di inizio non può essere nel passato (oggi è il " + oggi.format(DATA) + ")");
        }

        // Il periodo che nascerebbe: dalla data scelta fino alla fine calcolata dal tipo.
        LocalDate fine = f.tipo().calcolaFine(f.dataInizio());
        List<Abbonamento> sovrapposti = abbonamenti.trovaSovrapposti(f.utenteId(), f.dataInizio(), fine);
        if (!sovrapposti.isEmpty()) {
            Abbonamento a = sovrapposti.get(0);
            return Optional.of("Si sovrappone a un abbonamento già registrato (dal " + a.getDataInizio().format(DATA)
                    + " al " + a.getDataFine().format(DATA) + "). Il primo giorno libero per questo iscritto è il "
                    + primoGiornoLibero(f.utenteId()).format(DATA));
        }
        return Optional.empty();
    }

    // Crea l'abbonamento (INSERT). Data di fine e prezzo li calcola il costruttore di Abbonamento dal tipo.
    // L'utente si legge davvero dal database (e non con un "segnaposto" come getReferenceById): così l'abbonamento
    // restituito ha già nome e cognome, che la API mostra senza dover rileggere il database.
    public Abbonamento crea(DatiAbbonamento f) {
        Utente utente = utenti.findById(f.utenteId())
                .orElseThrow(() -> new RisorsaNonTrovataException("Utente " + f.utenteId() + " non trovato"));
        return abbonamenti.save(new Abbonamento(utente, f.tipo(), f.dataInizio()));
    }

    // Elimina un abbonamento SOLO se non è ancora iniziato (serve a correggere un errore di inserimento). Quelli
    // attivi o scaduti sono già stati usati e pagati: restano nello storico e non si cancellano.
    public void elimina(Long id) {
        Abbonamento a = trova(id);
        if (a.stato(oggi()) != StatoAbbonamento.FUTURO) {
            throw new RegolaViolataException("Si possono eliminare solo gli abbonamenti non ancora iniziati: "
                    + "quelli attivi o scaduti restano nello storico");
        }
        abbonamenti.delete(a);
    }

    // Tra due abbonamenti dello stesso utente sceglie il più significativo (vedi riepilogoPerUtente).
    private Abbonamento piuSignificativo(Abbonamento x, Abbonamento y, LocalDate oggi) {
        int px = priorita(x, oggi), py = priorita(y, oggi);
        if (px != py) return px < py ? x : y;            // priorità più bassa = più significativo
        return switch (x.stato(oggi)) {
            case FUTURO -> x.getDataInizio().isBefore(y.getDataInizio()) ? x : y;   // il più vicino a partire
            default -> x.getDataFine().isAfter(y.getDataFine()) ? x : y;             // il più recente
        };
    }

    // 0 = attivo, 1 = futuro, 2 = scaduto.
    private int priorita(Abbonamento a, LocalDate oggi) {
        return switch (a.stato(oggi)) {
            case ATTIVO -> 0;
            case FUTURO -> 1;
            case SCADUTO -> 2;
        };
    }
}
