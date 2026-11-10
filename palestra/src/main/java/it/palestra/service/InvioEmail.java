/*
 * FILE: InvioEmail.java
 * A COSA SERVE: il "contratto" per spedire un'email: destinatario, oggetto, testo in HTML e versione di solo testo. Il resto dell'applicazione (NotificheEmail)
 * conosce solo questa interfaccia, non il modo in cui l'email parte: SmtpInvioEmail la spedisce davvero via SMTP; nei test una versione finta
 * (EmailFinte) la registra in memoria, così non serve nessun server di posta.
 */
package it.palestra.service;

public interface InvioEmail {

    // Spedisce l'email. Può lanciare un'eccezione se il server di posta non risponde: la gestisce chi chiama (NotificheEmail).
    void invia(String destinatario, String oggetto, String testoHtml, String testoSemplice);
}
