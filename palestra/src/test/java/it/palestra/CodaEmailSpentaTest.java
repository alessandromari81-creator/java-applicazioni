/*
 * FILE: CodaEmailSpentaTest.java
 * A COSA SERVE: verifica che con la coda SPENTA (app.email.coda.attiva=false, come nei test normali) l'email parta subito, come prima della coda: nessuna riga in coda,
 * nessun lavoratore, e l'unica InvioEmail è quella che spedisce. È l'interruttore: chi vuole il comportamento di prima imposta app.email.coda.attiva=false.
 */
package it.palestra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.palestra.repository.EmailInCodaRepository;
import it.palestra.service.InvioEmail;
import it.palestra.service.InvioEmailInCoda;
import it.palestra.service.SpedizioneEmailProgrammata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

class CodaEmailSpentaTest extends BaseWebTest {

    @Autowired ApplicationContext contesto;
    @Autowired EmailInCodaRepository repo;

    @Test
    @DisplayName("Coda spenta: non esistono né il lavoratore né l'invio in coda, e l'unica InvioEmail è quella che spedisce")
    void nessunPezzoDellaCoda() {
        assertThat(contesto.getBeansOfType(SpedizioneEmailProgrammata.class)).isEmpty();
        assertThat(contesto.getBeansOfType(InvioEmailInCoda.class)).isEmpty();
        assertThat(contesto.getBeansOfType(InvioEmail.class)).hasSize(1).containsValue(emailFinte);
    }

    @Test
    @DisplayName("Coda spenta: la registrazione spedisce subito l'email e non scrive niente in coda")
    void lEmailParteSubito() throws Exception {
        ruolo("ISCRITTO");

        mockMvc.perform(post("/registrazione").with(csrf())
                        .param("nome", "Mario").param("cognome", "Rossi").param("email", "mario@example.com")
                        .param("indirizzo", "Via Roma 1").param("cap", "00100").param("citta", "Roma").param("accettoPrivacy", "true"))
                .andExpect(status().is3xxRedirection());

        assertThat(emailFinte.per("mario@example.com")).hasSize(1);
        assertThat(repo.count()).isZero();
    }
}
