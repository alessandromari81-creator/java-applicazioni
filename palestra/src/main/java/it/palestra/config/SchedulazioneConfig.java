/*
 * FILE: SchedulazioneConfig.java
 * A COSA SERVE: accende l'esecuzione dei compiti programmati (le annotazioni @Scheduled, vedi PuliziaProgrammata). Senza @EnableScheduling
 * le annotazioni @Scheduled sarebbero ignorate.
 * Si spegne con app.pulizia.attiva=false (lo fa il profilo di test, così durante i test non parte nulla da solo: la pulizia nei test
 * si prova chiamando direttamente il metodo).
 */
package it.palestra.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.pulizia.attiva", havingValue = "true", matchIfMissing = true)
public class SchedulazioneConfig {
}
