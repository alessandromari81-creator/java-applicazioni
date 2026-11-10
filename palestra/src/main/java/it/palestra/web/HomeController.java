/*
 * FILE: HomeController.java
 * A COSA SERVE: gestisce due rotte semplici: la home "/" (che rimanda al pannello) e la
 * pagina "/login". Il login vero (controllo di username e password) lo fa Spring Security,
 * qui serve solo a mostrare la pagina.
 *
 * COME UN CONTROLLER SI COLLEGA A UNA PAGINA (vale per tutti i controller):
 *  1. L'URL si associa al metodo con @GetMapping("/percorso") o @PostMapping(...)
 *  2. Il metodo restituisce una STRINGA: è il nome del template senza ".html", relativo a
 *     src/main/resources/templates/. Esempio: return "login" -> templates/login.html;
 *     return "utenti/lista" -> templates/utenti/lista.html.
 *  3. I dati per la pagina si passano con model.addAttribute("nome", valore).
 *     Nel template li leggi con ${nome}.
 *  4. Se invece restituisci "redirect:/percorso" il browser viene mandato a un altro URL.
 */
package it.palestra.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final String versionePrivacy;

    public HomeController(@Value("${app.privacy.versione}") String versionePrivacy) {
        this.versionePrivacy = versionePrivacy;
    }

    // GET / -> redirect a /utenti. Se non sei loggato Security ti porta prima al login.
    // La radice manda ognuno alla propria pagina: l'amministratore al pannello, iscritti e istruttori alla loro area. (Chi non ha fatto login
    // non arriva qui: SecurityConfig lo rimanda alla pagina di login.)
    @GetMapping("/")
    public String home(Authentication autenticazione) {
        boolean admin = autenticazione != null
                && autenticazione.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return admin ? "redirect:/utenti" : "redirect:/area";
    }

    // L'informativa sulla privacy: pagina pubblica (la leggono anche i visitatori prima di registrarsi). La versione del testo è
    // quella salvata insieme al consenso di chi si registra (app.privacy.versione).
    @GetMapping("/privacy")
    public String privacy(Model model) {
        model.addAttribute("versionePrivacy", versionePrivacy);
        return "privacy";
    }

    // GET /login -> restituisce "login", cioè mostra templates/login.html.
    // Questa è la pagina verso cui Spring Security rimanda chi non è loggato. L'invio del form (POST /login) è
    // intercettato da Spring Security, per questo non c'è un metodo @PostMapping.
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
