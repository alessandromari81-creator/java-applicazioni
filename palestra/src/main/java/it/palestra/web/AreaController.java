/*
 * FILE: AreaController.java
 * A COSA SERVE: le pagine dell'AREA PERSONALE (/area) per chi ha il ruolo ISCRITTO o ISTRUTTORE:
 *   GET  /area                      riepilogo: profilo, abbonamento più significativo, corsi
 *   GET  /area/corsi                corsi frequentati e, per l'istruttore, corsi tenuti con i nomi degli iscritti
 *   GET  /area/abbonamenti          storico dei propri abbonamenti (paginato)
 *   GET  /area/profilo              dati personali e consensi
 *   POST /area/profilo/marketing    dà o ritira il consenso alle comunicazioni promozionali
 * La persona è SEMPRE quella che ha fatto login (Principal): nessuna pagina accetta un id di utente dall'indirizzo, quindi non si può guardare
 * ciò che appartiene a un altro. L'accesso a /area/** è riservato a ISCRITTO e ISTRUTTORE da SecurityConfig.
 * Tutte le pagine sono di sola lettura, tranne il consenso marketing.
 */
package it.palestra.web;

import it.palestra.model.Abbonamento;
import it.palestra.pagina.NavigazionePagine;
import it.palestra.pagina.OrdinamentiListe;
import it.palestra.pagina.RichiestaPagina;
import it.palestra.pagina.StatoLista;
import it.palestra.service.AreaService;
import java.security.Principal;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/area")
public class AreaController {

    private final AreaService service;

    public AreaController(AreaService service) {
        this.service = service;
    }

    // Riepilogo. "utenteArea" (l'email di chi è loggato) fa comparire nel layout la barra dell'area, con il pulsante "Esci".
    @GetMapping
    public String home(Model model, Principal utente) {
        AreaService.Riepilogo riepilogo = service.riepilogo(utente.getName());
        model.addAttribute("r", riepilogo);
        model.addAttribute("oggi", service.oggi());
        model.addAttribute("utenteArea", utente.getName());
        return "area/home";
    }

    @GetMapping("/corsi")
    public String corsi(Model model, Principal utente) {
        model.addAttribute("r", service.riepilogo(utente.getName()));
        model.addAttribute("utenteArea", utente.getName());
        return "area/corsi";
    }

    // Storico degli abbonamenti, con la stessa paginazione e ordinamento dalle intestazioni delle altre liste. Se la pagina chiesta non esiste
    // più si va all'ultima.
    @GetMapping("/abbonamenti")
    public String abbonamenti(@ModelAttribute("lista") StatoLista lista, Model model, RedirectAttributes flash, Principal utente) {
        lista.setQ(null);                                              // sullo storico non c'è la ricerca
        RichiestaPagina richiesta = lista.richiesta(OrdinamentiListe.ABBONAMENTI_DI_UTENTE);
        Page<Abbonamento> pagina = service.storicoAbbonamenti(utente.getName(), richiesta);
        if (NavigazionePagine.oltreLUltima(pagina, richiesta)) {
            return "redirect:/area/abbonamenti"
                    + lista.queryPagina(OrdinamentiListe.ABBONAMENTI_DI_UTENTE, pagina.getTotalPages() - 1);
        }
        model.addAttribute("abbonamenti", pagina.getContent());
        model.addAttribute("nav", new NavigazionePagine("/area/abbonamenti", pagina, richiesta));
        model.addAttribute("oggi", service.oggi());
        model.addAttribute("utenteArea", utente.getName());
        return "area/abbonamenti";
    }

    @GetMapping("/profilo")
    public String profilo(Model model, Principal utente) {
        model.addAttribute("r", service.riepilogo(utente.getName()));
        model.addAttribute("utenteArea", utente.getName());
        return "area/profilo";
    }

    // Spunta o toglie il consenso facoltativo alle comunicazioni promozionali (la casella inviata dal form: presente = consenso).
    @PostMapping("/profilo/marketing")
    public String marketing(@RequestParam(name = "consenso", defaultValue = "false") boolean consenso,
                            Principal utente, RedirectAttributes flash) {
        service.impostaConsensoMarketing(utente.getName(), consenso);
        flash.addFlashAttribute("messaggio", consenso
                ? "Consenso alle comunicazioni promozionali registrato"
                : "Consenso alle comunicazioni promozionali ritirato");
        return "redirect:/area/profilo";
    }
}
