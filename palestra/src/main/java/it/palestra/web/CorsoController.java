/*
 * FILE: CorsoController.java
 * A COSA SERVE: gestisce le pagine /corsi (lista, nuovo, modifica, elimina). Come
 * UtenteController: riceve la richiesta, chiama il service, sceglie il template.
 * PARAGONE LARAVEL: un resource controller per Corso.
 */
package it.palestra.web;

import it.palestra.exception.RegolaViolataException;
import it.palestra.model.Corso;
import it.palestra.model.Livello;
import it.palestra.pagina.NavigazionePagine;
import it.palestra.pagina.OrdinamentiListe;
import it.palestra.pagina.RichiestaPagina;
import it.palestra.pagina.StatoLista;
import it.palestra.service.CorsoService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/corsi")
public class CorsoController {

    private final CorsoService service;

    public CorsoController(CorsoService service) {
        this.service = service;
    }

    // GET /corsi -> lista (templates/corsi/lista.html).
    // LISTA PAGINATA: i parametri ?page=&size=&sort=&q= dell'indirizzo riempiono "lista" e vengono validati (400 se sbagliati).
    @GetMapping
    public String lista(@ModelAttribute("lista") StatoLista lista, Model model, RedirectAttributes flash, Principal admin) {
        RichiestaPagina richiesta = lista.richiesta(OrdinamentiListe.CORSI);
        Page<Corso> pagina = service.elenco(richiesta);
        if (NavigazionePagine.oltreLUltima(pagina, richiesta)) {
            SupportoListe.riportaMessaggi(model, flash);
            return "redirect:/corsi" + lista.queryPagina(OrdinamentiListe.CORSI, pagina.getTotalPages() - 1);
        }
        model.addAttribute("corsi", pagina.getContent());
        model.addAttribute("nav", new NavigazionePagine("/corsi", pagina, richiesta));
        model.addAttribute("admin", admin.getName());
        return "corsi/lista";
    }

    // GET /corsi/nuovo -> form vuoto.
    @GetMapping("/nuovo")
    public String nuovo(@ModelAttribute("lista") StatoLista lista, Model model) {
        model.addAttribute("corsoForm", new CorsoForm());
        return formView(model, null);
    }

    // POST /corsi -> crea. Se la validazione fallisce, rimostra il form con gli errori.
    @PostMapping
    public String crea(@Valid @ModelAttribute CorsoForm corsoForm, BindingResult errori,
                       @ModelAttribute("lista") StatoLista lista, Model model, RedirectAttributes flash) {
        controllaIstruttore(corsoForm, null, errori);
        if (errori.hasErrors()) return formView(model, null);
        service.crea(corsoForm.comeDati());
        flash.addFlashAttribute("messaggio", "Corso creato");
        return "redirect:/corsi" + lista.query(OrdinamentiListe.CORSI);
    }

    // GET /corsi/5/modifica -> form precompilato.
    @GetMapping("/{id}/modifica")
    public String modifica(@PathVariable Long id, @ModelAttribute("lista") StatoLista lista, Model model) {
        model.addAttribute("corsoForm", CorsoForm.da(service.trova(id)));
        return formView(model, id);
    }

    // POST /corsi/5 -> salva le modifiche.
    @PostMapping("/{id}")
    public String aggiorna(@PathVariable Long id, @Valid @ModelAttribute CorsoForm corsoForm,
                           BindingResult errori, @ModelAttribute("lista") StatoLista lista, Model model,
                           RedirectAttributes flash) {
        controllaIstruttore(corsoForm, id, errori);
        if (errori.hasErrors()) return formView(model, id);
        service.modifica(id, corsoForm.comeDati());
        flash.addFlashAttribute("messaggio", "Corso aggiornato");
        return "redirect:/corsi" + lista.query(OrdinamentiListe.CORSI);
    }

    // POST /corsi/5/elimina -> elimina, oppure mostra l'errore se ci sono iscritti.
    // try/catch: se il service lancia RegolaViolataException
    // mostriamo il suo messaggio (flash "errore") invece di una pagina di errore.
    @PostMapping("/{id}/elimina")
    public String elimina(@PathVariable Long id, @ModelAttribute("lista") StatoLista lista, RedirectAttributes flash) {
        try {
            service.elimina(id);
            flash.addFlashAttribute("messaggio", "Corso eliminato");
        } catch (RegolaViolataException e) {
            flash.addFlashAttribute("errore", e.getMessage());
        }
        return "redirect:/corsi" + lista.query(OrdinamentiListe.CORSI);
    }

    // Dati comuni a creazione e modifica: i livelli per la tendina (Livello.values() = tutti i
    // valori dell'enum), gli istruttori scelgibili (solo utenti con ruolo ISTRUTTORE) e l'id del corso
    // (null se nuovo).
    private String formView(Model model, Long id) {
        model.addAttribute("livelli", Livello.values());
        model.addAttribute("istruttori", service.istruttoriDisponibili());
        model.addAttribute("corsoId", id);
        return "corsi/form";
    }

    // Validazione "a mano" dell'istruttore: deve avere il ruolo ISTRUTTORE e non essere iscritto al corso.
    // Le regole stanno nel service; qui il messaggio diventa un errore sul campo "istruttoreId", che
    // Thymeleaf mostra sotto la tendina.
    private void controllaIstruttore(CorsoForm form, Long corsoId, BindingResult errori) {
        service.erroreSullIstruttore(form.comeDati(), corsoId)
                .ifPresent(messaggio -> errori.rejectValue("istruttoreId", "non-valido", messaggio));
    }
}
