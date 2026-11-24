/*
 * FILE: NotificheEmail.java
 * A COSA SERVE: scrive e fa partire le email dell'applicazione: conferma della registrazione, link per reimpostare la password, invito
 * dell'amministratore a impostare la password e avviso «hai già un account». Il testo HTML nasce dai modelli in templates/email/ (Thymeleaf); i link si
 * costruiscono con l'indirizzo configurato in app.url-pubblico, MAI con quello della richiesta (l'intestazione Host si può falsificare).
 * Se il server di posta non risponde, l'errore si scrive nel log e NON si propaga: la persona vede comunque la solita risposta, perché le pagine non
 * devono rivelare nulla (nemmeno che l'email non è partita). Ogni metodo restituisce true se l'email è partita.
 * Nel log NON finiscono mai i link né i token.
 */
package it.palestra.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class NotificheEmail {

    private static final Logger LOG = LoggerFactory.getLogger(NotificheEmail.class);

    private final InvioEmail invio;
    private final TemplateEngine motore;
    private final String urlPubblico;

    public NotificheEmail(InvioEmail invio, TemplateEngine motore, @Value("${app.url-pubblico}") String urlPubblico) {
        this.invio = invio;
        this.motore = motore;
        this.urlPubblico = urlPubblico.endsWith("/") ? urlPubblico.substring(0, urlPubblico.length() - 1) : urlPubblico;
    }

    // Email con il link per CONFERMARE la registrazione e scegliere la password.
    public boolean confermaRegistrazione(String email, String nome, String token) {
        String link = urlPubblico + "/registrazione/conferma?token=" + codifica(token);
        return invia(email, "Conferma la tua registrazione", "conferma-registrazione", nome, link,
                "Ciao " + nome + ",\n\nper completare la registrazione apri questo link e scegli la tua password:\n" + link
                        + "\n\nIl link vale 24 ore. Se non sei stato tu, ignora questo messaggio: non succederà nulla.\n");
    }

    // Email con il link per REIMPOSTARE la password dimenticata.
    public boolean reimpostaPassword(String email, String nome, String token) {
        String link = urlPubblico + "/password/imposta?token=" + codifica(token);
        return invia(email, "Reimposta la tua password", "reimposta-password", nome, link,
                "Ciao " + nome + ",\n\nper scegliere una nuova password apri questo link:\n" + link
                        + "\n\nIl link vale poco tempo e si può usare una sola volta. Se non l'hai richiesto tu, ignora questo messaggio: la tua password non cambia.\n");
    }

    // Email di INVITO dell'amministratore: il profilo è stato creato, manca la password.
    public boolean invitoAttivazione(String email, String nome, String token) {
        String link = urlPubblico + "/password/imposta?token=" + codifica(token);
        return invia(email, "Attiva il tuo account", "invito-attivazione", nome, link,
                "Ciao " + nome + ",\n\nla palestra ha creato il tuo profilo. Per attivare l'accesso scegli la tua password da questo link:\n" + link
                        + "\n\nIl link vale 24 ore e si può usare una sola volta.\n");
    }

    // Avviso a chi si è registrato con un indirizzo che ha già un account: niente conferma, solo il modo per recuperare l'accesso.
    public boolean accountGiaEsistente(String email, String nome) {
        String link = urlPubblico + "/password-dimenticata";
        return invia(email, "Hai già un account", "account-esistente", nome, link,
                "Ciao " + nome + ",\n\nqualcuno ha provato a registrarsi con questo indirizzo, ma esiste già un account. Se sei stato tu e non ricordi la password, puoi reimpostarla qui:\n"
                        + link + "\n\nSe non sei stato tu, ignora questo messaggio.\n");
    }

    // Email di CONFERMA ISCRIZIONE a un corso: parte dopo che l'iscrizione è stata salvata (l'admin ha iscritto la persona). Non contiene token: solo il nome
    // del corso e il link all'area personale, dove si vedono i corsi.
    public boolean iscrizioneCorso(String email, String nome, String nomeCorso) {
        String link = urlPubblico + "/area/corsi";
        return invia(email, "Sei iscritto al corso " + nomeCorso, "iscrizione-corso", nome, link,
                "Ciao " + nome + ",\n\nsei stato iscritto al corso " + nomeCorso + ". Puoi vedere i tuoi corsi qui:\n" + link + "\n", nomeCorso);
    }

    // Compone l'email dal modello HTML e la fa partire; gli errori di invio si scrivono nel log (senza link né token).
    private boolean invia(String destinatario, String oggetto, String modello, String nome, String link, String testo) {
        return invia(destinatario, oggetto, modello, nome, link, testo, null);
    }

    // Come sopra, con in più il nome del corso per i modelli che lo mostrano (null per gli altri).
    private boolean invia(String destinatario, String oggetto, String modello, String nome, String link, String testo, String corso) {
        try {
            Context contesto = new Context();
            contesto.setVariable("nome", nome);
            contesto.setVariable("link", link);
            contesto.setVariable("corso", corso);
            String html = motore.process("email/" + modello, contesto);
            invio.invia(destinatario, oggetto, html, testo);
            return true;
        } catch (RuntimeException e) {
            LOG.warn("Invio dell'email '{}' non riuscito ({}): {}", modello, e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    // Il token è già adatto agli indirizzi web (Base64 URL-safe), ma si codifica comunque per sicurezza.
    private String codifica(String token) {
        return URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
