/*
 * FILE: CorsoService.java
 * A COSA SERVE: logica sui corsi (elenco, creazione, modifica, eliminazione con controllo e scelta
 * dell'istruttore con le sue regole). Il controller delega qui; il service usa i repository.
 * Stesso schema di UtenteService.
 */
package it.palestra.service;

import it.palestra.exception.RegolaViolataException;
import it.palestra.exception.RisorsaNonTrovataException;
import it.palestra.model.Corso;
import it.palestra.model.Ruolo;
import it.palestra.model.Utente;
import it.palestra.repository.CorsoRepository;
import it.palestra.repository.UtenteRepository;
import it.palestra.pagina.RichiestaPagina;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// @Service: Spring ne crea un'istanza. @Transactional: ogni metodo è in una transazione.
@Service
@Transactional
public class CorsoService {

    private final CorsoRepository corsi;
    private final UtenteRepository utenti;

    // Dependency injection: Spring passa i due repository al costruttore.
    public CorsoService(CorsoRepository corsi, UtenteRepository utenti) {
        this.corsi = corsi;
        this.utenti = utenti;
    }

    // UNA PAGINA di corsi (con l'istruttore già caricato), eventualmente filtrata dalla ricerca sul nome.
    @Transactional(readOnly = true)
    public Page<Corso> elenco(RichiestaPagina richiesta) {
        return richiesta.q() == null
                ? corsi.findAll(richiesta.pageable())
                : corsi.findByNomeContainingIgnoreCase(richiesta.q(), richiesta.pageable());
    }

    // Un corso per id (con l'istruttore), o 404 se non esiste (come findOrFail()).
    @Transactional(readOnly = true)
    public Corso trova(Long id) {
        return corsi.findWithIstruttoreById(id)
                .orElseThrow(() -> new RisorsaNonTrovataException("Corso " + id + " non trovato"));
    }

    // Gli utenti tra cui scegliere l'istruttore (tendina del form): SOLO chi ha il ruolo ISTRUTTORE.
    @Transactional(readOnly = true)
    public List<Utente> istruttoriDisponibili() {
        return utenti.findByRuoliNomeOrderByCognomeAscNomeAsc(Ruolo.ISTRUTTORE);
    }

    // Controlla le regole sull'istruttore scelto e restituisce il messaggio d'errore, oppure Optional vuoto se
    // va bene (o se non è stato scelto nessun istruttore, che è ammesso). corsoId = id del corso che stiamo
    // modificando (null in creazione).
    // REGOLE:
    //  1) l'utente scelto deve esistere ed avere il ruolo ISTRUTTORE;
    //  2) l'istruttore NON può essere iscritto come allievo allo stesso corso (in creazione non può succedere,
    //     perché un corso nuovo non ha ancora iscritti: il controllo serve in modifica).
    // La tendina mostra già solo gli istruttori, ma il controllo va fatto anche qui: una richiesta può essere
    // costruita a mano (o la pagina può essere scaduta), quindi non ci si fida mai solo di ciò che mostra l'HTML.
    @Transactional(readOnly = true)
    public Optional<String> erroreSullIstruttore(DatiCorso f, Long corsoId) {
        Long istruttoreId = f.istruttoreId();
        if (istruttoreId == null) return Optional.empty();

        Optional<Utente> scelto = utenti.findWithRelazioniById(istruttoreId);
        if (scelto.isEmpty()) {
            return Optional.of("L'istruttore scelto non esiste");
        }
        if (!scelto.get().isIstruttore()) {
            return Optional.of("L'utente scelto non ha il ruolo ISTRUTTORE");
        }
        if (corsoId != null && utenti.existsByIdAndCorsiId(istruttoreId, corsoId)) {
            return Optional.of("L'istruttore scelto è iscritto a questo corso come allievo: "
                    + "togli prima la sua iscrizione (un istruttore non può frequentare il corso che tiene)");
        }
        return Optional.empty();
    }

    // Crea un corso con i dati ricevuti, cioè un DatiCorso (INSERT).
    public Corso crea(DatiCorso f) {
        Corso c = new Corso(f.nome().trim(), pulisci(f.descrizione()), f.durataMinuti(), f.livello());
        c.setIstruttore(istruttoreDa(f));
        return corsi.save(c);
    }

    // Modifica un corso: basta cambiare i campi, a fine transazione Hibernate fa l'UPDATE.
    public Corso modifica(Long id, DatiCorso f) {
        Corso c = trova(id);
        c.setNome(f.nome().trim());
        c.setDescrizione(pulisci(f.descrizione()));
        c.setDurataMinuti(f.durataMinuti());
        c.setLivello(f.livello());
        c.setIstruttore(istruttoreDa(f));
        return c;
    }

    // Elimina un corso SOLO se nessun utente è iscritto.
    // Perché il controllo: la tabella pivot utente_corso ha una chiave esterna verso corso,
    // quindi il database rifiuterebbe la DELETE con un errore poco chiaro. Controlliamo prima
    // e lanciamo un'eccezione con un messaggio leggibile, che il controller mostra a video.
    // (L'istruttore non crea problemi: il legame sta nella colonna istruttore_id del corso stesso.)
    public void elimina(Long id) {
        long iscritti = utenti.countByCorsiId(id);
        if (iscritti > 0) {
            throw new RegolaViolataException(
                    "Impossibile eliminare: ci sono " + iscritti + " utenti iscritti a questo corso");
        }
        corsi.deleteById(id);
    }

    // Trasforma l'id dell'istruttore scelto nell'utente istruttore (o null se nessuno). getReferenceById non fa una
    // SELECT: restituisce un "segnaposto" con quell'id, sufficiente per scrivere la colonna istruttore_id.
    private Utente istruttoreDa(DatiCorso f) {
        return f.istruttoreId() == null ? null : utenti.getReferenceById(f.istruttoreId());
    }

    // Se la descrizione è vuota o solo spazi salva null, altrimenti il testo senza spazi ai bordi.
    private String pulisci(String testo) {
        return (testo == null || testo.isBlank()) ? null : testo.trim();
    }
}
