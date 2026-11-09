/*
 * FILE: UtenteService.java
 * A COSA SERVE: contiene la logica dell'applicazione sugli utenti (creare, modificare, eliminare, ruoli,
 * password, email duplicate, protezione dell'ultimo admin). Il controller riceve la richiesta HTTP e
 * delega qui; il service parla con i repository.
 * PARAGONE LARAVEL: una classe in app/Services (in Spring è la regola: Controller -> Service -> Repository).
 */
package it.palestra.service;

import it.palestra.exception.RisorsaNonTrovataException;
import it.palestra.model.Corso;
import it.palestra.model.Ruolo;
import it.palestra.model.Utente;
import it.palestra.repository.CorsoRepository;
import it.palestra.repository.RuoloRepository;
import it.palestra.repository.UtenteRepository;
import it.palestra.web.UtenteForm;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder encoder;

    // Dependency injection: Spring vede i parametri del costruttore e passa gli oggetti giusti.
    public UtenteService(UtenteRepository utenti, CorsoRepository corsi,
                         RuoloRepository ruoli, PasswordEncoder encoder) {
        this.utenti = utenti;
        this.corsi = corsi;
        this.ruoli = ruoli;
        this.encoder = encoder;
    }

    // Tutti gli utenti per la lista. readOnly = solo lettura (più efficiente).
    @Transactional(readOnly = true)
    public List<Utente> elenco() {
        return utenti.findAllByOrderByCognomeAscNomeAsc();
    }

    // Un utente per id (con corsi e ruoli), oppure 404 se non esiste (come findOrFail()).
    @Transactional(readOnly = true)
    public Utente trova(Long id) {
        return utenti.findWithCorsiERuoliById(id)
                .orElseThrow(() -> new RisorsaNonTrovataException("Utente " + id + " non trovato"));
    }

    // Tutti i corsi e tutti i ruoli: servono per mostrare le checkbox nel form.
    @Transactional(readOnly = true)
    public List<Corso> tuttiICorsi() {
        return corsi.findAll();
    }

    @Transactional(readOnly = true)
    public List<Ruolo> tuttiIRuoli() {
        return ruoli.findAllByOrderByIdAsc();
    }

    // true se l'email è già usata da UN ALTRO utente. idDaIgnorare = id dell'utente che stiamo modificando
    // (null in creazione): tenere la propria email non è un duplicato (come Rule::unique()->ignore($id)).
    @Transactional(readOnly = true)
    public boolean emailInUso(String email, Long idDaIgnorare) {
        return utenti.findByEmailIgnoreCase(email)
                .map(u -> !u.getId().equals(idDaIgnorare))
                .orElse(false);
    }

    // Controlla le regole sulla password e restituisce il messaggio d'errore, oppure Optional.vuoto se va bene.
    //  - se è scritta: da 8 caratteri e al massimo 72 BYTE (limite di bcrypt, che oltre ignorerebbe il resto);
    //  - se tra i ruoli c'è ADMIN e l'utente non ha ancora una password: è obbligatoria (senza, l'admin non
    //    potrebbe fare login). In modifica, lasciarla vuota significa "non cambiarla".
    @Transactional(readOnly = true)
    public Optional<String> erroreSullaPassword(UtenteForm f, Long id) {
        String password = f.getPassword();
        boolean scritta = password != null && !password.isEmpty();
        if (scritta && (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72)) {
            return Optional.of("La password deve avere da 8 a 72 caratteri");
        }
        if (!scritta && contieneAdmin(f.getRuoliIds())) {
            boolean giaImpostata = id != null && trova(id).getPasswordHash() != null;
            if (!giaImpostata) {
                return Optional.of("La password è obbligatoria per gli amministratori");
            }
        }
        return Optional.empty();
    }

    // Crea un nuovo utente con i dati del form e lo salva (INSERT).
    public Utente crea(UtenteForm form) {
        return utenti.save(applica(new Utente(), form));
    }

    // Aggiorna un utente esistente. Non serve save(): l'entità caricata è "gestita" da Hibernate, che a fine
    // transazione vede le modifiche e fa l'UPDATE da solo. Prima controlla di non togliere ADMIN all'ultimo admin.
    public Utente modifica(Long id, UtenteForm form) {
        Utente u = trova(id);
        if (u.isAdmin() && !contieneAdmin(form.getRuoliIds()) && utenti.countByRuoliNome(Ruolo.ADMIN) <= 1) {
            throw new IllegalStateException("Non puoi togliere il ruolo ADMIN all'ultimo amministratore");
        }
        return applica(u, form);
    }

    // Elimina l'utente (DELETE) e le sue righe nelle pivot (corsi e ruoli); i corsi e i ruoli restano.
    // Rifiuta di eliminare l'ultimo admin: nessuno potrebbe più entrare nel pannello.
    public void elimina(Long id) {
        Utente u = trova(id);
        if (u.isAdmin() && utenti.countByRuoliNome(Ruolo.ADMIN) <= 1) {
            throw new IllegalStateException("Impossibile eliminare l'ultimo amministratore");
        }
        utenti.delete(u);
    }

    // true se tra gli id dei ruoli indicati c'è ADMIN.
    private boolean contieneAdmin(List<Long> ruoliIds) {
        return ruoli.findAllById(ruoliIds).stream().anyMatch(r -> Ruolo.ADMIN.equals(r.getNome()));
    }

    // Metodo privato usato sia da crea() sia da modifica(): copia i dati del form dentro l'entità.
    private Utente applica(Utente u, UtenteForm f) {
        u.setNome(f.getNome().trim());          // trim() toglie gli spazi ai bordi
        u.setCognome(f.getCognome().trim());
        u.setEmail(f.getEmail().trim());
        u.setCorsi(new HashSet<>(corsi.findAllById(f.getCorsiIds())));
        u.setRuoli(new HashSet<>(ruoli.findAllById(f.getRuoliIds())));
        // La password si cambia SOLO se è stata scritta: nel database finisce l'hash bcrypt, mai il testo.
        if (f.getPassword() != null && !f.getPassword().isEmpty()) {
            u.setPasswordHash(encoder.encode(f.getPassword()));
        }
        return u;
    }
}
