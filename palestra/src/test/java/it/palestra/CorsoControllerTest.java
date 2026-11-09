/*
 * FILE: CorsoControllerTest.java
 * A COSA SERVE: verifica le pagine /corsi: lista, creazione, validazione, modifica, eliminazione e la regola
 * "un corso con iscritti non si può eliminare". Stesso schema: PREPARO / ESEGUO / VERIFICO.
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import it.palestra.model.Corso;
import it.palestra.model.Livello;
import it.palestra.model.Utente;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CorsoControllerTest extends BaseWebTest {

    @Test
    @DisplayName("La lista mostra i corsi in ordine alfabetico")
    void laListaMostraICorsiInOrdine() throws Exception {
        // PREPARO: li creo in ordine "sbagliato" (Yoga prima di Aikido) per provare che la lista li ordina.
        creaCorso("Yoga");
        creaCorso("Aikido");

        // ESEGUO: andReturn() restituisce la risposta, così possiamo leggerne l'HTML e fare confronti nostri.
        String html = mockMvc.perform(get("/corsi").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(view().name("corsi/lista"))
                .andReturn().getResponse().getContentAsString();

        // VERIFICO: nell'HTML "Aikido" compare PRIMA di "Yoga" (indexOf = posizione del testo).
        assertThat(html.indexOf("Aikido")).isPositive().isLessThan(html.indexOf("Yoga"));
    }

    @Test
    @DisplayName("Creare un corso valido: redirect, messaggio e salvataggio")
    void creareUnCorsoValido() throws Exception {
        mockMvc.perform(post("/corsi").with(comeAdmin()).with(csrf())
                        .param("nome", "Zumba")
                        .param("descrizione", "Danza")
                        .param("durataMinuti", "55")
                        .param("livello", "INTERMEDIO"))     // "INTERMEDIO" viene convertito nell'enum Livello
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/corsi"))
                .andExpect(flash().attribute("messaggio", "Corso creato"));

        ricarica();
        Corso salvato = corsi.findAllByOrderByNomeAsc().stream()
                .filter(c -> c.getNome().equals("Zumba")).findFirst().orElseThrow();
        assertThat(salvato.getDurataMinuti()).isEqualTo(55);
        assertThat(salvato.getLivello()).isEqualTo(Livello.INTERMEDIO);
    }

    @Test
    @DisplayName("Nome vuoto, durata 0 e livello mancante: errori di validazione")
    void validazioneDelCorso() throws Exception {
        mockMvc.perform(post("/corsi").with(comeAdmin()).with(csrf())
                        .param("nome", "")
                        .param("durataMinuti", "0"))         // niente "livello" nella richiesta
                .andExpect(status().isOk())
                .andExpect(view().name("corsi/form"))
                .andExpect(model().attributeHasFieldErrors("corsoForm", "nome", "durataMinuti", "livello"))
                .andExpect(content().string(containsString("La durata deve essere almeno 1 minuto")));
    }

    @Test
    @DisplayName("Una descrizione più lunga di 255 caratteri è rifiutata (altrimenti il database darebbe errore 500)")
    void descrizioneTroppoLunga() throws Exception {
        mockMvc.perform(post("/corsi").with(comeAdmin()).with(csrf())
                        .param("nome", "Zumba")
                        .param("descrizione", "x".repeat(300))   // "x".repeat(300) = una stringa di 300 caratteri
                        .param("durataMinuti", "30")
                        .param("livello", "AVANZATO"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("corsoForm", "descrizione"));
    }

    @Test
    @DisplayName("Modificare un corso aggiorna i dati")
    void modificareUnCorso() throws Exception {
        Corso yoga = creaCorso("Yoga");

        mockMvc.perform(post("/corsi/" + yoga.getId()).with(comeAdmin()).with(csrf())
                        .param("nome", "Yoga Avanzato")
                        .param("durataMinuti", "90")
                        .param("livello", "AVANZATO"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("messaggio", "Corso aggiornato"));

        ricarica();
        Corso dopo = corsi.findById(yoga.getId()).orElseThrow();
        assertThat(dopo.getNome()).isEqualTo("Yoga Avanzato");
        assertThat(dopo.getDurataMinuti()).isEqualTo(90);
    }

    @Test
    @DisplayName("Un corso inesistente dà la pagina 404")
    void corsoInesistenteDa404() throws Exception {
        mockMvc.perform(get("/corsi/999999/modifica").with(comeAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(model().attribute("dettaglio", "Corso 999999 non trovato"));
    }

    @Test
    @DisplayName("Un corso senza iscritti si può eliminare")
    void eliminareUnCorsoSenzaIscritti() throws Exception {
        Corso yoga = creaCorso("Yoga");

        mockMvc.perform(post("/corsi/" + yoga.getId() + "/elimina").with(comeAdmin()).with(csrf()))
                .andExpect(flash().attribute("messaggio", "Corso eliminato"));

        ricarica();
        assertThat(corsi.existsById(yoga.getId())).isFalse();
    }

    @Test
    @DisplayName("Un corso con utenti iscritti NON si può eliminare")
    void nonSiEliminaUnCorsoConIscritti() throws Exception {
        // PREPARO: un corso e un utente iscritto a quel corso.
        Corso yoga = creaCorso("Yoga");
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        // new HashSet<>(...) e non Set.of(...): Set.of crea un insieme IMMUTABILE e Hibernate, che deve poter
        // modificare la collezione di un'entità già salvata, lancerebbe UnsupportedOperationException.
        mario.setCorsi(new HashSet<>(Set.of(yoga)));
        utenti.save(mario);

        mockMvc.perform(post("/corsi/" + yoga.getId() + "/elimina").with(comeAdmin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                // Messaggio d'errore (rosso) che dice QUANTI iscritti ci sono.
                .andExpect(flash().attribute("errore", containsString("ci sono 1 utenti iscritti")));

        ricarica();
        assertThat(corsi.existsById(yoga.getId())).isTrue();   // il corso è ancora nel database
    }
}
