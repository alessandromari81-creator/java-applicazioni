/*
 * FILE: UtenteController.java
 * A COSA SERVE: gestisce le pagine /utenti: riceve le richieste HTTP, chiama il service e sceglie quale
 * template mostrare. Non contiene logica di business.
 * PARAGONE LARAVEL: un resource controller (index, create, store, edit, update, destroy).
 * Le rotte stanno qui sopra ai metodi con le annotazioni, non in routes/web.php.
 */
package it.palestra.web;

import it.palestra.exception.RegolaViolataException;
import it.palestra.service.AbbonamentoService;
import it.palestra.service.UtenteService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// @Controller = questa classe risponde a richieste web e restituisce NOMI di template.
// @RequestMapping("/utenti") = prefisso comune a tutte le rotte (come Route::prefix('utenti')).
@Controller
@RequestMapping("/utenti")
public class UtenteController {

    private final UtenteService service;
    private final AbbonamentoService abbonamenti;

    // I service arrivano tramite dependency injection (Spring li passa al costruttore).
    public UtenteController(UtenteService service, AbbonamentoService abbonamenti) {
        this.service = service;
        this.abbonamenti = abbonamenti;
    }

    // GET /utenti -> lista. "Model" è il contenitore dei dati per il template (come compact()/with()).
    // "Principal" è l'utente loggato (Auth::user()): getName() restituisce la sua email.
    // return "utenti/lista" significa: usa templates/utenti/lista.html.
    @GetMapping
    public String lista(Model model, Principal admin) {
        model.addAttribute("utenti", service.elenco());
        // Per la colonna "Abbonamento": per ogni utente quello più significativo (attivo, prossimo o ultimo
        // scaduto) e la data di oggi, con cui il template calcola lo stato.
        model.addAttribute("abbonamenti", abbonamenti.riepilogoPerUtente());
        model.addAttribute("oggi", abbonamenti.oggi());
        model.addAttribute("admin", admin.getName());
        return "utenti/lista";
    }

    // GET /utenti/nuovo -> form vuoto di creazione (create()).
    @GetMapping("/nuovo")
    public String nuovo(Model model) {
        model.addAttribute("utenteForm", new UtenteForm());
        return formView(model, null);
    }

    // POST /utenti -> salva il nuovo utente (store()).
    // @Valid = esegui la validazione di UtenteForm. @ModelAttribute = riempi l'oggetto con i campi del form.
    // BindingResult = contiene gli errori di validazione (in Laravel il redirect con $errors è automatico;
    // qui, se ci sono errori, riproponiamo il form).
    // RedirectAttributes = messaggio "flash" per la pagina dopo il redirect (session()->flash()).
    @PostMapping
    public String crea(@Valid @ModelAttribute UtenteForm utenteForm, BindingResult errori,
                       Model model, RedirectAttributes flash) {
        controllaEmail(utenteForm, null, errori);
        controllaPassword(utenteForm, null, errori);
        controllaCorsi(utenteForm, null, errori);
        if (errori.hasErrors()) return formView(model, null);   // errori: rimostra il form
        service.crea(utenteForm);
        flash.addFlashAttribute("messaggio", "Utente creato");
        return "redirect:/utenti";   // pattern Post-Redirect-Get: evita il doppio invio al refresh
    }

    // GET /utenti/5/modifica -> form precompilato (edit()). {id} nel percorso arriva
    // nel parametro con @PathVariable (come il route model binding).
    @GetMapping("/{id}/modifica")
    public String modifica(@PathVariable Long id, Model model) {
        model.addAttribute("utenteForm", UtenteForm.da(service.trova(id)));
        return formView(model, id);
    }

    // POST /utenti/5 -> salva le modifiche (update()). I form HTML supportano solo GET e POST,
    // per questo non usiamo PUT/PATCH (in Laravel servirebbe @method('PUT')).
    @PostMapping("/{id}")
    public String aggiorna(@PathVariable Long id, @Valid @ModelAttribute UtenteForm utenteForm,
                           BindingResult errori, Model model, RedirectAttributes flash) {
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
        return "redirect:/utenti";
    }

    // POST /utenti/5/elimina -> cancella (destroy()). Mai con GET: un link potrebbe cancellare dati solo
    // visitandolo. Se il service rifiuta (ultimo admin, o istruttore di corsi) mostriamo il suo messaggio in rosso.
    @PostMapping("/{id}/elimina")
    public String elimina(@PathVariable Long id, RedirectAttributes flash) {
        try {
            service.elimina(id);
            flash.addFlashAttribute("messaggio", "Utente eliminato");
        } catch (RegolaViolataException e) {
            flash.addFlashAttribute("errore", e.getMessage());
        }
        return "redirect:/utenti";
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
}
