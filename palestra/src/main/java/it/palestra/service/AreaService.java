/*
 * FILE: AreaService.java
 * A COSA SERVE: i dati dell'AREA PERSONALE di chi ha fatto login come iscritto o istruttore (pagine /area). Ogni metodo riceve l'EMAIL dell'utente
 * che ha fatto login (presa dalla sessione, mai da un numero scritto nell'indirizzo) e restituisce SOLO i dati di quella persona: per questo
 * nessuno può leggere i dati di un altro cambiando un id nell'URL.
 * L'istruttore vede, per ciascun corso che TIENE, nome e cognome degli iscritti (nient'altro: niente email né abbonamenti). I corsi tenuti
 * si ricavano dalla relazione dell'utente stesso, non da un id passato dal client.
 * I risultati sono record semplici costruiti dentro la transazione (i template non possono più leggere le relazioni lazy dopo).
 */
package it.palestra.service;

import it.palestra.exception.RisorsaNonTrovataException;
import it.palestra.model.Abbonamento;
import it.palestra.model.Corso;
import it.palestra.model.Utente;
import it.palestra.pagina.RichiestaPagina;
import it.palestra.repository.UtenteRepository;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AreaService {

    // Un corso che l'utente frequenta come iscritto.
    public record CorsoFrequentato(String nome, String livello, int durataMinuti, String descrizione, String istruttore) {}

    // Un corso che l'utente tiene come istruttore, con nome e cognome degli iscritti ("Cognome Nome").
    public record CorsoTenuto(String nome, String livello, int durataMinuti, List<String> iscritti) {}

    // Tutto ciò che mostrano la pagina iniziale e il profilo dell'area personale.
    public record Riepilogo(String nome, String cognome, String email, String indirizzo, String cap, String citta,
                            boolean iscritto, boolean istruttore, Abbonamento abbonamento,
                            List<CorsoFrequentato> corsi, List<CorsoTenuto> corsiTenuti,
                            String privacyAccettata, boolean consensoMarketing) {}

    private static final DateTimeFormatter DATA_ORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UtenteRepository utenti;
    private final AbbonamentoService abbonamenti;
    private final Clock clock;

    public AreaService(UtenteRepository utenti, AbbonamentoService abbonamenti, Clock clock) {
        this.utenti = utenti;
        this.abbonamenti = abbonamenti;
        this.clock = clock;
    }

    // I dati dell'utente con questa email: profilo, abbonamento più significativo, corsi frequentati e (se istruttore) corsi tenuti.
    @Transactional(readOnly = true)
    public Riepilogo riepilogo(String email) {
        Utente u = carica(email);
        Abbonamento abbonamento = abbonamenti.riepilogoPerUtente(List.of(u.getId())).get(u.getId());

        List<CorsoFrequentato> frequentati = u.getCorsi().stream()
                .sorted(Comparator.comparing(c -> c.getNome()))
                .map(c -> new CorsoFrequentato(c.getNome(), c.getLivello().name(), c.getDurataMinuti(), c.getDescrizione(),
                        c.getIstruttore() == null ? null : c.getIstruttore().getCognome() + " " + c.getIstruttore().getNome()))
                .toList();

        List<CorsoTenuto> tenuti = u.getCorsiInsegnati().stream()
                .sorted(Comparator.comparing(c -> c.getNome()))
                .map(c -> new CorsoTenuto(c.getNome(), c.getLivello().name(), c.getDurataMinuti(), nomiIscritti(c)))
                .toList();

        String privacy = u.getPrivacyAccettataIl() == null ? null
                : DATA_ORA.format(u.getPrivacyAccettataIl().atZone(clock.getZone()));
        return new Riepilogo(u.getNome(), u.getCognome(), u.getEmail(), u.getIndirizzo(), u.getCap(), u.getCitta(),
                u.isIscritto(), u.isIstruttore(), abbonamento, frequentati, tenuti, privacy,
                u.getConsensoMarketingIl() != null);
    }

    // Una pagina dello storico degli abbonamenti dell'utente con questa email (dal più recente, secondo la richiesta).
    @Transactional(readOnly = true)
    public Page<Abbonamento> storicoAbbonamenti(String email, RichiestaPagina richiesta) {
        return abbonamenti.abbonamentiDi(carica(email).getId(), richiesta);
    }

    // La data di oggi (per calcolare lo stato degli abbonamenti nelle pagine).
    public java.time.LocalDate oggi() {
        return abbonamenti.oggi();
    }

    // Dà o ritira il consenso FACOLTATIVO alle comunicazioni promozionali. Ritirarlo è semplice quanto darlo (richiesto dal GDPR). Se lo stato non
    // cambia non si tocca nulla, così la data del consenso originale resta quella vera.
    @Transactional
    public void impostaConsensoMarketing(String email, boolean consenso) {
        Utente u = utenti.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RisorsaNonTrovataException("Utente non trovato"));
        boolean dato = u.getConsensoMarketingIl() != null;
        if (consenso && !dato) {
            u.impostaConsensoMarketing(clock.instant());
        } else if (!consenso && dato) {
            u.impostaConsensoMarketing(null);
        }
    }

    private Utente carica(String email) {
        return utenti.findWithRelazioniByEmailIgnoreCase(email)
                .orElseThrow(() -> new RisorsaNonTrovataException("Utente non trovato"));
    }

    // "Cognome Nome" degli iscritti al corso, in ordine alfabetico (solo questi due dati).
    private List<String> nomiIscritti(Corso corso) {
        return utenti.findIscrittiDelCorso(corso.getId()).stream()
                .map(n -> n.getCognome() + " " + n.getNome())
                .toList();
    }
}
