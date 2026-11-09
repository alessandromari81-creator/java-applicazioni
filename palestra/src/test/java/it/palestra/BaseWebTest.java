/*
 * FILE: BaseWebTest.java
 * A COSA SERVE: classe di base per tutti i test "web" (quelli con MockMvc). Contiene la configurazione comune e
 * piccoli metodi di aiuto per preparare i dati, così i test veri restano brevi e leggibili.
 * PARAGONE LARAVEL: la classe TestCase da cui estendono i test Feature (con RefreshDatabase e gli helper).
 *
 * COSA FA MOCKMVC: simula le richieste HTTP dentro la stessa JVM, senza avviare Tomcat e senza browser. La
 * richiesta attraversa DAVVERO i filtri di sicurezza, il controller, il service, il database e Thymeleaf; poi si
 * controlla la risposta (codice, redirect, vista usata, dati nel model). È come $this->get('/utenti')->assertOk().
 */
package it.palestra;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

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
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

// @SpringBootTest: avvia l'applicazione completa (come in produzione, ma con configurazione "test").
// @AutoConfigureMockMvc: prepara l'oggetto MockMvc da usare nei test.
// @ActiveProfiles("test"): carica application-test.properties, cioè H2 in memoria e niente Flyway/Docker.
// @Transactional: ogni test gira in una transazione che alla fine viene ANNULLATA (rollback): i dati creati in un
// test spariscono e non disturbano gli altri (come il trait RefreshDatabase / DatabaseTransactions di Laravel).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class BaseWebTest {

    // Email dell'admin creato all'avvio da AdminIniziale (da app.admin.email in application.properties).
    static final String EMAIL_ADMIN = "admin@palestra.local";

    // @Autowired = "Spring, mettimi qui l'oggetto" (dependency injection anche nei test).
    @Autowired protected MockMvc mockMvc;
    @Autowired protected UtenteRepository utenti;
    @Autowired protected CorsoRepository corsi;
    @Autowired protected RuoloRepository ruoli;
    @Autowired protected AbbonamentoRepository abbonamenti;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected EntityManager em;   // serve a "svuotare" la cache di Hibernate (vedi ricarica())

    // Simula una richiesta fatta da un amministratore già loggato (come actingAs($admin) in Laravel).
    // Non passa dal form di login: dichiara solo "chi sono" e quale ruolo ho, per provare il resto.
    protected RequestPostProcessor comeAdmin() {
        return user(EMAIL_ADMIN).roles("ADMIN");
    }

    // Restituisce il ruolo col nome dato; se nel database di test non esiste ancora lo crea.
    // (Nel database di test c'è solo ADMIN, creato da AdminIniziale: gli altri li creiamo qui, quando servono.)
    protected Ruolo ruolo(String nome) {
        return ruoli.findByNome(nome).orElseGet(() -> ruoli.save(new Ruolo(nome)));
    }

    // Crea e salva un utente con i ruoli indicati (senza password: non può fare login).
    protected Utente creaUtente(String nome, String cognome, String email, Ruolo... ruoliUtente) {
        Utente u = new Utente();
        u.setNome(nome);
        u.setCognome(cognome);
        u.setEmail(email);
        u.setRuoli(new HashSet<>(Arrays.asList(ruoliUtente)));
        return utenti.save(u);
    }

    // Crea e salva un corso di prova.
    protected Corso creaCorso(String nome) {
        return corsi.save(new Corso(nome, "Descrizione di prova", 60, Livello.PRINCIPIANTE));
    }

    // Crea e salva un abbonamento che parte il giorno indicato (fine e prezzo li calcola il costruttore).
    // NOTA: salvando DIRETTAMENTE dal repository non passiamo dalle regole del service (niente passato, niente
    // sovrapposizioni): per questo qui si possono creare anche abbonamenti già scaduti, utili per i test.
    protected Abbonamento creaAbbonamento(Utente utente, TipoAbbonamento tipo, LocalDate inizio) {
        return abbonamenti.save(new Abbonamento(utente, tipo, inizio));
    }

    // L'admin creato all'avvio (l'unico ADMIN presente a inizio di ogni test).
    protected Utente adminIniziale() {
        return utenti.findByEmailIgnoreCase(EMAIL_ADMIN).orElseThrow();
    }

    // Allinea Java e database dopo una richiesta che modifica dati.
    // PERCHÉ SERVE: Hibernate tiene in memoria (nella "sessione") gli oggetti già caricati. Dopo che il controller ha
    // salvato qualcosa, se rileggessimo subito dal repository potremmo ricevere la copia vecchia in memoria invece
    // del dato vero. flush() manda al database tutte le modifiche in sospeso; clear() svuota la memoria, così la
    // lettura successiva fa una vera SELECT. È come $model->refresh() in Eloquent.
    protected void ricarica() {
        em.flush();
        em.clear();
    }
}
