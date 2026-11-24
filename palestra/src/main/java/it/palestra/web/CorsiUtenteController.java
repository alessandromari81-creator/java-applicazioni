/*
 * FILE: CorsiUtenteController.java
 * A COSA SERVE: la pagina «Corsi» di UN utente (solo ADMIN): i corsi a cui è iscritto, quelli a cui può essere iscritto (con ricerca e paginazione)
 * e i pulsanti per iscriverlo e disiscriverlo, che chiedono conferma con una finestra di dialogo. Ci si arriva dal pulsante nella colonna «Corsi»
 * della lista utenti. Ha preso il posto dell'elenco di caselle nel modulo di modifica dell'utente, che con molti corsi diventava ingestibile.
 *   GET  /utenti/{utenteId}/corsi                       la pagina
 *   POST /utenti/{utenteId}/corsi/{corsoId}/iscrivi     iscrive
 *   POST /utenti/{utenteId}/corsi/{corsoId}/disiscrivi  toglie l'iscrizione
 * L'utente e il corso arrivano SEMPRE dall'indirizzo, mai da un campo del modulo. Le regole (abbonamento attivo oggi, istruttore del corso) sono in
 * IscrizioneService; qui si traducono in messaggi: verde se è andato tutto bene, rosso se la regola ha rifiutato.
 */
package it.palestra.web;

import it.palestra.exception.RegolaViolataException;
import it.palestra.model.Corso;
import it.palestra.model.Utente;
import it.palestra.pagina.NavigazionePagine;
import it.palestra.pagina.OrdinamentiListe;
import it.palestra.pagina.RichiestaPagina;
import it.palestra.pagina.StatoLista;
import it.palestra.service.IscrizioneService;
import java.security.Principal;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/utenti/{utenteId}/corsi")
public class CorsiUtenteController {

    private final IscrizioneService service;

    public CorsiUtenteController(IscrizioneService service) {
        this.service = service;
    }

    // GET /utenti/5/corsi -> la pagina. I parametri ?page=&size=&sort=&q= riguardano la lista dei corsi DISPONIBILI (q cerca nel nome del corso);
    // quella dei corsi a cui è già iscritto è breve e non si pagina. Se si chiede una pagina oltre l'ultima (per esempio dopo aver iscritto l'ultimo
    // corso della pagina) si torna all'ultima, portandosi dietro i messaggi.
    @GetMapping
    public String lista(@PathVariable Long utenteId, @ModelAttribute("lista") StatoLista lista, Model model,
                        RedirectAttributes flash, Principal admin) {
        Utente utente = service.trovaUtente(utenteId);                     // 404 se non esiste
        RichiestaPagina richiesta = lista.richiesta(OrdinamentiListe.CORSI_DISPONIBILI);
        Page<Corso> pagina = service.corsiDisponibili(utenteId, richiesta);
        if (NavigazionePagine.oltreLUltima(pagina, richiesta)) {
            SupportoListe.riportaMessaggi(model, flash);
            return "redirect:" + indirizzo(utenteId) + lista.queryPagina(OrdinamentiListe.CORSI_DISPONIBILI, pagina.getTotalPages() - 1);
        }
        model.addAttribute("utente", utente);
        model.addAttribute("iscrittoA", service.corsiDi(utenteId));
        model.addAttribute("disponibili", pagina.getContent());
        model.addAttribute("nav", new NavigazionePagine(indirizzo(utenteId), pagina, richiesta));
        model.addAttribute("puoIscriversi", service.puoIscriversi(utente));
        model.addAttribute("admin", admin.getName());
        return "utenti/corsi";
    }

    // POST /utenti/5/corsi/3/iscrivi -> iscrive l'utente al corso. Se una regola lo vieta (abbonamento non attivo, istruttore del corso, già iscritto)
    // si mostra il messaggio in rosso; in ogni caso si torna alla pagina, con la lista com'era (pagina, ordine, ricerca).
    @PostMapping("/{corsoId}/iscrivi")
    public String iscrivi(@PathVariable Long utenteId, @PathVariable Long corsoId, @ModelAttribute("lista") StatoLista lista,
                          RedirectAttributes flash) {
        try {
            Corso corso = service.iscrivi(utenteId, corsoId);
            flash.addFlashAttribute("messaggio", "Iscritto al corso " + corso.getNome());
        } catch (RegolaViolataException e) {
            flash.addFlashAttribute("errore", e.getMessage());
        }
        return "redirect:" + indirizzo(utenteId) + lista.query(OrdinamentiListe.CORSI_DISPONIBILI);
    }

    // POST /utenti/5/corsi/3/disiscrivi -> toglie l'iscrizione (sempre ammesso, anche con l'abbonamento scaduto).
    @PostMapping("/{corsoId}/disiscrivi")
    public String disiscrivi(@PathVariable Long utenteId, @PathVariable Long corsoId, @ModelAttribute("lista") StatoLista lista,
                             RedirectAttributes flash) {
        try {
            Corso corso = service.disiscrivi(utenteId, corsoId);
            flash.addFlashAttribute("messaggio", "Iscrizione al corso " + corso.getNome() + " tolta");
        } catch (RegolaViolataException e) {
            flash.addFlashAttribute("errore", e.getMessage());
        }
        return "redirect:" + indirizzo(utenteId) + lista.query(OrdinamentiListe.CORSI_DISPONIBILI);
    }

    // L'indirizzo della pagina, costruito dal server con l'id dell'utente preso dal percorso: non si accetta mai un indirizzo di ritorno scritto da
    // chi invia la richiesta (eviterebbe i cosiddetti «open redirect»).
    private String indirizzo(Long utenteId) {
        return "/utenti/" + utenteId + "/corsi";
    }
}
