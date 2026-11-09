/*
 * FILE: DatiIniziali.java
 * A COSA SERVE: all'avvio inserisce dati di prova (4 corsi e 2 utenti) nel database.
 * Serve perché H2 è in memoria e a ogni avvio parte vuoto.
 * PARAGONE LARAVEL: un DatabaseSeeder lanciato in automatico a ogni avvio.
 */
package it.palestra.config;

import it.palestra.model.Corso;
import it.palestra.model.Livello;
import it.palestra.model.Utente;
import it.palestra.repository.CorsoRepository;
import it.palestra.repository.UtenteRepository;
import java.util.List;
import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatiIniziali {

    // CommandLineRunner = codice eseguito UNA volta subito dopo l'avvio dell'app.
    // I due repository arrivano da Spring come parametri del metodo @Bean.
    @Bean
    CommandLineRunner seed(CorsoRepository corsi, UtenteRepository utenti) {
        return args -> {
            // Se ci sono già corsi non fare nulla (evita di duplicare i dati con DB persistente).
            if (corsi.count() > 0) return;

            // Crea e salva i corsi in un colpo solo. saveAll restituisce gli oggetti salvati
            // (ora con l'id assegnato dal DB), che ci servono per collegarli agli utenti.
            List<Corso> c = corsi.saveAll(List.of(
                    new Corso("Yoga", "Stretching e respirazione", 60, Livello.PRINCIPIANTE),
                    new Corso("Spinning", "Ciclismo indoor ad alta intensità", 45, Livello.INTERMEDIO),
                    new Corso("CrossFit", "Allenamento funzionale", 60, Livello.AVANZATO),
                    new Corso("Pilates", "Tonificazione e postura", 50, Livello.PRINCIPIANTE)));

            // Primo utente, iscritto a Yoga (c.get(0)) e Spinning (c.get(1)).
            Utente u1 = new Utente();
            u1.setNome("Mario"); u1.setCognome("Rossi"); u1.setEmail("mario.rossi@example.com");
            u1.setCorsi(Set.of(c.get(0), c.get(1)));

            // Secondo utente, iscritto solo a Pilates (c.get(3)).
            Utente u2 = new Utente();
            u2.setNome("Laura"); u2.setCognome("Bianchi"); u2.setEmail("laura.bianchi@example.com");
            u2.setCorsi(Set.of(c.get(3)));

            utenti.saveAll(List.of(u1, u2));
        };
    }
}
