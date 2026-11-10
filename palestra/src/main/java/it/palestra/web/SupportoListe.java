/*
 * FILE: SupportoListe.java
 * A COSA SERVE: piccolo aiuto condiviso dai controller delle liste HTML per un caso particolare: quando una lista deve
 * RIMANDARE a un'altra pagina (es. la pagina chiesta non esiste più perché si è eliminato l'ultimo elemento) il messaggio di
 * conferma ("Utente eliminato") non deve andare perso. I messaggi "flash" vivono per UNA sola richiesta: se quella richiesta
 * fa subito un altro redirect, vanno rimessi nel nuovo flash.
 */
package it.palestra.web;

import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

final class SupportoListe {

    private SupportoListe() {}

    // Copia i messaggi flash ricevuti (presenti nel Model della richiesta in corso) nel flash del prossimo redirect.
    static void riportaMessaggi(Model model, RedirectAttributes flash) {
        for (String nome : new String[] {"messaggio", "errore"}) {
            Object valore = model.asMap().get(nome);
            if (valore != null) flash.addFlashAttribute(nome, valore);
        }
    }
}
