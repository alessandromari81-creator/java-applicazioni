/*
 * FILE: SecurityConfig.java
 * A COSA SERVE: configura la sicurezza: quali pagine sono pubbliche e quali richiedono
 * login, come funziona il login/logout, e chi è l'utente amministratore.
 * PARAGONE LARAVEL: l'insieme di middleware 'auth', config/auth.php e le rotte di login
 * di Breeze/Fortify, tutto in un file.
 *
 * PERCHÉ LA PRIMA PAGINA È IL LOGIN: è deciso QUI. La regola anyRequest().authenticated()
 * dice "ogni pagina richiede il login". Quando un visitatore non loggato chiede una pagina
 * (anche solo "/"), il filtro lo rimanda alla pagina indicata da loginPage("/login").
 * Quella pagina è poi servita da HomeController.login(). Nessun controller "decide" il login:
 * è il filtro di sicurezza che si mette in mezzo PRIMA che la richiesta arrivi al controller.
 */
package it.palestra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

// @Configuration = classe di configurazione: i metodi @Bean creano oggetti che Spring
// registra nel suo container (come i Service Provider di Laravel).
@Configuration
public class SecurityConfig {

    // Definisce le regole di sicurezza HTTP. Spring Security funziona con una catena
    // di filtri, equivalente ai middleware di Laravel.
    @Bean
    SecurityFilterChain filtri(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                // /login e i file CSS sono pubblici (senza questo non si vedrebbe nemmeno il login).
                // /error è la pagina interna a cui Spring Boot passa gli errori (404, 500...):
                // se non fosse pubblica, anche la pagina di errore chiederebbe il login.
                .requestMatchers("/login", "/css/**", "/error").permitAll()
                // TUTTO il resto richiede di essere loggati (come middleware('auth') su tutto il gruppo).
                .anyRequest().authenticated())
            .formLogin(f -> f
                .loginPage("/login")                    // usa la NOSTRA pagina invece di quella di default
                .defaultSuccessUrl("/utenti", true)     // dopo il login vai sempre al pannello
                .permitAll())
            // Dopo il logout torna al login con ?logout (per mostrare il messaggio).
            // Il logout si fa con POST /logout (protetto da CSRF automaticamente).
            .logout(l -> l.logoutSuccessUrl("/login?logout"));
        // Il CSRF è attivo di default: i form Thymeleaf aggiungono da soli il token
        // nascosto (come @csrf in Blade).
        return http.build();
    }

    // Cifratore di password. Quello "delegating" salva la password con un prefisso
    // dell'algoritmo, es. {bcrypt}..., e usa bcrypt come Hash::make() di Laravel.
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    // Dice a Spring Security "chi può fare login". Qui c'è un solo amministratore tenuto
    // in memoria (non nel database), con credenziali lette da application.properties
    // tramite @Value (come env()/config() in Laravel). Solo per sviluppo!
    @Bean
    UserDetailsService admin(PasswordEncoder encoder,
                             @Value("${app.admin.username}") String username,
                             @Value("${app.admin.password}") String password) {
        return new InMemoryUserDetailsManager(
                // La password viene cifrata prima di essere salvata in memoria.
                User.withUsername(username).password(encoder.encode(password)).roles("ADMIN").build());
    }
}
