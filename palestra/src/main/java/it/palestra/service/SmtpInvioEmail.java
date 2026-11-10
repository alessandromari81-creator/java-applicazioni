/*
 * FILE: SmtpInvioEmail.java
 * A COSA SERVE: spedisce davvero le email tramite un server SMTP (impostato in spring.mail.*: in sviluppo Mailpit su localhost:1025, in produzione il
 * server del proprio provider). L'email contiene sia la versione HTML sia quella di solo testo: il programma di posta mostra la prima, se la supporta.
 * Non è attiva nei test (profilo "test"): lì c'è EmailFinte.
 */
package it.palestra.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class SmtpInvioEmail implements InvioEmail {

    private final JavaMailSender posta;
    private final String mittente;

    public SmtpInvioEmail(JavaMailSender posta, @Value("${app.email.mittente}") String mittente) {
        this.posta = posta;
        this.mittente = mittente;
    }

    @Override
    public void invia(String destinatario, String oggetto, String testoHtml, String testoSemplice) {
        try {
            MimeMessage messaggio = posta.createMimeMessage();
            MimeMessageHelper aiuto = new MimeMessageHelper(messaggio, true, "UTF-8");   // true = messaggio con più parti (testo + HTML)
            aiuto.setFrom(mittente);
            aiuto.setTo(destinatario);
            aiuto.setSubject(oggetto);
            aiuto.setText(testoSemplice, testoHtml);                                       // prima il testo semplice, poi l'HTML
            posta.send(messaggio);
        } catch (MessagingException e) {
            throw new IllegalStateException("Impossibile comporre l'email per " + destinatario, e);
        }
    }
}
