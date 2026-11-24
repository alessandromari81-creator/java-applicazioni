/*
 * FILE: InvioEmail.java
 * A COSA SERVE: il "contratto" per spedire un'email: destinatario, oggetto, testo in HTML e versione di solo testo. Il resto dell'applicazione (NotificheEmail)
 * conosce solo questa interfaccia, non il modo in cui l'email parte. Le implementazioni:
 *   - SmtpInvioEmail la spedisce davvero via SMTP (si chiama "invioReale");
 *   - InvioEmailInCoda non la spedisce: la scrive nella coda (tabella email_in_coda), e SpedizioneEmailProgrammata la spedisce poco dopo con l'implementazione
 *     "invioReale", riprovando se il server di posta non risponde. È quella usata dall'applicazione (app.email.coda.attiva=true);
 *   - nei test una versione finta (EmailFinte) la registra in memoria, così non serve nessun server di posta.
 */
package it.palestra.service;

public interface InvioEmail {

    // Spedisce l'email (o la mette in coda, secondo l'implementazione). Può lanciare un'eccezione se il server di posta non risponde o la coda non si scrive: la gestisce chi chiama
    // (NotificheEmail, che la scrive nel log); con la coda, l'eccezione dell'invio vero la gestisce il lavoratore e riprova.
    void invia(String destinatario, String oggetto, String testoHtml, String testoSemplice);
}
