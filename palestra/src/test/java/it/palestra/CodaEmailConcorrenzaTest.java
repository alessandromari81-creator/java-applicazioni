/*
 * FILE: CodaEmailConcorrenzaTest.java
 * A COSA SERVE: prova che con DUE lavoratori che girano nello stesso istante (come due istanze dell'app) ogni email della coda parte UNA sola volta: né perse né doppie.
 * Il meccanismo è la «reclamazione»: prima di spedire, ogni lavoratore prende la riga con un UPDATE condizionato (where stato = DA_INVIARE) che riesce a uno solo.
 * Come UltimoAdminConcorrenzaTest: PostgreSQL vero (Testcontainers; senza Docker il test si salta), NON @Transactional (ogni operazione fa il suo commit), due thread che partono
 * insieme con un CountDownLatch e più giri, perché una gara dipende dai tempi (è una prova, non una dimostrazione).
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;

import it.palestra.repository.EmailInCodaRepository;
import it.palestra.service.CodaEmailService;
import it.palestra.service.SpedizioneEmailProgrammata;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// "concorrenza" accende Flyway (schema vero, compresa la migration V14); la proprietà accende la coda delle email.
@SpringBootTest
@ActiveProfiles({"test", "concorrenza"})
@TestPropertySource(properties = "app.email.coda.attiva=true")
@Testcontainers(disabledWithoutDocker = true)
class CodaEmailConcorrenzaTest {

    private static final int GIRI = 10;
    private static final int EMAIL_PER_GIRO = 60;

    @Container
    @ServiceConnection
    static PostgreSQLContainer db = new PostgreSQLContainer("postgres:17");

    @Autowired private SpedizioneEmailProgrammata spedizione;
    @Autowired private CodaEmailService coda;
    @Autowired private EmailInCodaRepository repo;
    @Autowired private EmailFinte emailFinte;

    @BeforeEach
    void pulisci() {
        repo.deleteAll();
        emailFinte.svuota();
        emailFinte.faiFallire(false);
        emailFinte.rifiutaSoloPer(null);
    }

    @Test
    @DisplayName("Due lavoratori insieme: ogni email parte esattamente una volta e la coda si svuota")
    void dueLavoratoriInsieme() throws Exception {
        for (int giro = 1; giro <= GIRI; giro++) {
            pulisci();
            Instant ora = Instant.now();
            for (int i = 0; i < EMAIL_PER_GIRO; i++) {
                coda.accoda("persona" + i + "@concorrenza.test", "Oggetto", "<p>h</p>", "t", ora);
            }

            // Ogni lavoratore fa giri (20 email l'uno) finché ce n'è; partono insieme e si contendono le stesse righe.
            ExecutorService pool = Executors.newFixedThreadPool(2);
            int spedite;
            try {
                CountDownLatch pronti = new CountDownLatch(2);
                CountDownLatch via = new CountDownLatch(1);
                Callable<Integer> lavoratore = () -> {
                    pronti.countDown();
                    via.await();
                    int mie = 0;
                    for (int k = 0; k < 10; k++) {
                        mie += spedizione.eseguiGiroAlle(ora.plusSeconds(1));
                    }
                    return mie;
                };
                Future<Integer> a = pool.submit(lavoratore);
                Future<Integer> b = pool.submit(lavoratore);
                pronti.await();
                via.countDown();
                spedite = a.get(60, TimeUnit.SECONDS) + b.get(60, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
            }

            assertThat(spedite).as("giro %d: la somma delle email spedite dai due lavoratori", giro).isEqualTo(EMAIL_PER_GIRO);
            assertThat(emailFinte.tutte()).as("giro %d: email arrivate al server di posta", giro).hasSize(EMAIL_PER_GIRO);
            assertThat(emailFinte.tutte().stream().map(m -> m.destinatario()).distinct().count())
                    .as("giro %d: nessun destinatario ha ricevuto la stessa email due volte", giro).isEqualTo(EMAIL_PER_GIRO);
            assertThat(repo.count()).as("giro %d: la coda è vuota", giro).isZero();
        }
    }

    @Test
    @DisplayName("Una riga IN_CORSO scaduta viene ripresa da un lavoratore e spedita una volta sola, anche con due lavoratori")
    void rigaScadutaRipresa() throws Exception {
        Instant ora = Instant.now();
        Long id = coda.accoda("mario@concorrenza.test", "Oggetto", "<p>h</p>", "t", ora).getId();
        coda.reclama(id, ora);                                   // un'istanza l'ha presa e poi è morta

        Instant dopo = ora.plus(Duration.ofMinutes(30));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch via = new CountDownLatch(1);
            Callable<Integer> lavoratore = () -> {
                via.await();
                return spedizione.eseguiGiroAlle(dopo);
            };
            Future<Integer> a = pool.submit(lavoratore);
            Future<Integer> b = pool.submit(lavoratore);
            via.countDown();
            assertThat(a.get(30, TimeUnit.SECONDS) + b.get(30, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(emailFinte.per("mario@concorrenza.test")).hasSize(1);
        assertThat(repo.count()).isZero();
    }
}
