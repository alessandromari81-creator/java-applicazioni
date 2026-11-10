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

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    // GET / -> redirect a /utenti. Se non sei loggato Security ti porta prima al login.
    @GetMapping("/")
    public String home() {
        return "redirect:/utenti";
    }

    // GET /login -> restituisce "login", cioè mostra templates/login.html.
    // Questa è la pagina verso cui Spring Security rimanda chi non è loggato. L'invio del form (POST /login) è
    // intercettato da Spring Security, per questo non c'è un metodo @PostMapping.
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
