/*
 * FILE: AbbonamentoController.java
 * A COSA SERVE: gestisce le pagine /abbonamenti: elenco, nuovo abbonamento (anche come rinnovo) ed eliminazione
 * di quelli non ancora iniziati. Non si MODIFICA un abbonamento: per correggere un errore si elimina (se non è
 * ancora iniziato) e se ne crea uno nuovo; per rinnovare se ne crea uno che parte dopo la scadenza.
 * Come gli altri controller: riceve la richiesta, chiama il service, sceglie il template.
 */
package it.palestra.web;

import it.palestra.exception.RegolaViolataException;
import it.palestra.model.Abbonamento;
import it.palestra.model.TipoAbbonamento;
import it.palestra.pagina.NavigazionePagine;
import it.palestra.pagina.OrdinamentiListe;
import it.palestra.pagina.RichiestaPagina;
import it.palestra.pagina.StatoLista;
import it.palestra.service.AbbonamentoService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.format.DateTimeFormatter;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/abbonamenti")
public class AbbonamentoController {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AbbonamentoService service;

    public AbbonamentoController(AbbonamentoService service) {
        this.service = service;
    }

    // GET /abbonamenti -> elenco PAGINATO. "oggi" serve al template per calcolare lo stato di ogni abbonamento.
    // I parametri ?page=&size=&sort=&q= (q cerca nome, cognome o email dell'iscritto) riempiono "lista" e vengono validati.
    @GetMapping
    public String lista(@ModelAttribute("lista") StatoLista lista, Model model, RedirectAttributes flash, Principal admin) {
        RichiestaPagina richiesta = lista.richiesta(OrdinamentiListe.ABBONAMENTI);
        Page<Abbonamento> pagina = service.elenco(richiesta);
        if (NavigazionePagine.oltreLUltima(pagina, richiesta)) {
            SupportoListe.riportaMessaggi(model, flash);
            return "redirect:/abbonamenti" + lista.queryPagina(OrdinamentiListe.ABBONAMENTI, pagina.getTotalPages() - 1);
        }
        model.addAttribute("abbonamenti", pagina.getContent());
        model.addAttribute("nav", new NavigazionePagine("/abbonamenti", pagina, richiesta));
        model.addAttribute("oggi", service.oggi());
        model.addAttribute("admin", admin.getName());
        return "abbonamenti/lista";
    }

    // GET /abbonamenti/utente/5 -> la pagina di UN utente: il suo storico di abbonamenti, l'eliminazione di quelli non
    // ancora iniziati e il form per aggiungerne uno (o rinnovare), già con la data di inizio proposta.
    // Ci si arriva dal link nella colonna "Abbonamento" della lista utenti.
    @GetMapping("/utente/{utenteId}")
    // Anche lo storico è PAGINATO (?page=&size=&sort=); la ricerca q qui non si applica.
    public String perUtente(@PathVariable Long utenteId, @ModelAttribute("lista") StatoLista lista, Model model,
                            RedirectAttributes flash, Principal admin) {
        AbbonamentoForm form = new AbbonamentoForm();
        form.setUtenteId(utenteId);
        form.setDataInizio(service.primoGiornoLibero(utenteId));   // oggi, oppure il giorno dopo la scadenza
        model.addAttribute("abbonamentoForm", form);
        return paginaUtente(utenteId, lista, model, flash, admin);
    }

    // POST /abbonamenti/utente/5 -> aggiunge un abbonamento a QUELL'utente. Stesse regole del form generale (vedi
    // crea()), ma con un'importante differenza: l'utente si ricava dall'URL e NON dal form. Anche se qualcuno
    // inviasse un utenteId diverso nei dati, viene sovrascritto con quello della pagina.
    @PostMapping("/utente/{utenteId}")
    public String creaPerUtente(@PathVariable Long utenteId, @Valid @ModelAttribute AbbonamentoForm abbonamentoForm,
                                BindingResult errori, @ModelAttribute("lista") StatoLista lista, Model model,
                                RedirectAttributes flash, Principal admin) {
        abbonamentoForm.setUtenteId(utenteId);
        controllaRegole(abbonamentoForm, errori);
        if (errori.hasErrors()) return paginaUtente(utenteId, lista, model, flash, admin);   // errori: rimostra la stessa pagina

        var creato = service.crea(abbonamentoForm);
        flash.addFlashAttribute("messaggio", messaggioCreato(creato));
        return "redirect:/abbonamenti/utente/" + utenteId;
    }

    // GET /abbonamenti/nuovo -> form. Con ?utenteId=5 (il link "Abbonamento" nella lista utenti) l'iscritto è già
    // scelto e la data di inizio è già il PRIMO GIORNO LIBERO: oggi, oppure il giorno dopo la scadenza
    // dell'abbonamento in corso. È così che funziona il rinnovo. @RequestParam legge un parametro dell'URL;
    // required = false lo rende facoltativo.
    @GetMapping("/nuovo")
    public String nuovo(@RequestParam(required = false) Long utenteId, Model model) {
        AbbonamentoForm form = new AbbonamentoForm();
        form.setUtenteId(utenteId);
        form.setDataInizio(service.primoGiornoLibero(utenteId));
        model.addAttribute("abbonamentoForm", form);
        return formView(model);
    }

    // POST /abbonamenti -> crea. Prima la validazione del form (@Valid), poi le regole di business del service.
    // Gli errori si aggiungono al BindingResult e, se ce ne sono, si rimostra il form.
    @PostMapping
    public String crea(@Valid @ModelAttribute AbbonamentoForm abbonamentoForm, BindingResult errori,
                       Model model, RedirectAttributes flash) {
        controllaRegole(abbonamentoForm, errori);
        if (errori.hasErrors()) return formView(model);

        var creato = service.crea(abbonamentoForm);
        flash.addFlashAttribute("messaggio", messaggioCreato(creato));
        return "redirect:/abbonamenti";
    }

    // POST /abbonamenti/5/elimina -> elimina SOLO se non è ancora iniziato. Altrimenti il service lancia
    // RegolaViolataException e mostriamo il suo messaggio in rosso.
    // "daUtente": il pulsante nella pagina di un utente aggiunge daUtente=true, così dopo l'eliminazione si torna a
    // QUELLA pagina invece che all'elenco generale. L'indirizzo di ritorno lo costruisce il server con l'id
    // dell'utente dell'abbonamento: non si accetta mai un indirizzo scritto da chi invia la richiesta (evita i
    // cosiddetti "open redirect"). @RequestParam(defaultValue = "false") rende il parametro facoltativo.
    @PostMapping("/{id}/elimina")
    public String elimina(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean daUtente,
                          @ModelAttribute("lista") StatoLista lista, RedirectAttributes flash) {
        Long utenteId = service.utenteDi(id);   // letto PRIMA di eliminare (poi l'abbonamento non esiste più); 404 se manca
        try {
            service.elimina(id);
            flash.addFlashAttribute("messaggio", "Abbonamento eliminato");
        } catch (RegolaViolataException e) {
            flash.addFlashAttribute("errore", e.getMessage());
        }
        // Si torna alla stessa vista (pagina, ordine, ricerca) da cui si è partiti. Sullo storico di un utente la ricerca non c'è.
        if (daUtente) {
            lista.setQ(null);
            return "redirect:/abbonamenti/utente/" + utenteId + lista.query(OrdinamentiListe.ABBONAMENTI_DI_UTENTE);
        }
        return "redirect:/abbonamenti" + lista.query(OrdinamentiListe.ABBONAMENTI);
    }

    // Le regole di business sul form (usate sia dal form generale sia da quello nella pagina dell'utente):
    // prima l'utente scelto, poi la data. Gli errori si aggiungono al BindingResult.
    private void controllaRegole(AbbonamentoForm form, BindingResult errori) {
        service.erroreSulUtente(form)
                .ifPresent(m -> errori.rejectValue("utenteId", "non-valido", m));
        // La data si controlla solo se non ha già errori (es. campo vuoto o formato errato) e l'utente è valido.
        if (!errori.hasFieldErrors("dataInizio") && !errori.hasFieldErrors("utenteId")) {
            service.erroreSullaDataDiInizio(form)
                    .ifPresent(m -> errori.rejectValue("dataInizio", "non-valida", m));
        }
    }

    // Il messaggio di conferma dopo la creazione, es. "Abbonamento mensile creato: dal 09/11/2026 al 08/12/2026".
    private String messaggioCreato(it.palestra.model.Abbonamento a) {
        return "Abbonamento " + a.getTipo().getEtichetta().toLowerCase() + " creato: dal "
                + a.getDataInizio().format(DATA) + " al " + a.getDataFine().format(DATA);
    }

    // Dati per la pagina di un utente: lui, UNA PAGINA del suo storico, la data di oggi (per gli stati) e i tipi disponibili.
    // Il form (abbonamentoForm) lo ha già preparato il metodo che chiama questo. Se la pagina dello storico chiesta non
    // esiste più si va all'ultima.
    private String paginaUtente(Long utenteId, StatoLista lista, Model model, RedirectAttributes flash, Principal admin) {
        lista.setQ(null);                                          // sullo storico non c'è la ricerca
        RichiestaPagina richiesta = lista.richiesta(OrdinamentiListe.ABBONAMENTI_DI_UTENTE);
        Page<Abbonamento> pagina = service.abbonamentiDi(utenteId, richiesta);
        if (NavigazionePagine.oltreLUltima(pagina, richiesta)) {
            SupportoListe.riportaMessaggi(model, flash);
            return "redirect:/abbonamenti/utente/" + utenteId
                    + lista.queryPagina(OrdinamentiListe.ABBONAMENTI_DI_UTENTE, pagina.getTotalPages() - 1);
        }
        model.addAttribute("utente", service.trovaUtente(utenteId));
        model.addAttribute("abbonamenti", pagina.getContent());
        model.addAttribute("nav", new NavigazionePagine("/abbonamenti/utente/" + utenteId, pagina, richiesta));
        model.addAttribute("oggi", service.oggi());
        model.addAttribute("tipi", TipoAbbonamento.values());
        model.addAttribute("admin", admin.getName());
        return "abbonamenti/utente";
    }

    // Dati per la pagina del form: gli iscritti tra cui scegliere e i tipi (con durata e prezzo di listino).
    private String formView(Model model) {
        model.addAttribute("iscritti", service.iscrittiDisponibili());
        model.addAttribute("tipi", TipoAbbonamento.values());
        return "abbonamenti/form";
    }
}
