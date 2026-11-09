/*
 * FILE: SecurityConfig.java
 * A COSA SERVE: configura la sicurezza: quali pagine sono pubbliche e quali richiedono
 * login, come funziona il login/logout, e chi è l'utente amministratore.
 * PARAGONE LARAVEL: l'insieme di middleware 'auth', config/auth.php e le rotte di login
 * di Breeze/Fortify, tutto in un file.
 *
 * CHI PUÒ FARE LOGIN: gli utenti della tabella "utente" che hanno una password (con l'email come username).
 * Il pannello è riservato a chi ha il ruolo ADMIN. Il primo admin lo crea AdminIniziale all'avvio.
 *
 * PERCHÉ LA PRIMA PAGINA È IL LOGIN: è deciso QUI. La regola anyRequest().hasRole("ADMIN")
 * dice "ogni pagina richiede il login e il ruolo ADMIN". Quando un visitatore non loggato chiede una pagina
 * (anche solo "/"), il filtro lo rimanda alla pagina indicata da loginPage("/login").
 * Quella pagina è poi servita da HomeController.login(). Nessun controller "decide" il login:
 * è il filtro di sicurezza che si mette in mezzo PRIMA che la richiesta arrivi al controller.
 */
package it.palestra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
                // TUTTO il resto richiede di essere loggati E di avere il ruolo ADMIN (come i middleware
                // 'auth' e 'role:admin' di Laravel). hasRole("ADMIN") controlla l'autorità ROLE_ADMIN.
                // Se non sei loggato vai al login; se sei loggato ma senza ruolo ADMIN ricevi un 403.
                .anyRequest().hasRole("ADMIN"))
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

    // Cifratore di password: BCrypt, come Hash::make() di Laravel. Serve a due cose:
    //  - AdminIniziale lo usa per cifrare la password prima di salvarla nel database;
    //  - Spring Security lo usa al login per confrontare la password scritta con l'hash salvato.
    // Chi fa login lo trova UtenteUserDetailsService (service/), che legge dal database.
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
