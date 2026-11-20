/*
 * FILE: UltimoAdminConcorrenzaTest.java
 * A COSA SERVE: prova che la regola "non si può togliere né eliminare l'ULTIMO amministratore" regge anche quando due richieste
 * arrivano nello STESSO istante. Senza protezione, il service conta gli admin e poi modifica: due richieste insieme leggono
 * entrambe "ce ne sono due", ognuna toglie il suo, e alla fine non resta nessun admin (nessuno potrebbe più entrare nel pannello).
 *
 * COME FUNZIONA:
 *   - usa un PostgreSQL VERO (Testcontainers) perché i blocchi sulle righe dipendono dal database: H2 li gestisce in modo diverso
 *     e il test proverebbe meno. Richiede Docker acceso; senza Docker il test viene SALTATO (disabledWithoutDocker);
 *   - NON è @Transactional: ogni chiamata al service fa il suo commit, come in produzione (altrimenti le due richieste
 *     vivrebbero nella stessa transazione di test e non ci sarebbe nessuna gara);
 *   - due thread partono insieme grazie a un "via" comune (CountDownLatch) e chiamano il service;
 *   - la prova si ripete più volte (GIRI), perché una gara dipende dai tempi: con il codice senza blocco basta un giro sfortunato per
 *     far fallire il test, con il blocco deve reggere sempre. È una prova, non una dimostrazione.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;

import it.palestra.exception.RegolaViolataException;
import it.palestra.model.Ruolo;
import it.palestra.model.Utente;
import it.palestra.repository.RuoloRepository;
import it.palestra.repository.UtenteRepository;
import it.palestra.service.UtenteService;
import it.palestra.web.UtenteForm;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Il profilo "test" serve per le email finte (EmailFinte) e per spegnere i compiti programmati; "concorrenza" accende Flyway.
@SpringBootTest
@ActiveProfiles({"test", "concorrenza"})
@Testcontainers(disabledWithoutDocker = true)
class UltimoAdminConcorrenzaTest {

    // Quante volte si ripete la gara (ogni giro riparte da due admin).
    private static final int GIRI = 20;

    // Il contenitore PostgreSQL: parte una volta per questa classe. @ServiceConnection fa collegare da solo l'applicazione.
    @Container
    @ServiceConnection
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:17");

    @Autowired private UtenteService service;
    @Autowired private UtenteRepository utenti;
    @Autowired private RuoloRepository ruoli;

    private Utente primo;
    private Utente secondo;

    // Prima di ogni test: si svuota la tabella degli utenti (compreso l'admin iniziale) e si creano esattamente DUE admin.
    // Si scrive direttamente dal repository: qui interessa la situazione di partenza, non le regole del service.
    @BeforeEach
    void duePersoneAdmin() {
        utenti.deleteAll();
        primo = creaAdmin("primo.admin@concorrenza.test");
        secondo = creaAdmin("secondo.admin@concorrenza.test");
    }

    // Due modifiche insieme, ognuna toglie il ruolo ADMIN a un admin diverso: ne deve riuscire una sola.
    @Test
    void dueModificheInsiemeNonLasciamoSenzaAdmin() throws Exception {
        provaLaGara(a -> service.modifica(a.getId(), formSenzaRuoli(a)),
                    b -> service.modifica(b.getId(), formSenzaRuoli(b)));
    }

    // Due eliminazioni insieme di admin diversi: ne deve riuscire una sola.
    @Test
    void dueEliminazioniInsiemeNonLasciamoSenzaAdmin() throws Exception {
        provaLaGara(a -> service.elimina(a.getId()),
                    b -> service.elimina(b.getId()));
    }

    // Una modifica (toglie ADMIN al primo) e un'eliminazione (del secondo) insieme: ne deve riuscire una sola.
    @Test
    void modificaEdEliminazioneInsiemeNonLasciamoSenzaAdmin() throws Exception {
        provaLaGara(a -> service.modifica(a.getId(), formSenzaRuoli(a)),
                    b -> service.elimina(b.getId()));
    }

    // Controllo che il blocco non impedisca l'uso normale: una dopo l'altra, la prima operazione riesce e la seconda no.
    @Test
    void inSequenzaPassaSoloLaPrima() {
        service.modifica(primo.getId(), formSenzaRuoli(primo));
        try {
            service.modifica(secondo.getId(), formSenzaRuoli(secondo));
            throw new AssertionError("La seconda modifica doveva essere rifiutata: è l'ultimo admin");
        } catch (RegolaViolataException atteso) {
            assertThat(utenti.countByRuoliNome(Ruolo.ADMIN)).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------ SUPPORTO

    // Ripete la gara GIRI volte. A ogni giro riparte da due admin, lancia le due operazioni insieme e controlla che:
    //   - ne sia riuscita esattamente UNA (l'altra deve essere rifiutata con RegolaViolataException);
    //   - nel database sia rimasto esattamente UN admin.
    private void provaLaGara(Consumer<Utente> operazioneA, Consumer<Utente> operazioneB) throws Exception {
        for (int giro = 1; giro <= GIRI; giro++) {
            if (giro > 1) {
                duePersoneAdmin();    // ricrea la situazione di partenza
            }
            boolean riuscitaA;
            boolean riuscitaB;
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch pronti = new CountDownLatch(2);   // i due thread dicono "sono pronto"
                CountDownLatch via = new CountDownLatch(1);       // il test dà il via a entrambi insieme
                Future<Boolean> a = pool.submit(operazione(pronti, via, () -> operazioneA.accept(primo)));
                Future<Boolean> b = pool.submit(operazione(pronti, via, () -> operazioneB.accept(secondo)));
                pronti.await();
                via.countDown();
                riuscitaA = a.get(30, TimeUnit.SECONDS);
                riuscitaB = b.get(30, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }
            long rimasti = utenti.countByRuoliNome(Ruolo.ADMIN);
            assertThat(rimasti)
                    .as("giro %d: admin rimasti (riuscite: A=%s, B=%s)", giro, riuscitaA, riuscitaB)
                    .isEqualTo(1);
            assertThat(riuscitaA ^ riuscitaB)
                    .as("giro %d: doveva riuscire UNA sola operazione (A=%s, B=%s)", giro, riuscitaA, riuscitaB)
                    .isTrue();
        }
    }

    // Prepara il lavoro di un thread: aspetta il via, poi esegue l'operazione. Restituisce true se è riuscita, false se
    // il service l'ha rifiutata per la regola dell'ultimo admin. Altri errori (es. un deadlock) escono e fanno fallire il test.
    private Callable<Boolean> operazione(CountDownLatch pronti, CountDownLatch via, Runnable lavoro) {
        return () -> {
            pronti.countDown();
            via.await();
            try {
                lavoro.run();
                return true;
            } catch (RegolaViolataException rifiutata) {
                return false;
            }
        };
    }

    // Crea un utente con il solo ruolo ADMIN (ruolo già presente dalle migration).
    private Utente creaAdmin(String email) {
        Utente u = new Utente();
        u.setNome("Admin");
        u.setCognome("Prova");
        u.setEmail(email);
        u.setRuoli(new HashSet<>(List.of(ruoli.findByNome(Ruolo.ADMIN).orElseThrow())));
        return utenti.save(u);
    }

    // Il form di modifica con gli stessi dati dell'utente ma SENZA nessun ruolo (quindi senza ADMIN).
    private UtenteForm formSenzaRuoli(Utente u) {
        UtenteForm f = new UtenteForm();
        f.setNome(u.getNome());
        f.setCognome(u.getCognome());
        f.setEmail(u.getEmail());
        f.setRuoliIds(List.of());
        return f;
    }
}
