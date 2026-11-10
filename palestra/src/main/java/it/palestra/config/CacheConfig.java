/*
 * FILE: CacheConfig.java
 * A COSA SERVE: accende la cache di Spring. Senza @EnableCaching le annotazioni @Cacheable/@CacheEvict sarebbero ignorate.
 * Il motore (Caffeine) e le sue regole (dimensione, scadenza) stanno in application.properties (spring.cache.*).
 * PARAGONE LARAVEL: scegliere il driver in config/cache.php; qui "attivare" la cache è un interruttore a parte.
 */
package it.palestra.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
}
