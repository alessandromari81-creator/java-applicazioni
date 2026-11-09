/*
 * FILE: LayoutTabelleTest.java
 * A COSA SERVE: "guardia" contro un difetto grafico già capitato: i pulsanti delle liste uscivano dalla tabella
 * perché la classe CSS "azioni" (che usa display:flex) era messa direttamente sulla cella <td>, e una cella
 * trasformata in flex smette di comportarsi da cella. La regola è: i pulsanti stanno in un <div class="azioni">
 * DENTRO la cella (<td class="col-azioni">).
 * Un test non può "vedere" la pagina, ma può controllare che l'HTML prodotto abbia la struttura giusta: se qualcuno
 * rimettesse class="azioni" su un <td>, questo test fallirebbe.
 */
package it.palestra;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.palestra.model.TipoAbbonamento;
import it.palestra.model.Utente;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LayoutTabelleTest extends BaseWebTest {

    @Test
    @DisplayName("Nelle liste utenti, corsi e abbonamenti i pulsanti stanno in un <div> dentro la cella, mai sulla <td>")
    void iPulsantiStannoInUnDivDentroLaCella() throws Exception {
        // PREPARO: almeno una riga in ciascuna lista, altrimenti la cella delle azioni non verrebbe mostrata.
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));
        creaCorso("Yoga");
        creaAbbonamento(mario, TipoAbbonamento.MENSILE, LocalDate.now().plusDays(5));   // futuro: ha il pulsante Elimina

        for (String pagina : new String[] {"/utenti", "/corsi", "/abbonamenti"}) {
            mockMvc.perform(get(pagina).with(comeAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("<td class=\"azioni\">"))))   // il difetto: no
                    .andExpect(content().string(containsString("<td class=\"col-azioni\">")))    // la cella giusta
                    .andExpect(content().string(containsString("<div class=\"azioni\">")));     // i pulsanti nel div
        }
    }
}
