/*
 * FILE: IstruttoriTest.java
 * A COSA SERVE: verifica la relazione tra corsi e ISTRUTTORI e le sue regole:
 *   - un corso ha al massimo UN istruttore; un istruttore può tenere PIÙ corsi (uno-a-molti);
 *   - come istruttore si può scegliere solo chi ha il ruolo ISTRUTTORE;
 *   - un istruttore NON può essere iscritto come allievo a un corso che tiene lui stesso (controllo in entrambe
 *     le direzioni: quando si sceglie l'istruttore e quando si iscrive l'utente);
 *   - finché tiene dei corsi, a un istruttore non si può togliere il ruolo né eliminare l'utente.
 * Convenzione dei commenti: PREPARO / ESEGUO / VERIFICO.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import it.palestra.model.Corso;
import it.palestra.model.Utente;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IstruttoriTest extends BaseWebTest {

    // ---------------------------------------------------------------- METODI DI AIUTO

    // Crea un utente con ruolo ISTRUTTORE (senza corsi e senza password).
    private Utente creaIstruttore(String nome, String cognome, String email) {
        return creaUtente(nome, cognome, email, ruolo("ISTRUTTORE"));
    }

    // Crea un corso e gli assegna l'istruttore. Poi ricarica(), così l'istruttore "vede" subito il corso nella sua
    // lista corsiInsegnati (Hibernate tiene in memoria gli oggetti già letti: vedi BaseWebTest.ricarica()).
    // L'istruttore si assegna sul CORSO (corso.setIstruttore), che è il lato che scrive la colonna istruttore_id.
    private Corso corsoTenutoDa(String nomeCorso, Utente istruttore) {
        Corso c = creaCorso(nomeCorso);
        c.setIstruttore(istruttore);
        corsi.save(c);
        ricarica();
        return c;
    }

    // Richiesta di modifica di un corso, con o senza istruttore (istruttoreId null = campo non inviato).
    private org.springframework.test.web.servlet.ResultActions modificaCorso(Corso c, Long istruttoreId) throws Exception {
        var richiesta = post("/corsi/" + c.getId()).with(comeAdmin()).with(csrf())
                .param("nome", c.getNome()).param("durataMinuti", "60").param("livello", "PRINCIPIANTE");
        if (istruttoreId != null) richiesta.param("istruttoreId", istruttoreId.toString());
        return mockMvc.perform(richiesta);
    }

    // ------------------------------------------------- SCEGLIERE L'ISTRUTTORE DI UN CORSO

    @Test
    @DisplayName("Si può assegnare a un corso un utente con ruolo ISTRUTTORE")
    void assegnareUnIstruttoreValido() throws Exception {
        Utente gino = creaIstruttore("Gino", "Verdi", "gino@example.com");
        Corso yoga = creaCorso("Yoga");

        modificaCorso(yoga, gino.getId())
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("messaggio", "Corso aggiornato"));

        ricarica();
        Corso dopo = corsi.findWithIstruttoreById(yoga.getId()).orElseThrow();
        assertThat(dopo.getIstruttore().getId()).isEqualTo(gino.getId());
    }

    @Test
    @DisplayName("Si può scegliere l'istruttore anche creando il corso")
    void creareUnCorsoConIstruttore() throws Exception {
        Utente gino = creaIstruttore("Gino", "Verdi", "gino@example.com");

        mockMvc.perform(post("/corsi").with(comeAdmin()).with(csrf())
                        .param("nome", "Zumba").param("durataMinuti", "55").param("livello", "INTERMEDIO")
                        .param("istruttoreId", gino.getId().toString()))
                .andExpect(status().is3xxRedirection());

        ricarica();
        Corso salvato = corsi.findAllByOrderByNomeAsc().stream()
                .filter(c -> c.getNome().equals("Zumba")).findFirst().orElseThrow();
        assertThat(salvato.getIstruttore().getEmail()).isEqualTo("gino@example.com");
    }

    @Test
    @DisplayName("Un corso può restare senza istruttore")
    void unCorsoPuoNonAvereIstruttore() throws Exception {
        Corso yoga = creaCorso("Yoga");

        modificaCorso(yoga, null)                       // campo istruttoreId non inviato
                .andExpect(status().is3xxRedirection());

        ricarica();
        assertThat(corsi.findWithIstruttoreById(yoga.getId()).orElseThrow().getIstruttore()).isNull();
    }

    @Test
    @DisplayName("Un istruttore può tenere più corsi (relazione uno-a-molti)")
    void unIstruttoreTienePiuCorsi() throws Exception {
        Utente gino = creaIstruttore("Gino", "Verdi", "gino@example.com");
        Corso yoga = creaCorso("Yoga");
        Corso pilates = creaCorso("Pilates");

        modificaCorso(yoga, gino.getId()).andExpect(status().is3xxRedirection());
        modificaCorso(pilates, gino.getId()).andExpect(status().is3xxRedirection());

        // Lato Utente la relazione si legge come lista dei corsi tenuti: "corsiInsegnati".
        ricarica();
        Utente dopo = utenti.findWithRelazioniById(gino.getId()).orElseThrow();
        assertThat(dopo.getCorsiInsegnati()).extracting(corso -> corso.getNome()).containsExactlyInAnyOrder("Yoga", "Pilates");
    }

    @Test
    @DisplayName("Chi non ha il ruolo ISTRUTTORE non può essere scelto come istruttore")
    void serveIlRuoloIstruttore() throws Exception {
        // PREPARO: un utente solo ISCRITTO; la richiesta è costruita a mano (la tendina non lo mostrerebbe).
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        Corso yoga = creaCorso("Yoga");

        modificaCorso(yoga, mario.getId())
                .andExpect(status().isOk())                                     // form riproposto, non salvato
                .andExpect(view().name("corsi/form"))
                .andExpect(model().attributeHasFieldErrorCode("corsoForm", "istruttoreId", "non-valido"))
                .andExpect(content().string(containsString("non ha il ruolo ISTRUTTORE")));

        ricarica();
        assertThat(corsi.findWithIstruttoreById(yoga.getId()).orElseThrow().getIstruttore()).isNull();
    }

    @Test
    @DisplayName("Un istruttore inesistente è rifiutato")
    void istruttoreInesistente() throws Exception {
        Corso yoga = creaCorso("Yoga");

        modificaCorso(yoga, 999999L)
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("corsoForm", "istruttoreId", "non-valido"));
    }

    @Test
    @DisplayName("La tendina del form corso elenca SOLO gli utenti con ruolo ISTRUTTORE")
    void laTendinaMostraSoloGliIstruttori() throws Exception {
        creaIstruttore("Gino", "Verdi", "gino@example.com");
        creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));

        @SuppressWarnings("unchecked")
        List<Utente> istruttori = (List<Utente>) mockMvc.perform(get("/corsi/nuovo").with(comeAdmin()))
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("istruttori");

        assertThat(istruttori).extracting(utente -> utente.getEmail()).containsExactly("gino@example.com");
    }

    // ------------------------------- UN ISTRUTTORE NON PUÒ ESSERE ISCRITTO AL CORSO CHE TIENE

    @Test
    @DisplayName("Non si può nominare istruttore di un corso chi vi è già iscritto come allievo")
    void nonSiNominaIstruttoreChiELIscritto() throws Exception {
        // PREPARO: Gino ha il ruolo ISTRUTTORE ed è iscritto, come allievo, al corso Yoga.
        Utente gino = creaIstruttore("Gino", "Verdi", "gino@example.com");
        Corso yoga = creaCorso("Yoga");
        gino.setCorsi(new HashSet<>(Set.of(yoga)));
        utenti.save(gino);

        // ESEGUO: provo a nominarlo istruttore di Yoga.
        modificaCorso(yoga, gino.getId())
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("corsoForm", "istruttoreId", "non-valido"))
                .andExpect(content().string(containsString("è iscritto a questo corso come allievo")));

        ricarica();
        assertThat(corsi.findWithIstruttoreById(yoga.getId()).orElseThrow().getIstruttore()).isNull();
    }

    @Test
    @DisplayName("Un istruttore non può essere iscritto al corso che tiene")
    void unIstruttoreNonPuoIscriversiAlProprioCorso() throws Exception {
        // PREPARO: Gino tiene Yoga.
        Utente gino = creaIstruttore("Gino", "Verdi", "gino@example.com");
        Corso yoga = corsoTenutoDa("Yoga", gino);

        // ESEGUO: modifico Gino spuntando Yoga tra i corsi che frequenta (richiesta costruita a mano).
        mockMvc.perform(post("/utenti/" + gino.getId()).with(comeAdmin()).with(csrf())
                        .param("nome", "Gino").param("cognome", "Verdi").param("email", "gino@example.com")
                        .param("ruoliIds", ruolo("ISTRUTTORE").getId().toString())
                        .param("corsiIds", yoga.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("utenti/form"))
                .andExpect(model().attributeHasFieldErrorCode("utenteForm", "corsiIds", "istruttore-del-corso"))
                .andExpect(content().string(containsString("Non puoi iscrivere un istruttore ai corsi che tiene lui stesso: Yoga")));

        // VERIFICO: l'iscrizione non è stata salvata.
        ricarica();
        assertThat(utenti.findWithRelazioniById(gino.getId()).orElseThrow().getCorsi()).isEmpty();
    }

    @Test
    @DisplayName("Un istruttore può invece iscriversi ai corsi che NON tiene")
    void unIstruttorePuoIscriversiAdAltriCorsi() throws Exception {
        Utente gino = creaIstruttore("Gino", "Verdi", "gino@example.com");
        corsoTenutoDa("Yoga", gino);
        Corso pilates = creaCorso("Pilates");           // un altro corso, tenuto da nessuno

        mockMvc.perform(post("/utenti/" + gino.getId()).with(comeAdmin()).with(csrf())
                        .param("nome", "Gino").param("cognome", "Verdi").param("email", "gino@example.com")
                        .param("ruoliIds", ruolo("ISTRUTTORE").getId().toString())
                        .param("corsiIds", pilates.getId().toString()))
                .andExpect(status().is3xxRedirection());

        ricarica();
        Utente dopo = utenti.findWithRelazioniById(gino.getId()).orElseThrow();
        assertThat(dopo.getCorsi()).extracting(corso -> corso.getNome()).containsExactly("Pilates");
        assertThat(dopo.getCorsiInsegnati()).extracting(corso -> corso.getNome()).containsExactly("Yoga");
    }

    @Test
    @DisplayName("Nel form utente la casella del corso tenuto è disattivata e spiegata")
    void ilFormDisattivaLaCasellaDelCorsoTenuto() throws Exception {
        Utente gino = creaIstruttore("Gino", "Verdi", "gino@example.com");
        Corso yoga = corsoTenutoDa("Yoga", gino);
        creaCorso("Pilates");

        mockMvc.perform(get("/utenti/" + gino.getId() + "/modifica").with(comeAdmin()))
                .andExpect(status().isOk())
                // Il controller passa al template gli id dei corsi che l'utente tiene...
                .andExpect(model().attribute("corsiInsegnatiIds", Set.of(yoga.getId())))
                // ...e l'HTML contiene la casella disattivata e il testo che spiega il perché.
                .andExpect(content().string(containsString("disabled")))
                .andExpect(content().string(containsString("(lo tiene lui come istruttore)")));
    }

    // ------------------------ FINCHÉ TIENE DEI CORSI: NIENTE TOGLIERE IL RUOLO, NIENTE ELIMINARE

    @Test
    @DisplayName("A un istruttore che tiene corsi non si può togliere il ruolo ISTRUTTORE")
    void nonSiTogliIlRuoloSeTieneCorsi() throws Exception {
        Utente gino = creaIstruttore("Gino", "Verdi", "gino@example.com");
        corsoTenutoDa("Yoga", gino);

        // ESEGUO: gli lascio solo il ruolo ISCRITTO (cioè gli tolgo ISTRUTTORE).
        mockMvc.perform(post("/utenti/" + gino.getId()).with(comeAdmin()).with(csrf())
                        .param("nome", "Gino").param("cognome", "Verdi").param("email", "gino@example.com")
                        .param("ruoliIds", ruolo("ISCRITTO").getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("utenti/form"))
                .andExpect(model().errorCount(1))                      // un errore "globale" del form
                .andExpect(content().string(containsString("Non puoi togliere il ruolo ISTRUTTORE: tiene ancora 1 corsi")));

        ricarica();
        assertThat(utenti.findWithRelazioniById(gino.getId()).orElseThrow().isIstruttore()).isTrue();
    }

    @Test
    @DisplayName("Se non tiene corsi, il ruolo ISTRUTTORE si può togliere")
    void siTogliIlRuoloSeNonTieneCorsi() throws Exception {
        Utente gino = creaIstruttore("Gino", "Verdi", "gino@example.com");

        mockMvc.perform(post("/utenti/" + gino.getId()).with(comeAdmin()).with(csrf())
                        .param("nome", "Gino").param("cognome", "Verdi").param("email", "gino@example.com")
                        .param("ruoliIds", ruolo("ISCRITTO").getId().toString()))
                .andExpect(status().is3xxRedirection());

        ricarica();
        assertThat(utenti.findWithRelazioniById(gino.getId()).orElseThrow().isIstruttore()).isFalse();
    }

    @Test
    @DisplayName("Un istruttore che tiene corsi non si può eliminare")
    void nonSiEliminaUnIstruttoreCheTieneCorsi() throws Exception {
        Utente gino = creaIstruttore("Gino", "Verdi", "gino@example.com");
        corsoTenutoDa("Yoga", gino);

        mockMvc.perform(post("/utenti/" + gino.getId() + "/elimina").with(comeAdmin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errore", containsString("è istruttore di 1 corsi")));

        ricarica();
        assertThat(utenti.findById(gino.getId())).isPresent();   // è ancora nel database
    }

    @Test
    @DisplayName("Un istruttore senza corsi si può eliminare")
    void siEliminaUnIstruttoreSenzaCorsi() throws Exception {
        Utente gino = creaIstruttore("Gino", "Verdi", "gino@example.com");

        mockMvc.perform(post("/utenti/" + gino.getId() + "/elimina").with(comeAdmin()).with(csrf()))
                .andExpect(flash().attribute("messaggio", "Utente eliminato"));

        ricarica();
        assertThat(utenti.findById(gino.getId())).isEmpty();
    }

    // ------------------------------------------------------------------- COSA SI VEDE NELLE LISTE

    @Test
    @DisplayName("Le liste mostrano chi insegna cosa")
    void leListeMostranoGliIstruttori() throws Exception {
        Utente gino = creaIstruttore("Gino", "Verdi", "gino@example.com");
        corsoTenutoDa("Yoga", gino);

        // Lista corsi: accanto a Yoga c'è l'istruttore ("cognome nome").
        mockMvc.perform(get("/corsi").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Verdi Gino")));

        // Lista utenti: Gino ha l'etichetta del corso che insegna (classe tag-insegna).
        mockMvc.perform(get("/utenti").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("tag tag-insegna")));
    }
}
