/*
 * FILE: IscrizioneConcorrenzaTest.java
 * A COSA SERVE: prova che iscrivere e disiscrivere un utente a un corso regge anche con due richieste nello STESSO istante. Il caso che interessa è la
 * doppia iscrizione (doppio clic, due schede aperte): senza protezione la seconda richiesta passa il controllo «è già iscritto?» (la prima non ha ancora
 * salvato) e poi va a sbattere contro la chiave primaria di utente_corso, con un errore generico del database. Con il blocco sulla riga dell'utente
 * (UtenteRepository.bloccaPerId, preso all'inizio di IscrizioneService.iscrivi e disiscrivi) la seconda richiesta aspetta la prima, poi vede il corso già
 * assegnato e risponde con il messaggio giusto: «è già iscritto».
 * Come UltimoAdminConcorrenzaTest: PostgreSQL vero (Testcontainers; senza Docker il test si salta), NON @Transactional (ogni chiamata fa il suo commit),
 * due thread che partono insieme grazie a un CountDownLatch e più giri, perché una gara dipende dai tempi (è una prova, non una dimostrazione).
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;

import it.palestra.exception.RegolaViolataException;
import it.palestra.model.Abbonamento;
import it.palestra.model.Corso;
import it.palestra.model.Livello;
import it.palestra.model.Ruolo;
import it.palestra.model.TipoAbbonamento;
import it.palestra.model.Utente;
import it.palestra.repository.AbbonamentoRepository;
import it.palestra.repository.CorsoRepository;
import it.palestra.repository.RuoloRepository;
import it.palestra.repository.UtenteRepository;
import it.palestra.service.IscrizioneService;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Il profilo "test" serve per le email finte e per spegnere i compiti programmati; "concorrenza" accende Flyway (schema e vincoli veri).
@SpringBootTest
@ActiveProfiles({"test", "concorrenza"})
@Testcontainers(disabledWithoutDocker = true)
class IscrizioneConcorrenzaTest {

    // Quante volte si ripete la gara (ogni giro riparte da dati nuovi).
    private static final int GIRI = 15;

    @Container
    @ServiceConnection
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:17");

    @Autowired private IscrizioneService service;
    @Autowired private UtenteRepository utenti;
    @Autowired private CorsoRepository corsi;
    @Autowired private RuoloRepository ruoli;
    @Autowired private AbbonamentoRepository abbonamenti;

    // Prima di ogni test si svuotano le tabelle (nell'ordine che i vincoli di chiave esterna richiedono): abbonamenti, utenti (e le loro iscrizioni), corsi.
    @BeforeEach
    void pulisci() {
        abbonamenti.deleteAll();
        utenti.deleteAll();
        corsi.deleteAll();
    }

    // ------------------------------------------------------------------ DOPPIA ISCRIZIONE

    @Test
    @DisplayName("Due iscrizioni insieme allo stesso corso: una riesce, l'altra riceve «è già iscritto» (non un errore del database) e c'è una sola iscrizione")
    void doppiaIscrizioneAlloStessoCorso() throws Exception {
        for (int giro = 1; giro <= GIRI; giro++) {
            pulisci();
            Utente mario = creaIscrittoAbbonato("mario" + giro + "@concorrenza.test");
            Corso yoga = creaCorso("Yoga");

            List<Throwable> esiti = inParallelo(
                    () -> service.iscrivi(mario.getId(), yoga.getId()),
                    () -> service.iscrivi(mario.getId(), yoga.getId()));

            assertThat(esiti.stream().filter(e -> e == null).count())
                    .as("giro %d: doveva riuscire UNA sola iscrizione (esiti: %s)", giro, esiti).isEqualTo(1);
            Throwable fallita = esiti.stream().filter(e -> e != null).findFirst().orElseThrow();
            assertThat(fallita)
                    .as("giro %d: la seconda doveva essere rifiutata dalla REGOLA, non da un errore del database", giro)
                    .isInstanceOf(RegolaViolataException.class)
                    .hasMessageContaining("è già iscritto al corso Yoga");
            assertThat(utenti.findWithRelazioniById(mario.getId()).orElseThrow().getCorsi()).hasSize(1);
        }
    }

    // ------------------------------------------------------------------ CORSI DIVERSI INSIEME

    @Test
    @DisplayName("Due iscrizioni insieme a corsi DIVERSI riescono entrambe: il blocco mette in fila, non rifiuta")
    void dueCorsiDiversiInsieme() throws Exception {
        for (int giro = 1; giro <= GIRI; giro++) {
            pulisci();
            Utente mario = creaIscrittoAbbonato("mario" + giro + "@concorrenza.test");
            Corso yoga = creaCorso("Yoga");
            Corso pilates = creaCorso("Pilates");

            List<Throwable> esiti = inParallelo(
                    () -> service.iscrivi(mario.getId(), yoga.getId()),
                    () -> service.iscrivi(mario.getId(), pilates.getId()));

            assertThat(esiti).as("giro %d: nessuna delle due doveva fallire", giro).containsOnlyNulls();
            assertThat(utenti.findWithRelazioniById(mario.getId()).orElseThrow().getCorsi())
                    .extracting(c -> c.getNome()).containsExactlyInAnyOrder("Yoga", "Pilates");
        }
    }

    // ------------------------------------------------------------------ ISCRIVERE E DISISCRIVERE INSIEME

    @Test
    @DisplayName("Iscrivere e disiscrivere insieme: nessun errore del database, e lo stato finale coincide con l'esito delle due richieste")
    void iscrivereEDisiscrivereInsieme() throws Exception {
        for (int giro = 1; giro <= GIRI; giro++) {
            pulisci();
            Utente mario = creaIscrittoAbbonato("mario" + giro + "@concorrenza.test");
            Corso yoga = creaCorso("Yoga");
            service.iscrivi(mario.getId(), yoga.getId());          // parte già iscritto

            List<Throwable> esiti = inParallelo(
                    () -> service.iscrivi(mario.getId(), yoga.getId()),         // [0] prova a iscriverlo di nuovo
                    () -> service.disiscrivi(mario.getId(), yoga.getId()));     // [1] lo toglie

            // Se la disiscrizione passa per prima, l'iscrizione riesce (finale: iscritto); se passa per seconda, l'iscrizione trova «già iscritto» (finale: non iscritto).
            // In ogni caso la disiscrizione riesce e gli errori possibili sono solo quelli della regola.
            assertThat(esiti.get(1)).as("giro %d: la disiscrizione doveva riuscire", giro).isNull();
            assertThat(esiti.get(0)).as("giro %d: l'iscrizione può riuscire o essere rifiutata dalla regola, mai dal database", giro)
                    .satisfiesAnyOf(e -> assertThat(e).isNull(), e -> assertThat(e).isInstanceOf(RegolaViolataException.class));
            boolean iscritto = !utenti.findWithRelazioniById(mario.getId()).orElseThrow().getCorsi().isEmpty();
            assertThat(iscritto).as("giro %d: stato finale coerente con l'esito dell'iscrizione", giro).isEqualTo(esiti.get(0) == null);
        }
    }

    // ------------------------------------------------------------------ SUPPORTO

    // Esegue le due operazioni in due thread che partono INSIEME e restituisce, per ognuna, l'eccezione lanciata (null se è riuscita).
    private List<Throwable> inParallelo(Runnable primo, Runnable secondo) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch pronti = new CountDownLatch(2);     // i due thread dicono «sono pronto»
            CountDownLatch via = new CountDownLatch(1);        // il test dà il via a entrambi insieme
            Future<Throwable> a = pool.submit(lavoro(pronti, via, primo));
            Future<Throwable> b = pool.submit(lavoro(pronti, via, secondo));
            pronti.await();
            via.countDown();
            return java.util.Arrays.asList(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Throwable> lavoro(CountDownLatch pronti, CountDownLatch via, Runnable operazione) {
        return () -> {
            pronti.countDown();
            via.await();
            try {
                operazione.run();
                return null;
            } catch (Throwable errore) {
                return errore;
            }
        };
    }

    // Un iscritto con un abbonamento attivo oggi (senza, la regola rifiuterebbe l'iscrizione prima ancora della gara).
    private Utente creaIscrittoAbbonato(String email) {
        Utente u = new Utente();
        u.setNome("Mario");
        u.setCognome("Rossi");
        u.setEmail(email);
        u.setRuoli(new HashSet<>(List.of(ruoli.findByNome(Ruolo.ISCRITTO).orElseGet(() -> ruoli.save(new Ruolo(Ruolo.ISCRITTO))))));
        Utente salvato = utenti.save(u);
        abbonamenti.save(new Abbonamento(salvato, TipoAbbonamento.MENSILE, LocalDate.now()));
        return salvato;
    }

    private Corso creaCorso(String nome) {
        return corsi.save(new Corso(nome, null, 60, Livello.PRINCIPIANTE));
    }
}
