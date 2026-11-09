/*
 * FILE: UtenteService.java
 * A COSA SERVE: contiene la logica dell'applicazione sugli utenti (creare, modificare,
 * eliminare, controllare le email duplicate). Il controller riceve la richiesta HTTP e
 * delega qui; il service parla con i repository.
 * PARAGONE LARAVEL: una classe in app/Services (in Laravel spesso si salta questo livello,
 * in Spring è la regola: Controller -> Service -> Repository).
 */
package it.palestra.service;

import it.palestra.exception.RisorsaNonTrovataException;
import it.palestra.model.Corso;
import it.palestra.model.Utente;
import it.palestra.repository.CorsoRepository;
import it.palestra.repository.UtenteRepository;
import it.palestra.web.UtenteForm;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// @Service = Spring crea UNA istanza di questa classe e la inietta dove serve.
// @Transactional = ogni metodo gira in una transazione DB: o riesce tutto o niente
// (come DB::transaction() di Laravel), e a fine metodo salva le modifiche alle entità.
@Service
@Transactional
public class UtenteService {

    // "final" = assegnati una sola volta, nel costruttore.
    private final UtenteRepository utenti;
    private final CorsoRepository corsi;

    // Dependency injection: Spring vede i parametri del costruttore e passa i repository.
    // Non scrivi mai "new UtenteService(...)": è come il type-hint nel costruttore di Laravel.
    public UtenteService(UtenteRepository utenti, CorsoRepository corsi) {
        this.utenti = utenti;
        this.corsi = corsi;
    }

    // Restituisce tutti gli utenti per la lista. readOnly = solo lettura (più efficiente).
    @Transactional(readOnly = true)
    public List<Utente> elenco() {
        return utenti.findAllByOrderByCognomeAscNomeAsc();
    }

    // Restituisce un utente per id, oppure lancia un'eccezione se non esiste
    // (come findOrFail() di Eloquent).
    @Transactional(readOnly = true)
    public Utente trova(Long id) {
        return utenti.findWithCorsiById(id)
                .orElseThrow(() -> new RisorsaNonTrovataException("Utente " + id + " non trovato"));
    }

    // Tutti i corsi disponibili: servono per mostrare le checkbox nel form.
    @Transactional(readOnly = true)
    public List<Corso> tuttiICorsi() {
        return corsi.findAll();
    }

    // true se l'email è già usata da UN ALTRO utente.
    // idDaIgnorare = id dell'utente che stiamo modificando (null in creazione), perché
    // tenere la propria email non è un duplicato (come Rule::unique()->ignore($id)).
    @Transactional(readOnly = true)
    public boolean emailInUso(String email, Long idDaIgnorare) {
        return utenti.findByEmailIgnoreCase(email)
                .map(u -> !u.getId().equals(idDaIgnorare))   // trovato: è un altro utente?
                .orElse(false);                              // non trovato: libera
    }

    // Crea un nuovo utente con i dati del form e lo salva (INSERT).
    public Utente crea(UtenteForm form) {
        return utenti.save(applica(new Utente(), form));
    }

    // Aggiorna un utente esistente. Non serve chiamare save(): l'entità caricata è
    // "gestita" da Hibernate, che a fine transazione vede le modifiche e fa l'UPDATE da solo.
    public Utente modifica(Long id, UtenteForm form) {
        return applica(trova(id), form);
    }

    // Elimina l'utente (DELETE). Hibernate cancella anche le righe nella tabella pivot,
    // ma NON elimina i corsi: si toglie solo il collegamento.
    public void elimina(Long id) {
        utenti.deleteById(id);
    }

    // Metodo privato di supporto, usato sia da crea() sia da modifica():
    // copia i dati del form dentro l'entità.
    private Utente applica(Utente u, UtenteForm f) {
        u.setNome(f.getNome().trim());          // trim() toglie gli spazi ai bordi
        u.setCognome(f.getCognome().trim());
        u.setEmail(f.getEmail().trim());
        // Trasforma la lista di id in oggetti Corso veri (una sola query) e li assegna.
        u.setCorsi(new HashSet<>(corsi.findAllById(f.getCorsiIds())));
        return u;
    }
}
