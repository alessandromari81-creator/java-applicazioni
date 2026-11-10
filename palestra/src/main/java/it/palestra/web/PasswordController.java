/*
 * FILE: PasswordController.java
 * A COSA SERVE: le pagine PUBBLICHE per recuperare l'accesso (le raggiunge anche chi non ha fatto login):
 *   GET  /password-dimenticata        modulo con la sola email
 *   POST /password-dimenticata        se l'email esiste invia il link; la risposta è SEMPRE la stessa («se l'indirizzo esiste riceverai un messaggio»)
 *   GET  /password/imposta?token=...  mostra il modulo per scegliere la password (NON consuma il link)
 *   POST /password/imposta            sceglie la password: consuma il link, imposta la password e rimanda al login
 * Sicurezza: la risposta non rivela se un'email è registrata; ogni richiesta conta per il limite alle email (LimiteRichiesteEmail: se scatta non si invia
 * nulla ma la risposta resta uguale); il link è monouso e a tempo; aprire il link non lo brucia (i programmi di posta aprono le anteprime).
 * Il link vale anche per gli inviti dell'amministratore e per chi ha un profilo senza password. Le pagine sono accessibili senza login (SecurityConfig).
 */
package it.palestra.web;

import it.palestra.exception.RegolaViolataException;
import it.palestra.exception.TokenNonValidoException;
import it.palestra.exception.TroppiTentativiException;
import it.palestra.service.LimiteRichiesteEmail;
import it.palestra.service.RecuperoPasswordService;
import it.palestra.service.TokenPasswordService;
import it.palestra.service.UtenteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordController {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordController.class);

    private final RecuperoPasswordService recupero;
    private final TokenPasswordService tokens;
    private final UtenteService utenti;
    private final LimiteRichiesteEmail limite;

    public PasswordController(RecuperoPasswordService recupero, TokenPasswordService tokens, UtenteService utenti,
                              LimiteRichiesteEmail limite) {
        this.recupero = recupero;
        this.tokens = tokens;
        this.utenti = utenti;
        this.limite = limite;
    }


    // Toglie gli spazi all'inizio e alla fine dell'email PRIMA della validazione (altrimenti " mario@example.com " verrebbe scartata come non valida).
    // Si applica solo all'email: le password non vanno mai "ripulite", gli spazi possono farne parte.
    @InitBinder("richiestaEmailForm")
    void rimuoviSpaziDallEmail(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, "email", new StringTrimmerEditor(false));
    }

    @GetMapping("/password-dimenticata")
    public String formDimenticata(Model model) {
        model.addAttribute("richiestaEmailForm", new RichiestaEmailForm());
        return "password/dimenticata";
    }

    // Se il formato dell'email è sbagliato si rimostra il modulo con l'errore. Altrimenti si prova a inviare il link e si risponde SEMPRE allo stesso modo
    // (redirect a /password-dimenticata?inviata), che l'indirizzo esista o no e che il limite sia scattato o no.
    @PostMapping("/password-dimenticata")
    public String inviaDimenticata(@Valid @ModelAttribute RichiestaEmailForm richiestaEmailForm, BindingResult errori,
                                   HttpServletRequest richiesta) {
        if (errori.hasErrors()) {
            return "password/dimenticata";
        }
        String ip = richiesta.getRemoteAddr();
        String email = richiestaEmailForm.getEmail().trim();
        try {
            limite.verifica(ip, email);
            limite.registraFallimento(ip, email);          // ogni richiesta conta, riuscita o no
            recupero.richiediReset(email);
        } catch (TroppiTentativiException e) {
            LOG.warn("Richiesta di reimpostazione password limitata per troppe richieste da {}", ip);
        }
        return "redirect:/password-dimenticata?inviata";
    }

    // Mostra il modulo per scegliere la password, se il link è valido; altrimenti la pagina «link non valido o scaduto». Non consuma il link.
    @GetMapping("/password/imposta")
    public String formImposta(@RequestParam(name = "token", required = false) String token, Model model) {
        if (tokens.trovaValido(token).isEmpty()) {
            return paginaNonValida(model);
        }
        ImpostaPasswordForm form = new ImpostaPasswordForm();
        form.setToken(token);
        return paginaModulo(model, form);
    }

    // Controlli, nell'ordine: il link è ancora valido; i campi sono compilati; la password ha la lunghezza giusta; le due password coincidono.
    // Solo allora si consuma il link e si imposta la password. Dopo il successo si va al login con un messaggio.
    @PostMapping("/password/imposta")
    public String imposta(@Valid @ModelAttribute("impostaPasswordForm") ImpostaPasswordForm form, BindingResult errori, Model model) {
        if (tokens.trovaValido(form.getToken()).isEmpty()) {
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
            recupero.impostaPassword(form.getToken(), form.getNuovaPassword());
        } catch (TokenNonValidoException e) {
            return paginaNonValida(model);                // qualcuno ha usato il link un attimo prima
        } catch (RegolaViolataException e) {
            errori.rejectValue("nuovaPassword", "non-valida", e.getMessage());
            return paginaModulo(model, form);
        }
        return "redirect:/login?passwordImpostata";
    }

    // La pagina con il modulo: le password non vengono mai rimesse nei campi (le riscrive la persona).
    private String paginaModulo(Model model, ImpostaPasswordForm form) {
        form.setNuovaPassword(null);
        form.setConfermaPassword(null);
        model.addAttribute("impostaPasswordForm", form);
        model.addAttribute("azione", "/password/imposta");
        model.addAttribute("titolo", "Scegli la password");
        model.addAttribute("tokenValido", true);
        return "password/imposta";
    }

    private String paginaNonValida(Model model) {
        model.addAttribute("impostaPasswordForm", new ImpostaPasswordForm());
        model.addAttribute("azione", "/password/imposta");
        model.addAttribute("titolo", "Link non valido");
        model.addAttribute("tokenValido", false);
        model.addAttribute("nuovoLink", "/password-dimenticata");
        model.addAttribute("testoNuovoLink", "Richiedi un nuovo link");
        return "password/imposta";
    }
}
