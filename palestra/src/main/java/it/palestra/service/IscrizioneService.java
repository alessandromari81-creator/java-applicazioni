/*
 * FILE: IscrizioneService.java
 * A COSA SERVE: l'iscrizione di un utente ai corsi, un corso alla volta: quali corsi segue, a quali può essere iscritto (con ricerca e
 * paginazione, perché i corsi possono essere tantissimi), iscrivere e disiscrivere. Lo usa la pagina «Corsi» di un utente
 * (CorsiUtenteController), che ha preso il posto dell'elenco di caselle nel modulo di modifica dell'utente.
 * Le REGOLE non sono scritte qui ma in UtenteService.erroreSuiCorsi, così valgono allo stesso modo anche per la API (che sostituisce tutti i corsi
 * insieme): un istruttore non si iscrive al corso che tiene; per iscrivere chiunque serve un abbonamento attivo oggi (e quindi il ruolo ISCRITTO, l'unico che può avere abbonamenti). Togliere un corso si può sempre.
 */
package it.palestra.service;

import it.palestra.exception.RegolaViolataException;
import it.palestra.exception.RisorsaNonTrovataException;
import it.palestra.model.Corso;
import it.palestra.model.Utente;
import it.palestra.pagina.RichiestaPagina;
import it.palestra.repository.CorsoRepository;
import it.palestra.repository.UtenteRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IscrizioneService {

    // Carattere con cui si «protegge» un carattere speciale di LIKE (%, _) nel testo cercato; è lo stesso scritto nella query di CorsoRepository.
    private static final char ESCAPE = '!';

    private final UtenteRepository utenti;
    private final CorsoRepository corsi;
    private final UtenteService utenteService;

    // Dependency injection: Spring passa i repository e UtenteService (da cui arriva la regola sui corsi e il controllo dell'abbonamento).
    public IscrizioneService(UtenteRepository utenti, CorsoRepository corsi, UtenteService utenteService) {
        this.utenti = utenti;
        this.corsi = corsi;
        this.utenteService = utenteService;
    }

    // L'utente della pagina (con ruoli e corsi già caricati), oppure 404 se non esiste.
    @Transactional(readOnly = true)
    public Utente trovaUtente(Long utenteId) {
        return utenti.findWithRelazioniById(utenteId)
                .orElseThrow(() -> new RisorsaNonTrovataException("Utente " + utenteId + " non trovato"));
    }

    // I corsi a cui l'utente è iscritto, in ordine alfabetico, con l'istruttore già caricato. Sono pochi per natura: non si paginano.
    @Transactional(readOnly = true)
    public List<Corso> corsiDi(Long utenteId) {
        return corsi.iscrittoA(utenteId);
    }

    // UNA PAGINA dei corsi a cui l'utente può essere iscritto: non quelli a cui è già iscritto né quelli che tiene come istruttore.
    // La ricerca (richiesta.q()) cerca nel nome del corso; % e _ sono caratteri normali, come nelle altre liste.
    @Transactional(readOnly = true)
    public Page<Corso> corsiDisponibili(Long utenteId, RichiestaPagina richiesta) {
        return corsi.disponibiliPer(utenteId, modelloDiRicerca(richiesta.q()), richiesta.pageable());
    }

    // true se all'utente si possono aggiungere corsi: serve un abbonamento attivo oggi, per tutti. Chi non ha il ruolo ISCRITTO non può averne uno, quindi non può essere
    // iscritto ai corsi finché non gli si assegna il ruolo e non gli si aggiunge un abbonamento. Serve alla pagina per spegnere i pulsanti «Iscrivi...»; il controllo
    // vero lo rifà iscrivi().
    @Transactional(readOnly = true)
    public boolean puoIscriversi(Utente utente) {
        return utenteService.haAbbonamentoAttivo(utente.getId());
    }

    // Iscrive l'utente al corso e restituisce il corso (il controller ne usa il nome nel messaggio). Prende il blocco sulla riga dell'utente (vedi trovaUtenteBloccato). Rifiuta con RegolaViolataException se è già
    // iscritto o se la regola sui corsi lo vieta (istruttore del corso, abbonamento non attivo); 404 se l'utente o il corso non esistono.
    // Basta aggiungere il corso all'insieme dell'utente: a fine transazione Hibernate scrive la riga nella tabella utente_corso.
    public Corso iscrivi(Long utenteId, Long corsoId) {
        Utente utente = trovaUtenteBloccato(utenteId);
        Corso corso = trovaCorso(corsoId);
        if (utente.getCorsi().contains(corso)) {
            throw new RegolaViolataException(nomeCompleto(utente) + " è già iscritto al corso " + corso.getNome());
        }
        List<Long> ruoliIds = utente.getRuoli().stream().map(r -> r.getId()).toList();
        List<Long> corsiDopo = new ArrayList<>(utente.getCorsi().stream().map(c -> c.getId()).toList());
        corsiDopo.add(corso.getId());
        utenteService.erroreSuiCorsi(utente.getId(), ruoliIds, corsiDopo).ifPresent(messaggio -> {
            throw new RegolaViolataException(messaggio);
        });
        utente.getCorsi().add(corso);
        return corso;
    }

    // Toglie l'iscrizione e restituisce il corso. Si può sempre, anche con l'abbonamento scaduto. Rifiuta se l'utente non era iscritto a quel corso.
    public Corso disiscrivi(Long utenteId, Long corsoId) {
        Utente utente = trovaUtenteBloccato(utenteId);
        Corso corso = trovaCorso(corsoId);
        if (!utente.getCorsi().remove(corso)) {
            throw new RegolaViolataException(nomeCompleto(utente) + " non è iscritto al corso " + corso.getNome());
        }
        return corso;
    }

    // Il testo cercato pronto per LIKE: «%» (tutto) se non si cerca niente, altrimenti «%testo%» con %, _ e il carattere di escape protetti,
    // così «50%» cerca davvero «50%» e non «50 seguito da qualunque cosa». È public e static perché è una funzione pura che si prova da sola.
    public static String modelloDiRicerca(String q) {
        if (q == null) return "%";
        String protetto = q.replace(String.valueOf(ESCAPE), "" + ESCAPE + ESCAPE)
                .replace("%", ESCAPE + "%")
                .replace("_", ESCAPE + "_");
        return "%" + protetto + "%";
    }

    // L'utente con la riga BLOCCATA fino alla fine della transazione, poi riletto con ruoli e corsi. Il blocco va preso PRIMA di leggere le iscrizioni:
    // chi arriva secondo aspetta qui, e quando riparte la lettura vede ciò che la prima richiesta ha già confermato. Senza, due iscrizioni insieme
    // passerebbero entrambe il controllo «è già iscritto?» e la seconda fallirebbe contro la chiave primaria di utente_corso con un errore generico.
    private Utente trovaUtenteBloccato(Long utenteId) {
        utenti.bloccaPerId(utenteId).orElseThrow(() -> new RisorsaNonTrovataException("Utente " + utenteId + " non trovato"));
        return trovaUtente(utenteId);
    }

    private Corso trovaCorso(Long corsoId) {
        return corsi.findWithIstruttoreById(corsoId)
                .orElseThrow(() -> new RisorsaNonTrovataException("Corso " + corsoId + " non trovato"));
    }

    private String nomeCompleto(Utente utente) {
        return utente.getNome() + " " + utente.getCognome();
    }
}
