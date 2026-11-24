/*
 * FILE: CodaEmailTest.java
 * A COSA SERVE: verifica la coda delle email con la coda ACCESA (app.email.coda.attiva=true, che nei test è altrimenti spenta): la richiesta dell'utente scrive
 * solo una riga in email_in_coda e il lavoratore (SpedizioneEmailProgrammata) la spedisce a un giro successivo. Si prova:
 *   - registrazione e «password dimenticata»: nessuna email parte nella richiesta, la persona vede la solita risposta, al giro l'email parte e la riga sparisce;
 *   - server di posta spento: la riga resta, conta i tentativi, aspetta 1, 2, 4... minuti e riparte quando il server torna; dopo 5 fallimenti diventa FALLITA;
 *   - una riga rimasta IN_CORSO da troppo tempo (l'app si è fermata a metà) viene ripresa;
 *   - la pulizia notturna cancella le righe FALLITA vecchie (contengono il link con il token) e non quelle recenti;
 *   - il compito programmato esiste ed è agganciato a @Scheduled.
 * Il compito programmato nei test non parte da solo (app.pulizia.attiva=false): i test chiamano eseguiGiroAlle(ora) a mano scegliendo l'ora, senza fermare nessun orologio.
 * Convenzione dei commenti: PREPARO / ESEGUO / VERIFICO.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.palestra.model.EmailInCoda;
import it.palestra.model.StatoEmail;
import it.palestra.repository.EmailInCodaRepository;
import it.palestra.service.CodaEmailService;
import it.palestra.service.PuliziaProgrammata;
import it.palestra.service.SpedizioneEmailProgrammata;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.email.coda.attiva=true")
class CodaEmailTest extends BaseWebTest {

    @Autowired SpedizioneEmailProgrammata spedizione;
    @Autowired CodaEmailService coda;
    @Autowired EmailInCodaRepository repo;
    @Autowired PuliziaProgrammata pulizia;

    private final Instant ora = Instant.now();

    @BeforeEach
    void preparo() {
        ruolo("ISCRITTO");                 // la registrazione assegna questo ruolo alla conferma
        emailFinte.faiFallire(false);
        emailFinte.rifiutaSoloPer(null);
        emailFinte.svuota();
    }

    @AfterEach
    void ripristino() {
        emailFinte.faiFallire(false);
        emailFinte.rifiutaSoloPer(null);
    }

    // Mette in coda una email di prova e ne restituisce l'id.
    private Long accoda(String destinatario) {
        return coda.accoda(destinatario, "Oggetto", "<p>html https://esempio.test/x?token=abc</p>", "testo https://esempio.test/x?token=abc", ora).getId();
    }

    // ------------------------------------------------------------------ LE RICHIESTE METTONO IN CODA

    @Test
    @DisplayName("Registrazione: la richiesta scrive una riga e NON spedisce; al giro l'email parte con il suo link e la riga sparisce")
    void registrazione() throws Exception {
        // ESEGUO
        mockMvc.perform(post("/registrazione").with(csrf())
                        .param("nome", "Mario").param("cognome", "Rossi").param("email", "mario@example.com")
                        .param("indirizzo", "Via Roma 1").param("cap", "00100").param("citta", "Roma").param("accettoPrivacy", "true"))
                .andExpect(status().is3xxRedirection());

        // VERIFICO: nessuna email partita, una riga in attesa con il destinatario giusto.
        assertThat(emailFinte.tutte()).isEmpty();
        List<EmailInCoda> righe = repo.findAll();
        assertThat(righe).hasSize(1);
        assertThat(righe.get(0).getDestinatario()).isEqualTo("mario@example.com");
        assertThat(righe.get(0).getStato()).isEqualTo(StatoEmail.DA_INVIARE);

        // ESEGUO il giro: l'email parte...
        assertThat(spedizione.eseguiGiroAlle(Instant.now())).isEqualTo(1);

        // ...con il link da aprire, e la riga (che conteneva il token) non c'è più.
        assertThat(emailFinte.per("mario@example.com")).hasSize(1);
        assertThat(emailFinte.ultima().link()).contains("token=");
        assertThat(repo.count()).isZero();
    }

    @Test
    @DisplayName("«Password dimenticata»: l'email di reset passa dalla coda e il link ricevuto è quello vero")
    void passwordDimenticata() throws Exception {
        creaUtenteConPassword("Mario", "Rossi", "mario@example.com", "password-vecchia-1", ruolo("ISCRITTO"));

        mockMvc.perform(post("/password-dimenticata").with(csrf()).param("email", "mario@example.com")).andExpect(status().is3xxRedirection());

        assertThat(emailFinte.tutte()).isEmpty();
        assertThat(repo.count()).isEqualTo(1);
        spedizione.eseguiGiroAlle(Instant.now());
        assertThat(emailFinte.per("mario@example.com")).hasSize(1);
        assertThat(emailFinte.ultima().token()).isNotBlank();
        assertThat(repo.count()).isZero();
    }

    @Test
    @DisplayName("Un indirizzo sconosciuto non mette niente in coda: la risposta resta la stessa e non si scopre chi è registrato")
    void indirizzoSconosciuto() throws Exception {
        mockMvc.perform(post("/password-dimenticata").with(csrf()).param("email", "sconosciuto@example.com"))
                .andExpect(status().is3xxRedirection());

        assertThat(repo.count()).isZero();
        assertThat(spedizione.eseguiGiroAlle(Instant.now())).isZero();
    }

    // ------------------------------------------------------------------ SERVER DI POSTA SPENTO: SI RIPROVA

    @Test
    @DisplayName("Server di posta spento: la riga resta, conta il tentativo, aspetta un minuto; quando il server torna l'email parte e la riga sparisce")
    void serverSpentoEPoiAcceso() {
        Long id = accoda("mario@example.com");
        emailFinte.faiFallire(true);

        assertThat(spedizione.eseguiGiroAlle(ora)).isZero();

        EmailInCoda dopo = repo.findById(id).orElseThrow();
        assertThat(dopo.getStato()).isEqualTo(StatoEmail.DA_INVIARE);
        assertThat(dopo.getTentativi()).isEqualTo(1);
        assertThat(dopo.getProssimoTentativo()).isEqualTo(ora.plus(Duration.ofMinutes(1)));
        assertThat(dopo.getUltimoErrore()).contains("IllegalStateException");

        // Troppo presto: la riga aspetta ancora e non viene nemmeno provata.
        assertThat(spedizione.eseguiGiroAlle(ora.plusSeconds(30))).isZero();
        assertThat(repo.findById(id).orElseThrow().getTentativi()).isEqualTo(1);

        // Il server torna e passa il minuto: l'email parte.
        emailFinte.faiFallire(false);
        assertThat(spedizione.eseguiGiroAlle(ora.plus(Duration.ofMinutes(1)))).isEqualTo(1);
        assertThat(emailFinte.per("mario@example.com")).hasSize(1);
        assertThat(repo.count()).isZero();
    }

    @Test
    @DisplayName("Dopo 5 fallimenti la riga diventa FALLITA (con attese di 1, 2, 4 e 8 minuti) e non si riprova più")
    void dopoCinqueFallimenti() {
        Long id = accoda("mario@example.com");
        emailFinte.faiFallire(true);

        Instant t = ora;
        List<Duration> attese = new ArrayList<>();
        for (int giro = 1; giro <= 5; giro++) {
            spedizione.eseguiGiroAlle(t);
            EmailInCoda riga = repo.findById(id).orElseThrow();
            if (giro < 5) {
                assertThat(riga.getStato()).isEqualTo(StatoEmail.DA_INVIARE);
                attese.add(Duration.between(t, riga.getProssimoTentativo()));
                t = riga.getProssimoTentativo();
            }
        }

        EmailInCoda finale = repo.findById(id).orElseThrow();
        assertThat(attese).containsExactly(Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(4), Duration.ofMinutes(8));
        assertThat(finale.getStato()).isEqualTo(StatoEmail.FALLITA);
        assertThat(finale.getTentativi()).isEqualTo(5);

        emailFinte.faiFallire(false);       // anche con il server tornato, una FALLITA non si riprova
        assertThat(spedizione.eseguiGiroAlle(t.plus(Duration.ofDays(1)))).isZero();
        assertThat(emailFinte.tutte()).isEmpty();
    }

    @Test
    @DisplayName("Un'email che fallisce non blocca le altre: la seconda parte, la prima resta in coda per un nuovo tentativo")
    void unaFallitaNonBloccaLeAltre() {
        Long prima = accoda("prima@example.com");
        accoda("seconda@example.com");
        emailFinte.rifiutaSoloPer("prima@example.com");        // il server di posta rifiuta solo questo indirizzo

        assertThat(spedizione.eseguiGiroAlle(ora)).as("è partita solo la seconda").isEqualTo(1);

        assertThat(emailFinte.tutte()).extracting(m -> m.destinatario()).containsExactly("seconda@example.com");
        EmailInCoda rimasta = repo.findById(prima).orElseThrow();
        assertThat(rimasta.getStato()).isEqualTo(StatoEmail.DA_INVIARE);
        assertThat(rimasta.getTentativi()).isEqualTo(1);
        assertThat(repo.count()).as("resta solo la riga della prima").isEqualTo(1);
    }

    // ------------------------------------------------------------------ L'APP SI FERMA A META'

    @Test
    @DisplayName("Una riga rimasta IN_CORSO da oltre 10 minuti (l'app si è fermata a metà) torna da inviare e viene spedita; prima dei 10 minuti no")
    void rigaInCorsoScaduta() {
        Long id = accoda("mario@example.com");
        assertThat(coda.reclama(id, ora)).isTrue();                  // «qualcuno» l'ha presa e poi si è fermato
        assertThat(repo.findById(id).orElseThrow().getStato()).isEqualTo(StatoEmail.IN_CORSO);

        assertThat(spedizione.eseguiGiroAlle(ora.plus(Duration.ofMinutes(5)))).as("dopo 5 minuti è ancora di qualcuno").isZero();
        assertThat(repo.findById(id).orElseThrow().getStato()).isEqualTo(StatoEmail.IN_CORSO);

        assertThat(spedizione.eseguiGiroAlle(ora.plus(Duration.ofMinutes(11)))).as("dopo 11 minuti torna in coda e parte").isEqualTo(1);
        assertThat(emailFinte.per("mario@example.com")).hasSize(1);
        assertThat(repo.count()).isZero();
    }

    @Test
    @DisplayName("Reclamare: riesce una volta sola; la seconda volta la riga è già di qualcuno")
    void reclamareUnaVoltaSola() {
        Long id = accoda("mario@example.com");

        assertThat(coda.reclama(id, ora)).isTrue();
        assertThat(coda.reclama(id, ora)).isFalse();
    }

    @Test
    @DisplayName("Ogni giro spedisce al massimo 20 email (il gruppo): le altre aspettano il giro dopo")
    void ilGruppo() {
        for (int i = 0; i < 45; i++) {
            accoda("persona" + i + "@example.com");
        }

        assertThat(spedizione.eseguiGiroAlle(ora)).isEqualTo(20);
        assertThat(spedizione.eseguiGiroAlle(ora)).isEqualTo(20);
        assertThat(spedizione.eseguiGiroAlle(ora)).isEqualTo(5);
        assertThat(spedizione.eseguiGiroAlle(ora)).isZero();
        assertThat(emailFinte.tutte()).hasSize(45);
    }

    // ------------------------------------------------------------------ PULIZIA

    @Test
    @DisplayName("La pulizia notturna cancella le FALLITA vecchie (hanno il token nel testo) e lascia le recenti e le da inviare")
    void pulizia() {
        Instant adesso = Instant.now();
        Long vecchia = coda.accoda("vecchia@example.com", "O", "<p>h</p>", "t", adesso.minus(Duration.ofHours(30))).getId();
        coda.segnaFallita(vecchia, adesso, 1, "errore");                       // FALLITA, creata 30 ore fa
        Long recente = coda.accoda("recente@example.com", "O", "<p>h</p>", "t", adesso).getId();
        coda.segnaFallita(recente, adesso, 1, "errore");                       // FALLITA, creata adesso
        Long daInviare = coda.accoda("attesa@example.com", "O", "<p>h</p>", "t", adesso.minus(Duration.ofHours(30))).getId();   // vecchia ma NON fallita
        assertThat(repo.findById(vecchia).orElseThrow().getStato()).isEqualTo(StatoEmail.FALLITA);

        pulizia.puliziaRefreshTokenScaduti();

        assertThat(repo.findById(vecchia)).as("la FALLITA di 30 ore fa è stata cancellata").isEmpty();
        assertThat(repo.findById(recente)).as("la FALLITA di adesso resta (si può ancora capire cosa è successo)").isPresent();
        assertThat(repo.findById(daInviare)).as("una DA_INVIARE non si cancella mai di notte").isPresent();
    }

    // ------------------------------------------------------------------ IL COMPITO PROGRAMMATO

    @Test
    @DisplayName("Il giro è agganciato a @Scheduled con una pausa dopo ogni esecuzione (fixedDelay)")
    void ilGiroEProgrammato() throws Exception {
        Method giro = SpedizioneEmailProgrammata.class.getMethod("eseguiGiro");

        Scheduled programmazione = giro.getAnnotation(Scheduled.class);

        assertThat(programmazione).as("il metodo eseguiGiro() deve avere @Scheduled").isNotNull();
        assertThat(programmazione.fixedDelayString()).contains("app.email.coda.intervallo-ms");
    }
}
