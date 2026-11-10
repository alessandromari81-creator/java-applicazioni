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
import org.springframework.security.core.Authentication;
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
    public String form(Model model, Authentication utenteLoggato) {
        model.addAttribute("cambiaPasswordForm", new CambiaPasswordForm());
        passaUtenteAllaBarra(model, utenteLoggato);
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
                         Authentication utenteLoggato, Model model, RedirectAttributes flash) {
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
            passaUtenteAllaBarra(model, utenteLoggato);
            return "account/password";
        }

        service.cambiaPassword(email, nuova);
        flash.addFlashAttribute("messaggio", "Password aggiornata");
        return èAdmin(utenteLoggato) ? "redirect:/utenti" : "redirect:/area";
    }

    // La barra in alto mostra chi è loggato e "Esci": quella del pannello per l'amministratore ("admin"), quella dell'area personale per gli altri
    // ("utenteArea"). Vedi layout/base.html.
    private void passaUtenteAllaBarra(Model model, Authentication utenteLoggato) {
        model.addAttribute(èAdmin(utenteLoggato) ? "admin" : "utenteArea", utenteLoggato.getName());
    }

    private boolean èAdmin(Authentication utenteLoggato) {
        return utenteLoggato.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
