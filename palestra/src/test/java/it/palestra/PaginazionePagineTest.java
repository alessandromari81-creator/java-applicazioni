/*
 * FILE: PaginazionePagineTest.java
 * A COSA SERVE: verifica la paginazione delle pagine HTML (utenti, corsi, abbonamenti, storico di un utente):
 * contenuto della pagina, link di navigazione, ricerca, parametri non validi (400), pagina oltre l'ultima (si va all'ultima)
 * e soprattutto che dopo crea/elimina si torna alla STESSA pagina/ordinamento/ricerca (lo stato della lista non si perde).
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.palestra.model.TipoAbbonamento;
import it.palestra.model.Utente;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaginazionePagineTest extends BaseWebTest {

    private void creaIscritti(int quanti) {
        for (int i = 0; i < quanti; i++) {
            String nn = String.format("%02d", i);
            creaUtente("Nome" + nn, "Cognome" + nn, "c" + nn + "@example.com", ruolo("ISCRITTO"));
        }
    }

    @Test
    @DisplayName("Lista utenti: mostra solo la pagina chiesta, con riepilogo e link alle altre pagine")
    void listaUtentiPaginata() throws Exception {
        creaIscritti(25);                                    // + admin = 26 utenti

        mockMvc.perform(get("/utenti").param("size", "10").param("page", "1").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Risultati 11–20 di 26")))
                .andExpect(content().string(containsString("Cognome10")))        // prima riga della pagina 2
                .andExpect(content().string(not(containsString("Cognome09"))))   // è nella pagina precedente
                .andExpect(content().string(not(containsString("Cognome20"))))   // è nella pagina successiva
                .andExpect(content().string(containsString("Successiva")))
                .andExpect(content().string(containsString("Precedente")));
    }

    @Test
    @DisplayName("Ricerca: la lista mostra solo i risultati, il riepilogo conta il filtro e c'è il link 'Azzera'")
    void ricercaNellaLista() throws Exception {
        creaIscritti(15);

        mockMvc.perform(get("/utenti").param("q", "Cognome07").with(comeAdmin()))
                .andExpect(content().string(containsString("Risultati 1–1 di 1")))
                .andExpect(content().string(containsString("c07@example.com")))
                .andExpect(content().string(not(containsString("c08@example.com"))))
                .andExpect(content().string(containsString("Azzera")));

        mockMvc.perform(get("/utenti").param("q", "inesistente").with(comeAdmin()))
                .andExpect(content().string(containsString("Nessun risultato")));
    }

    @Test
    @DisplayName("Ordinamento dalle intestazioni: ?sort=email,desc inverte l'ordine")
    void ordinamentoDaIntestazione() throws Exception {
        creaIscritti(3);

        String html = mockMvc.perform(get("/utenti").param("sort", "email,desc").with(comeAdmin()))
                .andReturn().getResponse().getContentAsString();
        assertThat(html.indexOf("c02@example.com")).isLessThan(html.indexOf("c00@example.com"));
    }

    @Test
    @DisplayName("Parametri non validi nelle pagine: 400 (size troppo grande, campo di ordinamento non ammesso)")
    void parametriNonValidi() throws Exception {
        mockMvc.perform(get("/utenti").param("size", "500").with(comeAdmin())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/utenti").param("sort", "passwordHash").with(comeAdmin())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/corsi").param("page", "-3").with(comeAdmin())).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Pagina oltre l'ultima: redirect all'ultima pagina (stessi ordinamento e ricerca)")
    void oltreLUltimaVaAllUltima() throws Exception {
        creaIscritti(25);                                    // 26 utenti, size 10 -> pagine 0,1,2

        mockMvc.perform(get("/utenti").param("size", "10").param("page", "9").with(comeAdmin()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/utenti?page=2&size=10"));
    }

    @Test
    @DisplayName("Eliminare da una pagina riporta alla stessa pagina, con stessa dimensione, ordinamento e ricerca")
    void eliminaMantieneLoStato() throws Exception {
        Utente da = creaUtente("Da", "Eliminare", "elimina@example.com", ruolo("ISCRITTO"));

        mockMvc.perform(post("/utenti/" + da.getId() + "/elimina").with(comeAdmin()).with(csrf())
                        .param("page", "2").param("size", "10").param("sort", "email,desc").param("q", "elim"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/utenti?page=2&size=10&sort=email,desc&q=elim"))
                .andExpect(flash().attribute("messaggio", "Utente eliminato"));
    }

    @Test
    @DisplayName("Eliminare l'ultimo elemento dell'ultima pagina: si arriva alla pagina precedente e il messaggio non si perde")
    void eliminaUltimoDellUltimaPagina() throws Exception {
        // 11 utenti (admin + 10) con size 10: la pagina 1 contiene solo l'ultimo; dopo averlo eliminato la pagina 1 non esiste.
        creaIscritti(10);
        Utente ultimo = utenti.findAll().stream().filter(u -> u.getEmail().equals("c09@example.com")).findFirst().orElseThrow();

        var esito = mockMvc.perform(post("/utenti/" + ultimo.getId() + "/elimina").with(comeAdmin()).with(csrf())
                        .param("page", "1").param("size", "10").param("sort", "email,asc"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String url = esito.getResponse().getRedirectedUrl();
        var flashMap = esito.getFlashMap();

        // Seguo il redirect portandomi dietro il messaggio flash, come fa il browser/sessione.
        mockMvc.perform(get(url).flashAttrs(flashMap).with(comeAdmin()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/utenti?size=10&sort=email,asc"))          // pagina 0 = predefinita: non scritta
                .andExpect(flash().attribute("messaggio", "Utente eliminato"));       // il messaggio ha attraversato il 2° redirect
    }

    @Test
    @DisplayName("Creare un utente riporta alla lista con lo stato di partenza; il form ricorda lo stato in campi nascosti")
    void formRicordaLoStato() throws Exception {
        mockMvc.perform(get("/utenti/nuovo").param("page", "3").param("size", "50").param("q", "ross").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"page\" value=\"3\"")))
                .andExpect(content().string(containsString("name=\"size\" value=\"50\"")))
                .andExpect(content().string(containsString("name=\"q\" value=\"ross\"")));

        mockMvc.perform(post("/utenti").with(comeAdmin()).with(csrf())
                        .param("nome", "Nuovo").param("cognome", "Utente").param("email", "nuovo@example.com")
                        .param("page", "3").param("size", "50").param("q", "ross"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/utenti?page=3&size=50&q=ross"));
    }

    @Test
    @DisplayName("Corsi: lista paginata con ricerca per nome")
    void corsiPaginati() throws Exception {
        for (int i = 0; i < 12; i++) creaCorso(String.format("Corso%02d", i));
        creaCorso("Yoga");

        mockMvc.perform(get("/corsi").param("size", "10").with(comeAdmin()))
                .andExpect(content().string(containsString("Risultati 1–10 di 13")));
        mockMvc.perform(get("/corsi").param("q", "yog").with(comeAdmin()))
                .andExpect(content().string(containsString("Risultati 1–1 di 1")))
                .andExpect(content().string(containsString("Yoga")));
    }

    @Test
    @DisplayName("Abbonamenti: lista paginata, ricerca sull'iscritto; lo storico di un utente è paginato senza ricerca")
    void abbonamentiPaginati() throws Exception {
        LocalDate oggi = LocalDate.now();
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        Utente laura = creaUtente("Laura", "Bianchi", "laura@example.com", ruolo("ISCRITTO"));
        for (int i = 0; i < 6; i++) creaAbbonamento(mario, TipoAbbonamento.MENSILE, oggi.plusMonths(i));
        creaAbbonamento(laura, TipoAbbonamento.ANNUALE, oggi);

        mockMvc.perform(get("/abbonamenti").param("size", "5").with(comeAdmin()))
                .andExpect(content().string(containsString("Risultati 1–5 di 7")));
        mockMvc.perform(get("/abbonamenti").param("q", "bianchi").with(comeAdmin()))
                .andExpect(content().string(containsString("Risultati 1–1 di 1")));

        mockMvc.perform(get("/abbonamenti/utente/" + mario.getId()).param("size", "4").with(comeAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Risultati 1–4 di 6")));
    }

    @Test
    @DisplayName("Eliminare un abbonamento dallo storico riporta allo storico dello stesso utente con lo stesso stato")
    void eliminaAbbonamentoDalloStorico() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        var futuro = creaAbbonamento(mario, TipoAbbonamento.MENSILE, LocalDate.now().plusMonths(3));

        mockMvc.perform(post("/abbonamenti/" + futuro.getId() + "/elimina").with(comeAdmin()).with(csrf())
                        .param("daUtente", "true").param("page", "1").param("size", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/abbonamenti/utente/" + mario.getId() + "?page=1&size=5"));
    }
}
