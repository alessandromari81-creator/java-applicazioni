/*
 * FILE: AccountController.java
 * A COSA SERVE: la pagina "Cambia password" per chi è loggato (/account/password). Chi la usa modifica la
 * PROPRIA password: l'utente su cui si agisce non arriva dall'URL ma dalla sessione (Principal), quindi nessuno
 * può cambiare la password di un altro passando un id diverso.
 * La pagina è dentro il pannello, quindi serve il ruolo ADMIN (regola generale di SecurityConfig).
 */
package it.palestra.web;

import it.palestra.service.UtenteService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/account/password")
public class AccountController {

    private final UtenteService service;

    public AccountController(UtenteService service) {
        this.service = service;
    }

    // GET /account/password -> mostra il form vuoto. "Principal" è l'utente loggato: getName() è la sua email.
    @GetMapping
    public String form(Model model, Principal utenteLoggato) {
        model.addAttribute("cambiaPasswordForm", new CambiaPasswordForm());
        model.addAttribute("admin", utenteLoggato.getName());   // la barra in alto mostra chi è loggato e "Esci"
        return "account/password";
    }

    // POST /account/password -> esegue il cambio. I controlli, in ordine:
    //  1) tutti i campi compilati (@Valid);  2) la password ATTUALE è giusta (altrimenti chiunque trovi un
    //  computer lasciato sbloccato potrebbe cambiarla);  3) la nuova rispetta la lunghezza;
    //  4) nuova e conferma coincidono (evita un errore di battitura che chiuderebbe fuori l'utente);
    //  5) la nuova è diversa dall'attuale.
    // Ogni controllo si fa solo se i precedenti sul campo non hanno già dato errore, per non mostrare messaggi
    // doppi o inutili. In caso di errori si rimostra il form, e le password NON vengono rimesse nei campi.
    @PostMapping
    public String cambia(@Valid @ModelAttribute CambiaPasswordForm cambiaPasswordForm, BindingResult errori,
                         Principal utenteLoggato, Model model, RedirectAttributes flash) {
        String email = utenteLoggato.getName();
        String attuale = cambiaPasswordForm.getPasswordAttuale();
        String nuova = cambiaPasswordForm.getNuovaPassword();

        if (!errori.hasFieldErrors("passwordAttuale") && !service.passwordCorrisponde(email, attuale)) {
            errori.rejectValue("passwordAttuale", "errata", "La password attuale non è corretta");
        }
        if (!errori.hasFieldErrors("nuovaPassword")) {
            service.erroreLunghezzaPassword(nuova)
                    .ifPresent(m -> errori.rejectValue("nuovaPassword", "non-valida", m));
        }
        if (!errori.hasFieldErrors("nuovaPassword") && nuova.equals(attuale)) {
            errori.rejectValue("nuovaPassword", "uguale", "La nuova password deve essere diversa da quella attuale");
        }
        if (!errori.hasFieldErrors("confermaPassword") && !errori.hasFieldErrors("nuovaPassword")
                && !nuova.equals(cambiaPasswordForm.getConfermaPassword())) {
            errori.rejectValue("confermaPassword", "diversa", "Le due password non coincidono");
        }

        if (errori.hasErrors()) {
            model.addAttribute("admin", email);
            return "account/password";
        }

        service.cambiaPassword(email, nuova);
        flash.addFlashAttribute("messaggio", "Password aggiornata");
        return "redirect:/utenti";
    }
}
