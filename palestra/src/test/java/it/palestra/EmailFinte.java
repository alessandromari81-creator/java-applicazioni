/*
 * FILE: EmailFinte.java
 * A COSA SERVE: strumento dei test. Prende il posto di SmtpInvioEmail (che nei test, profilo "test", non esiste) e invece di spedire le email le
 * registra in memoria, così i test possono leggerle: a chi sono andate, con quale testo e, soprattutto, quale LINK contengono (da cui estrarre il token
 * per simulare il clic). Si può anche farle "fallire" per provare cosa succede se il server di posta non risponde.
 * Il contenuto si azzera prima di ogni test (BaseWebTest).
 */
package it.palestra;

import it.palestra.service.InvioEmail;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class EmailFinte implements InvioEmail {

    // Un'email "spedita": destinatario, oggetto, versione HTML e versione di solo testo.
    public record Messaggio(String destinatario, String oggetto, String html, String testo) {

        private static final Pattern LINK = Pattern.compile("https?://\\S+");

        // Il primo indirizzo web scritto nel testo (il link da aprire).
        public String link() {
            Matcher m = LINK.matcher(testo);
            if (!m.find()) throw new IllegalStateException("Nessun link nell'email: " + testo);
            return m.group();
        }

        // Il valore del parametro token nel link.
        public String token() {
            String link = link();
            int i = link.indexOf("token=");
            if (i < 0) throw new IllegalStateException("Nessun token nel link: " + link);
            return link.substring(i + "token=".length());
        }
    }

    private final List<Messaggio> inviati = new CopyOnWriteArrayList<>();
    private volatile boolean fallisce;

    @Override
    public void invia(String destinatario, String oggetto, String testoHtml, String testoSemplice) {
        if (fallisce) {
            throw new IllegalStateException("Server di posta non raggiungibile (simulato)");
        }
        inviati.add(new Messaggio(destinatario, oggetto, testoHtml, testoSemplice));
    }

    public List<Messaggio> tutte() { return List.copyOf(inviati); }

    // Le email spedite a un certo indirizzo.
    public List<Messaggio> per(String destinatario) {
        return inviati.stream().filter(m -> m.destinatario().equalsIgnoreCase(destinatario)).toList();
    }

    // L'ultima email spedita (errore se non ne è partita nessuna).
    public Messaggio ultima() {
        if (inviati.isEmpty()) throw new IllegalStateException("Nessuna email spedita");
        return inviati.get(inviati.size() - 1);
    }

    public void svuota() { inviati.clear(); }

    public void faiFallire(boolean fallisci) { this.fallisce = fallisci; }
}
