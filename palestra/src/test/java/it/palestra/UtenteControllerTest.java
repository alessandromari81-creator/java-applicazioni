/*
 * FILE: UtenteControllerTest.java
 * A COSA SERVE: verifica le pagine /utenti: lista, creazione, modifica, eliminazione, validazione, password,
 * ruoli e la regola "non toccare l'ultimo amministratore".
 * Convenzione dei commenti: PREPARO (dati di partenza) / ESEGUO (richiesta) / VERIFICO (risposta e database).
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
import it.palestra.model.Ruolo;
import it.palestra.model.Utente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UtenteControllerTest extends BaseWebTest {

    // ---------------------------------------------------------------- LISTA

    @Test
    @DisplayName("La lista mostra gli utenti con i loro ruoli")
    void laListaMostraUtentiERuoli() throws Exception {
        creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));

        mockMvc.perform(get("/utenti").with(comeAdmin()))
                .andExpect(status().isOk())
                // content().string(...) controlla l'HTML prodotto da Thymeleaf: containsString = "contiene questo testo".
                .andExpect(content().string(containsString("Rossi Mario")))
                .andExpect(content().string(containsString("ISCRITTO")));
    }

    // ------------------------------------------------------------- CREAZIONE

    @Test
    @DisplayName("Creare un utente valido: redirect alla lista, messaggio e salvataggio con il ruolo")
    void creareUnUtenteValido() throws Exception {
        Ruolo iscritto = ruolo("ISCRITTO");

        // ESEGUO: POST del form. .with(csrf()) aggiunge il token CSRF, senza il quale Spring risponderebbe 403.
        // .param(nome, valore) imposta un campo del form, come i campi <input name="...">.
        mockMvc.perform(post("/utenti").with(comeAdmin()).with(csrf())
                        .param("nome", "Gino")
                        .param("cognome", "Verdi")
                        .param("email", "gino@example.com")
                        .param("ruoliIds", iscritto.getId().toString()))
                // VERIFICO la risposta: dopo il salvataggio si va alla lista (pattern Post-Redirect-Get)...
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/utenti"))
                // ...e il messaggio "flash" per la pagina successiva è stato impostato.
                .andExpect(flash().attribute("messaggio", "Utente creato"));

        // VERIFICO il database: ricarica() forza una vera lettura, poi controlliamo cosa è stato salvato.
        ricarica();
        Utente salvato = utenti.findByEmailIgnoreCase("gino@example.com").orElseThrow();
        assertThat(salvato.getNome()).isEqualTo("Gino");
        assertThat(salvato.getRuoli()).extracting(Ruolo::getNome).containsExactly("ISCRITTO");
        assertThat(salvato.getPasswordHash()).isNull();   // un iscritto non ha password
    }

    @Test
    @DisplayName("Campi obbligatori vuoti: il form viene riproposto con gli errori")
    void campiObbligatoriVuoti() throws Exception {
        mockMvc.perform(post("/utenti").with(comeAdmin()).with(csrf())
                        .param("nome", "")
                        .param("cognome", "")
                        .param("email", ""))
                // Con errori di validazione NON c'è redirect: si rimostra la pagina del form (status 200)...
                .andExpect(status().isOk())
                .andExpect(view().name("utenti/form"))
                // ...e nel model, per l'oggetto "utenteForm", ci sono errori proprio su questi tre campi.
                .andExpect(model().attributeHasFieldErrors("utenteForm", "nome", "cognome", "email"));
    }

    @Test
    @DisplayName("Un'email già usata (anche con maiuscole diverse) viene rifiutata")
    void emailDuplicata() throws Exception {
        creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));

        mockMvc.perform(post("/utenti").with(comeAdmin()).with(csrf())
                        .param("nome", "Altro")
                        .param("cognome", "Utente")
                        .param("email", "MARIO@example.com"))   // stessa email, scritta in maiuscolo
                .andExpect(status().isOk())
                // "duplicata" è il codice d'errore che abbiamo scelto in UtenteController.controllaEmail().
                .andExpect(model().attributeHasFieldErrorCode("utenteForm", "email", "duplicata"));
    }

    @Test
    @DisplayName("Un ADMIN senza password è rifiutato")
    void adminSenzaPassword() throws Exception {
        Ruolo admin = ruolo("ADMIN");

        mockMvc.perform(post("/utenti").with(comeAdmin()).with(csrf())
                        .param("nome", "Nuovo").param("cognome", "Admin").param("email", "nuovo@example.com")
                        .param("ruoliIds", admin.getId().toString()))     // ruolo ADMIN ma nessuna password
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("utenteForm", "password", "non-valida"));
    }

    @Test
    @DisplayName("Una password troppo corta è rifiutata")
    void passwordTroppoCorta() throws Exception {
        Ruolo admin = ruolo("ADMIN");

        mockMvc.perform(post("/utenti").with(comeAdmin()).with(csrf())
                        .param("nome", "Nuovo").param("cognome", "Admin").param("email", "nuovo@example.com")
                        .param("ruoliIds", admin.getId().toString())
                        .param("password", "corta"))                      // 5 caratteri: ne servono almeno 8
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("utenteForm", "password", "non-valida"));
    }

    @Test
    @DisplayName("Un ADMIN con password valida viene creato e la password è salvata cifrata")
    void adminConPasswordValida() throws Exception {
        Ruolo admin = ruolo("ADMIN");

        mockMvc.perform(post("/utenti").with(comeAdmin()).with(csrf())
                        .param("nome", "Nuovo").param("cognome", "Admin").param("email", "nuovo@example.com")
                        .param("ruoliIds", admin.getId().toString())
                        .param("password", "password-lunga-1"))
                .andExpect(status().is3xxRedirection());

        ricarica();
        Utente salvato = utenti.findByEmailIgnoreCase("nuovo@example.com").orElseThrow();
        // Nel database NON c'è la password scritta, ma il suo hash bcrypt (che inizia con "$2")...
        assertThat(salvato.getPasswordHash()).isNotEqualTo("password-lunga-1").startsWith("$2");
        // ...e encoder.matches() conferma che quell'hash corrisponde proprio alla password inserita.
        assertThat(encoder.matches("password-lunga-1", salvato.getPasswordHash())).isTrue();
    }

    // -------------------------------------------------------------- MODIFICA

    @Test
    @DisplayName("Modificando un utente senza scrivere la password, quella vecchia resta valida")
    void modificaSenzaCambiarePassword() throws Exception {
        // PREPARO: un secondo admin con password nota (così l'admin iniziale resta e non tocchiamo "l'ultimo admin").
        Ruolo admin = ruolo("ADMIN");
        Utente luca = creaUtente("Luca", "Neri", "luca@example.com", admin);
        luca.setPasswordHash(encoder.encode("vecchia-password"));
        utenti.save(luca);

        // ESEGUO: modifica del cognome, con il campo password VUOTO.
        mockMvc.perform(post("/utenti/" + luca.getId()).with(comeAdmin()).with(csrf())
                        .param("nome", "Luca").param("cognome", "Bianchi").param("email", "luca@example.com")
                        .param("ruoliIds", admin.getId().toString())
                        .param("password", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("messaggio", "Utente aggiornato"));

        // VERIFICO: il cognome è cambiato, la password no.
        ricarica();
        Utente dopo = utenti.findById(luca.getId()).orElseThrow();
        assertThat(dopo.getCognome()).isEqualTo("Bianchi");
        assertThat(encoder.matches("vecchia-password", dopo.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Scrivendo una nuova password, la vecchia non vale più")
    void modificaConNuovaPassword() throws Exception {
        Ruolo admin = ruolo("ADMIN");
        Utente luca = creaUtente("Luca", "Neri", "luca@example.com", admin);
        luca.setPasswordHash(encoder.encode("vecchia-password"));
        utenti.save(luca);

        mockMvc.perform(post("/utenti/" + luca.getId()).with(comeAdmin()).with(csrf())
                        .param("nome", "Luca").param("cognome", "Neri").param("email", "luca@example.com")
                        .param("ruoliIds", admin.getId().toString())
                        .param("password", "nuova-password-1"))
                .andExpect(status().is3xxRedirection());

        ricarica();
        String hash = utenti.findById(luca.getId()).orElseThrow().getPasswordHash();
        assertThat(encoder.matches("nuova-password-1", hash)).isTrue();
        assertThat(encoder.matches("vecchia-password", hash)).isFalse();
    }

    @Test
    @DisplayName("Un id inesistente dà la pagina 404 con il messaggio preciso")
    void idInesistenteDa404() throws Exception {
        // Il service lancia RisorsaNonTrovataException e GestoreErrori la trasforma in 404 + template error/404.
        mockMvc.perform(get("/utenti/999999/modifica").with(comeAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(model().attribute("dettaglio", "Utente 999999 non trovato"));
    }

    // ----------------------------------------------------------- ELIMINAZIONE

    @Test
    @DisplayName("Eliminare un utente lo toglie dal database")
    void eliminareUnUtente() throws Exception {
        Utente mario = creaUtente("Mario", "Rossi", "mario@example.com", ruolo("ISCRITTO"));

        mockMvc.perform(post("/utenti/" + mario.getId() + "/elimina").with(comeAdmin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/utenti"))
                .andExpect(flash().attribute("messaggio", "Utente eliminato"));

        ricarica();
        assertThat(utenti.findById(mario.getId())).isEmpty();   // Optional vuoto: non esiste più
    }

    // ------------------------------------------------------- ULTIMO AMMINISTRATORE

    @Test
    @DisplayName("L'ultimo amministratore non si può eliminare")
    void nonSiEliminaLUltimoAdmin() throws Exception {
        // PREPARO: a inizio test esiste UN solo ADMIN (quello creato da AdminIniziale).
        Utente admin = adminIniziale();
        assertThat(utenti.countByRuoliNome("ADMIN")).isEqualTo(1);

        mockMvc.perform(post("/utenti/" + admin.getId() + "/elimina").with(comeAdmin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                // Il service ha rifiutato: al posto del messaggio verde arriva quello rosso ("errore").
                .andExpect(flash().attribute("errore", "Impossibile eliminare l'ultimo amministratore"));

        ricarica();
        assertThat(utenti.findById(admin.getId())).isPresent();   // è ancora lì
    }

    @Test
    @DisplayName("Si può eliminare un amministratore se ne esiste un altro")
    void siEliminaUnAdminSeCENeAltri() throws Exception {
        Utente secondo = creaUtente("Luca", "Neri", "luca@example.com", ruolo("ADMIN"));
        assertThat(utenti.countByRuoliNome("ADMIN")).isEqualTo(2);

        mockMvc.perform(post("/utenti/" + secondo.getId() + "/elimina").with(comeAdmin()).with(csrf()))
                .andExpect(flash().attribute("messaggio", "Utente eliminato"));

        ricarica();
        assertThat(utenti.countByRuoliNome("ADMIN")).isEqualTo(1);
    }

    @Test
    @DisplayName("All'ultimo amministratore non si può togliere il ruolo ADMIN")
    void nonSiTogliAdminAllUltimoAdmin() throws Exception {
        Utente admin = adminIniziale();
        Ruolo iscritto = ruolo("ISCRITTO");

        // ESEGUO: modifica che lascia all'admin SOLO il ruolo ISCRITTO (cioè gli toglie ADMIN).
        mockMvc.perform(post("/utenti/" + admin.getId()).with(comeAdmin()).with(csrf())
                        .param("nome", "Admin").param("cognome", "Palestra").param("email", EMAIL_ADMIN)
                        .param("ruoliIds", iscritto.getId().toString()))
                // Il form viene riproposto (status 200) con UN errore "globale" (non legato a un campo)...
                .andExpect(status().isOk())
                .andExpect(view().name("utenti/form"))
                .andExpect(model().attributeHasErrors("utenteForm"))
                .andExpect(model().errorCount(1))
                .andExpect(content().string(containsString("Non puoi togliere il ruolo ADMIN all&#39;ultimo amministratore")));

        // ...e nel database è ancora ADMIN. (Nell'HTML l'apostrofo diventa &#39;: Thymeleaf protegge i caratteri speciali.)
        ricarica();
        assertThat(utenti.findById(admin.getId()).orElseThrow().isAdmin()).isTrue();
    }

    // ---------------------------------------------------- CORSI ASSOCIATI

    @Test
    @DisplayName("Si possono assegnare più corsi a un utente")
    void assegnareDeiCorsi() throws Exception {
        Corso yoga = creaCorso("Yoga");
        Corso spinning = creaCorso("Spinning");

        mockMvc.perform(post("/utenti").with(comeAdmin()).with(csrf())
                        .param("nome", "Gino").param("cognome", "Verdi").param("email", "gino@example.com")
                        .param("corsiIds", yoga.getId().toString(), spinning.getId().toString()))   // due valori, stesso campo
                .andExpect(status().is3xxRedirection());

        ricarica();
        Utente gino = utenti.findWithRelazioniById(
                utenti.findByEmailIgnoreCase("gino@example.com").orElseThrow().getId()).orElseThrow();
        assertThat(gino.getCorsi()).extracting(Corso::getNome).containsExactlyInAnyOrder("Yoga", "Spinning");
    }
}
