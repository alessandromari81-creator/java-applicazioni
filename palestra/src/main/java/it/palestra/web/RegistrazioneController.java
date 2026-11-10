/*
 * FILE: RegistrazioneController.java
 * A COSA SERVE: le pagine PUBBLICHE della registrazione libera:
 *   GET  /registrazione                     modulo (nome, cognome, email, indirizzo, CAP, città, accettazione privacy, consenso marketing facoltativo)
 *   POST /registrazione                     salva i dati IN ATTESA e invia l'email di conferma; la risposta è SEMPRE la stessa
 *   GET  /registrazione/conferma?token=...  dal link dell'email: mostra il modulo per scegliere la password (non consuma il link)
 *   POST /registrazione/conferma            sceglie la password: crea l'account (o attiva la scheda esistente) e rimanda al login
 * Difese: l'accettazione della privacy è obbligatoria; campo trappola per i programmi automatici (se compilato si risponde come se nulla fosse e non si fa
 * niente); limite alle richieste di email (LimiteRichiesteEmail: se scatta non parte nulla ma la risposta è uguale); nessuna informazione sull'esistenza
 * dell'email; nessun account prima della conferma. Vedi RegistrazioneService per le regole.
 */
package it.palestra.web;

import it.palestra.exception.RegolaViolataException;
import it.palestra.exception.TokenNonValidoException;
import it.palestra.exception.TroppiTentativiException;
import it.palestra.service.LimiteRichiesteEmail;
import it.palestra.service.RegistrazioneService;
import it.palestra.service.UtenteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/registrazione")
public class RegistrazioneController {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrazioneController.class);

    private final RegistrazioneService registrazioni;
    private final UtenteService utenti;
    private final LimiteRichiesteEmail limite;

    public RegistrazioneController(RegistrazioneService registrazioni, UtenteService utenti, LimiteRichiesteEmail limite) {
        this.registrazioni = registrazioni;
        this.utenti = utenti;
        this.limite = limite;
    }


    // Toglie gli spazi all'inizio e alla fine dell'email PRIMA della validazione (altrimenti " mario@example.com " verrebbe scartata come non valida).
    // Si applica solo all'email: le password non vanno mai "ripulite", gli spazi possono farne parte.
    @InitBinder("registrazioneForm")
    void rimuoviSpaziDallEmail(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, "email", new StringTrimmerEditor(false));
    }

    @GetMapping
    public String modulo(Model model) {
        model.addAttribute("registrazioneForm", new RegistrazioneForm());
        return "registrazione/modulo";
    }

    // Se i dati non sono validi si rimostra il modulo con gli errori. Se sono validi la risposta è SEMPRE «controlla la tua email» (redirect a
    // /registrazione?inviata), che l'indirizzo sia nuovo, già in attesa, già registrato, o che il limite sia scattato.
    @PostMapping
    public String invia(@Valid @ModelAttribute RegistrazioneForm registrazioneForm, BindingResult errori, HttpServletRequest richiesta) {
        if (registrazioneForm.getSito() != null && !registrazioneForm.getSito().isBlank()) {
            return "redirect:/registrazione?inviata";                 // campo trappola compilato: un programma automatico, non si fa nulla
        }
        if (errori.hasErrors()) {
            return "registrazione/modulo";
        }
        String ip = richiesta.getRemoteAddr();
        String email = registrazioneForm.getEmail().trim();
        try {
            limite.verifica(ip, email);
            limite.registraFallimento(ip, email);                      // ogni richiesta conta
            registrazioni.richiedi(registrazioneForm);
        } catch (TroppiTentativiException e) {
            LOG.warn("Registrazione limitata per troppe richieste da {}", ip);
        }
        return "redirect:/registrazione?inviata";
    }

    // Dal link dell'email: se è valido mostra il modulo per scegliere la password, altrimenti «link non valido». Non consuma il link.
    @GetMapping("/conferma")
    public String formConferma(@RequestParam(name = "token", required = false) String token, Model model) {
        if (registrazioni.trovaValida(token).isEmpty()) {
            return paginaNonValida(model);
        }
        ImpostaPasswordForm form = new ImpostaPasswordForm();
        form.setToken(token);
        return paginaModulo(model, form);
    }

    // Stessi controlli della pagina «scegli la password»: link valido, campi compilati, lunghezza, password uguali. Poi si crea l'account.
    @PostMapping("/conferma")
    public String conferma(@Valid @ModelAttribute("impostaPasswordForm") ImpostaPasswordForm form, BindingResult errori, Model model) {
        if (registrazioni.trovaValida(form.getToken()).isEmpty()) {
            return paginaNonValida(model);
        }
        if (!errori.hasFieldErrors("nuovaPassword")) {
            utenti.erroreLunghezzaPassword(form.getNuovaPassword())
                    .ifPresent(m -> errori.rejectValue("nuovaPassword", "non-valida", m));
        }
        if (!errori.hasFieldErrors("confermaPassword") && !errori.hasFieldErrors("nuovaPassword")
                && !form.getNuovaPassword().equals(form.getConfermaPassword())) {
            errori.rejectValue("confermaPassword", "diversa", "Le due password non coincidono");
        }
        if (errori.hasErrors()) {
            return paginaModulo(model, form);
        }
        try {
            RegistrazioneService.Esito esito = registrazioni.conferma(form.getToken(), form.getNuovaPassword());
            return switch (esito) {
                case CREATO -> "redirect:/login?registrato";
                case ATTIVATO -> "redirect:/login?passwordImpostata";
                case GIA_ESISTENTE -> "redirect:/login?giaRegistrato";
            };
        } catch (TokenNonValidoException e) {
            return paginaNonValida(model);
        } catch (DataIntegrityViolationException e) {
            return "redirect:/login?giaRegistrato";                   // due conferme insieme per la stessa email: una ha già creato l'account
        } catch (RegolaViolataException e) {
            errori.rejectValue("nuovaPassword", "non-valida", e.getMessage());
            return paginaModulo(model, form);
        }
    }

    private String paginaModulo(Model model, ImpostaPasswordForm form) {
        form.setNuovaPassword(null);
        form.setConfermaPassword(null);
        model.addAttribute("impostaPasswordForm", form);
        model.addAttribute("azione", "/registrazione/conferma");
        model.addAttribute("titolo", "Scegli la password per completare la registrazione");
        model.addAttribute("tokenValido", true);
        return "password/imposta";
    }

    private String paginaNonValida(Model model) {
        model.addAttribute("impostaPasswordForm", new ImpostaPasswordForm());
        model.addAttribute("azione", "/registrazione/conferma");
        model.addAttribute("titolo", "Link non valido");
        model.addAttribute("tokenValido", false);
        model.addAttribute("nuovoLink", "/registrazione");
        model.addAttribute("testoNuovoLink", "Ripeti la registrazione");
        return "password/imposta";
    }
}
