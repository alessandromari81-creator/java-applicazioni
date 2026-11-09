/*
 * FILE: OrologioConfig.java
 * A COSA SERVE: mette a disposizione di tutta l'applicazione l'"orologio" (java.time.Clock). Il codice che ha
 * bisogno della data di oggi lo chiede qui invece di chiamare LocalDate.now() direttamente.
 * PERCHÉ: così nei test si può sostituire con un orologio finto fermo a una data precisa, e provare cosa succede
 * "domani" o "tra un anno" senza aspettare. PARAGONE LARAVEL: Carbon::setTestNow(...).
 */
package it.palestra.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrologioConfig {

    // L'orologio vero: ora e fuso orario del computer su cui gira l'applicazione.
    @Bean
    Clock orologio() {
        return Clock.systemDefaultZone();
    }
}
