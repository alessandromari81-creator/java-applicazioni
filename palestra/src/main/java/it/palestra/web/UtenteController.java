/*
 * FILE: UtenteController.java
 * A COSA SERVE: gestisce le pagine /utenti: riceve le richieste HTTP, chiama il service e sceglie quale
 * template mostrare. Non contiene logica di business.
 * PARAGONE LARAVEL: un resource controller (index, create, store, edit, update, destroy).
 * Le rotte stanno qui sopra ai metodi con le annotazioni, non in routes/web.php.
 */
package it.palestra.web;

import it.palestra.exception.RegolaViolataException;
import it.palestra.model.Utente;
import it.palestra.pagina.NavigazionePagine;
import it.palestra.pagina.OrdinamentiListe;
import it.palestra.pagina.RichiestaPagina;
import it.palestra.pagina.StatoLista;
import it.palestra.service.AbbonamentoService;
import it.palestra.service.RecuperoPasswordService;
import it.palestra.service.UtenteService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// @Controller = questa classe risponde a richieste web e restituisce NOMI di template.
// @RequestMapping("/utenti") = prefisso comune a tutte le rotte.
@Controller
@RequestMapping("/utenti")
public class UtenteController {

    private final UtenteService service;
    private final AbbonamentoService abbonamenti;
    private final RecuperoPasswordService recupero;

    // I service arrivano tramite dependency injection (Spring li passa al costruttore).
    public UtenteController(UtenteService service, AbbonamentoService abbonamenti, RecuperoPasswordService recupero) {
        this.service = service;
        this.abbonamenti = abbonamenti;
        this.recupero = recupero;
    }

    // GET /utenti -> lista. "Model" è il contenitore dei dati per il template.
    // "Principal" è l'utente loggato: getName() restituisce la sua email.
    // return "utenti/lista" significa: usa templates/utenti/lista.html.
    //
    // LISTA PAGINATA: Spring riempie "lista" (uno StatoLista) con i parametri ?page=&size=&sort=&q= dell'indirizzo, che
    // vengono validati (400 se sbagliati). Se la pagina chiesta non esiste più si va all'ultima.
    @GetMapping
    public String lista(@ModelAttribute("lista") StatoLista lista, Model model, RedirectAttributes flash, Principal admin) {
        RichiestaPagina richiesta = lista.richiesta(OrdinamentiListe.UTENTI);
        Page<Utente> pagina = service.elenco(richiesta);
        if (NavigazionePagine.oltreLUltima(pagina, richiesta)) {
            SupportoListe.riportaMessaggi(model, flash);
            return "redirect:/utenti" + lista.queryPagina(OrdinamentiListe.UTENTI, pagina.getTotalPages() - 1);
        }
        List<Utente> mostrati = pagina.getContent();
        model.addAttribute("utenti", mostrati);
        model.addAttribute("nav", new NavigazionePagine("/utenti", pagina, richiesta));   // controlli di pagina/ordinamento
        // Per la colonna "Abbonamento": per ogni utente MOSTRATO quello più significativo (attivo, prossimo o ultimo
        // scaduto) e la data di oggi, con cui il template calcola lo stato.
        model.addAttribute("abbonamenti", abbonamenti.riepilogoPerUtente(mostrati.stream().map(u -> u.getId()).toList()));
        model.addAttribute("oggi", abbonamenti.oggi());
        model.addAttribute("admin", admin.getName());
        return "utenti/lista";
    }

    // GET /utenti/nuovo -> form vuoto di creazione (create()).
    @GetMapping("/nuovo")
    public String nuovo(@ModelAttribute("lista") StatoLista lista, Model model) {
        model.addAttribute("utenteForm", new UtenteForm());
        return formView(model, null);
    }

    // POST /utenti -> salva il nuovo utente (store()).
    // @Valid = esegui la validazione di UtenteForm. @ModelAttribute = riempi l'oggetto con i campi del form.
    // BindingResult = contiene gli errori di validazione
    // (se ci sono errori, riproponiamo il form).
    // RedirectAttributes = messaggio "flash" per la pagina dopo il redirect.
    @PostMapping
    public String crea(@Valid @ModelAttribute UtenteForm utenteForm, BindingResult errori,
                       @ModelAttribute("lista") StatoLista lista, Model model, RedirectAttributes flash) {
        controllaEmail(utenteForm, null, errori);
        controllaPassword(utenteForm, null, errori);
        controllaCorsi(utenteForm, null, errori);
        if (errori.hasErrors()) return formView(model, null);   // errori: rimostra il form
        service.crea(utenteForm);
        flash.addFlashAttribute("messaggio", "Utente creato");
        // Pattern Post-Redirect-Get (evita il doppio invio al refresh), tornando alla lista com'era (pagina, ordine, ricerca).
        return "redirect:/utenti" + lista.query(OrdinamentiListe.UTENTI);
    }

    // GET /utenti/5/modifica -> form precompilato (edit()). {id} nel percorso arriva
    // nel parametro con @PathVariable (come il route model binding).
    @GetMapping("/{id}/modifica")
    public String modifica(@PathVariable Long id, @ModelAttribute("lista") StatoLista lista, Model model) {
        model.addAttribute("utenteForm", UtenteForm.da(service.trova(id)));
        return formView(model, id);
    }

    // POST /utenti/5 -> salva le modifiche (update()). I form HTML supportano solo GET e POST,
    // per questo non usiamo PUT/PATCH.
    @PostMapping("/{id}")
    public String aggiorna(@PathVariable Long id, @Valid @ModelAttribute UtenteForm utenteForm,
                           BindingResult errori, @ModelAttribute("lista") StatoLista lista, Model model,
                           RedirectAttributes flash) {
        controllaEmail(utenteForm, id, errori);
        controllaPassword(utenteForm, id, errori);
        controllaCorsi(utenteForm, id, errori);
        if (errori.hasErrors()) return formView(model, id);
        try {
            service.modifica(id, utenteForm);
        } catch (RegolaViolataException e) {
            // Il service ha rifiutato (es. si toglie ADMIN all'ultimo admin, o ISTRUTTORE a chi tiene corsi):
            // errore "globale" del form, cioè non legato a un campo, mostrato in cima al form.
            errori.reject("regola", e.getMessage());
            return formView(model, id);
        }
        flash.addFlashAttribute("messaggio", "Utente aggiornato");
        return "redirect:/utenti" + lista.query(OrdinamentiListe.UTENTI);
    }

    // POST /utenti/5/elimina -> cancella (destroy()). Mai con GET: un link potrebbe cancellare dati solo
    // visitandolo. Se il service rifiuta (ultimo admin, o istruttore di corsi) mostriamo il suo messaggio in rosso.
    @PostMapping("/{id}/elimina")
    public String elimina(@PathVariable Long id, @ModelAttribute("lista") StatoLista lista, RedirectAttributes flash) {
        try {
            service.elimina(id);
            flash.addFlashAttribute("messaggio", "Utente eliminato");
        } catch (RegolaViolataException e) {
            flash.addFlashAttribute("errore", e.getMessage());
        }
        return "redirect:/utenti" + lista.query(OrdinamentiListe.UTENTI);
    }

    // Prepara i dati comuni a creazione e modifica (corsi e ruoli per le checkbox, id dell'utente: null
    // se nuovo, e gli id dei corsi che l'utente TIENE come istruttore, le cui checkbox vanno disattivate)
    // e restituisce il template del form.
    private String formView(Model model, Long id) {
        model.addAttribute("corsiInsegnatiIds", service.idCorsiInsegnati(id));
        model.addAttribute("corsi", service.tuttiICorsi());
        model.addAttribute("ruoli", service.tuttiIRuoli());
        model.addAttribute("utenteId", id);
        return "utenti/form";
    }

    // Validazione "a mano": email già usata da un altro utente. Si esegue solo se l'email non ha già errori di
    // formato. rejectValue aggiunge l'errore al campo "email" e Thymeleaf lo mostra accanto al campo.
    private void controllaEmail(UtenteForm form, Long id, BindingResult errori) {
        if (!errori.hasFieldErrors("email") && service.emailInUso(form.getEmail().trim(), id)) {
            errori.rejectValue("email", "duplicata", "Email già registrata");
        }
    }

    // Validazione "a mano" della password (regole nel service, perché dipendono dai ruoli scelti).
    private void controllaPassword(UtenteForm form, Long id, BindingResult errori) {
        service.erroreSullaPassword(form, id)
                .ifPresent(messaggio -> errori.rejectValue("password", "non-valida", messaggio));
    }

    // Validazione "a mano" dei corsi scelti: un istruttore non può iscriversi ai corsi che tiene. Il messaggio
    // (con i nomi dei corsi) diventa un errore sul campo "corsiIds", mostrato sotto l'elenco dei corsi.
    private void controllaCorsi(UtenteForm form, Long id, BindingResult errori) {
        service.erroreSuiCorsiScelti(form, id)
                .ifPresent(messaggio -> errori.rejectValue("corsiIds", "istruttore-del-corso", messaggio));
    }

    // POST /utenti/5/invito -> invia a un utente SENZA password l'email con il link per scegliere la password (e quindi poter accedere alla sua area).
    // Se ha già una password o l'email non parte si mostra il messaggio in rosso. Poi si torna alla stessa pagina della lista.
    @PostMapping("/{id}/invito")
    public String invito(@PathVariable Long id, @ModelAttribute("lista") StatoLista lista, RedirectAttributes flash) {
        try {
            String email = recupero.invita(id);
            flash.addFlashAttribute("messaggio", "Invito inviato a " + email);
        } catch (RegolaViolataException e) {
            flash.addFlashAttribute("errore", e.getMessage());
        }
        return "redirect:/utenti" + lista.query(OrdinamentiListe.UTENTI);
    }
}
