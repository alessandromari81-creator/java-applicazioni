/*
 * FILE: CorsoService.java
 * A COSA SERVE: logica sui corsi (elenco, creazione, modifica, eliminazione con controllo).
 * Il controller delega qui; il service usa i repository. Stesso schema di UtenteService.
 */
package it.palestra.service;

import it.palestra.exception.RisorsaNonTrovataException;
import it.palestra.model.Corso;
import it.palestra.repository.CorsoRepository;
import it.palestra.repository.UtenteRepository;
import it.palestra.web.CorsoForm;
import java.util.List;
import org.springframework.data.domain.Sort;
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

    // Tutti i corsi ordinati per nome (Sort = ORDER BY, come ->orderBy('nome')).
    @Transactional(readOnly = true)
    public List<Corso> elenco() {
        return corsi.findAll(Sort.by("nome"));
    }

    // Un corso per id, o eccezione se non esiste (come findOrFail()).
    @Transactional(readOnly = true)
    public Corso trova(Long id) {
        return corsi.findById(id)
                .orElseThrow(() -> new RisorsaNonTrovataException("Corso " + id + " non trovato"));
    }

    // Crea un corso con i dati del form (INSERT).
    public Corso crea(CorsoForm f) {
        return corsi.save(new Corso(f.getNome().trim(), pulisci(f.getDescrizione()),
                f.getDurataMinuti(), f.getLivello()));
    }

    // Modifica un corso: basta cambiare i campi, a fine transazione Hibernate fa l'UPDATE.
    public Corso modifica(Long id, CorsoForm f) {
        Corso c = trova(id);
        c.setNome(f.getNome().trim());
        c.setDescrizione(pulisci(f.getDescrizione()));
        c.setDurataMinuti(f.getDurataMinuti());
        c.setLivello(f.getLivello());
        return c;
    }

    // Elimina un corso SOLO se nessun utente è iscritto.
    // Perché il controllo: la tabella pivot utente_corso ha una chiave esterna verso corso,
    // quindi il database rifiuterebbe la DELETE con un errore poco chiaro. Controlliamo prima
    // e lanciamo un'eccezione con un messaggio leggibile, che il controller mostra a video.
    public void elimina(Long id) {
        long iscritti = utenti.countByCorsiId(id);
        if (iscritti > 0) {
            throw new IllegalStateException(
                    "Impossibile eliminare: ci sono " + iscritti + " utenti iscritti a questo corso");
        }
        corsi.deleteById(id);
    }

    // Se la descrizione è vuota o solo spazi salva null, altrimenti il testo senza spazi ai bordi.
    private String pulisci(String testo) {
        return (testo == null || testo.isBlank()) ? null : testo.trim();
    }
}
