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
 * DUE CATENE DI SICUREZZA (DUE "MIDDLEWARE STACK"): l'applicazione ha due modi di far entrare le persone, con regole
 * diverse, e Spring sceglie la catena in base all'indirizzo della richiesta:
 *   1) /api/**  -> la API REST: senza sessione e senza CSRF, si entra con un TOKEN JWT (intestazione
 *                  "Authorization: Bearer ..."). Chi non è autorizzato riceve 401/403 in JSON, mai un redirect al login.
 *   2) tutto il resto -> le PAGINE: form di login con sessione (cookie) e CSRF, redirect al login.
 * @Order stabilisce l'ordine in cui le catene vengono provate: la prima il cui securityMatcher corrisponde vince.
 *
 * PERCHÉ LA PRIMA PAGINA È IL LOGIN: è deciso QUI. La regola anyRequest().hasRole("ADMIN")
 * dice "ogni pagina richiede il login e il ruolo ADMIN". Quando un visitatore non loggato chiede una pagina
 * (anche solo "/"), il filtro lo rimanda alla pagina indicata da loginPage("/login").
 * Quella pagina è poi servita da HomeController.login(). Nessun controller "decide" il login:
 * è il filtro di sicurezza che si mette in mezzo PRIMA che la richiesta arrivi al controller.
 */
package it.palestra.config;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import it.palestra.service.JwtService;
import org.springframework.security.web.SecurityFilterChain;

// @Configuration = classe di configurazione: i metodi @Bean creano oggetti che Spring
// registra nel suo container (come i Service Provider di Laravel).
@Configuration
public class SecurityConfig {

    // Il registro (log) dell'applicazione: ciò che scrive compare nel terminale in cui gira l'app, mai nelle risposte.
    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

    // CATENA 1: la API REST (/api/**). Provata per prima (@Order(1)).
    @Bean
    @Order(1)
    SecurityFilterChain filtriApi(HttpSecurity http) throws Exception {
        http
            // Questa catena si occupa SOLO degli indirizzi che iniziano con /api/.
            .securityMatcher("/api/**")
            // Niente CSRF: quella protezione serve quando il browser invia da solo i cookie di sessione. Qui non ci
            // sono cookie: il token va aggiunto a mano a ogni richiesta, quindi un altro sito non può "usare" il tuo.
            .csrf(csrf -> csrf.disable())
            // STATELESS: il server non crea né usa sessioni. Ogni richiesta porta con sé tutto ciò che serve (il token).
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                // Login, rinnovo e logout sono pubblici: sono il modo di OTTENERE (o chiudere) l'accesso, quindi non possono
                // richiedere un access token (che può essere scaduto: è proprio per questo che si fa il rinnovo).
                // Si autenticano con le credenziali o con il refresh token che il client invia nel corpo.
                .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                // Tutto il resto della API richiede un token valido con il ruolo ADMIN.
                .anyRequest().hasRole("ADMIN"))
            // "Resource server": la API verifica i token JWT in arrivo. Il decodificatore (JwtConfig) controlla firma e
            // scadenza; il convertitore trasforma il claim "ruoli" del token nelle autorità ROLE_... di Spring.
            // Gli errori si rispondono in JSON (vedi sotto), non con la pagina di login.
            .oauth2ResourceServer(o -> o
                .jwt(j -> j.jwtAuthenticationConverter(convertitoreJwt()))
                .authenticationEntryPoint(rispostaNonAutenticato())
                .accessDeniedHandler(rispostaAccessoNegato()));
        return http.build();
    }

    // Trasforma il contenuto del token in un utente di Spring Security: i nomi nel claim "ruoli" (es. ["ADMIN"])
    // diventano le autorità "ROLE_ADMIN", che hasRole("ADMIN") sa controllare.
    private JwtAuthenticationConverter convertitoreJwt() {
        JwtGrantedAuthoritiesConverter ruoli = new JwtGrantedAuthoritiesConverter();
        ruoli.setAuthoritiesClaimName(JwtService.CLAIM_RUOLI);
        ruoli.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter convertitore = new JwtAuthenticationConverter();
        convertitore.setJwtGrantedAuthoritiesConverter(ruoli);
        return convertitore;
    }

    // 401 Unauthorized: token mancante, scaduto, modificato o firmato male. Il testo è fisso e generico di proposito:
    // dire PERCHÉ il token è stato rifiutato aiuterebbe solo chi attacca. L'intestazione WWW-Authenticate è quella
    // che lo standard richiede in un 401 (dice quale schema di autenticazione usare).
    //
    // IL MOTIVO VERO PERÒ SI SCRIVE NEL LOG DEL SERVER: chi sviluppa deve poter capire perché una richiesta è stata
    // rifiutata (token scaduto? firma sbagliata? intestazione assente?) senza che il client lo venga a sapere. Si
    // indica anche se l'intestazione Authorization c'era: è la causa più comune ("il token non viene inviato").
    // Il token in sé NON si scrive mai nel log, solo il motivo fornito da Spring (es. "Jwt expired at ...").
    private AuthenticationEntryPoint rispostaNonAutenticato() {
        return (richiesta, risposta, eccezione) -> {
            boolean conIntestazione = richiesta.getHeader(HttpHeaders.AUTHORIZATION) != null;
            LOG.warn("API {} {} rifiutata con 401 ({}): {}", richiesta.getMethod(), richiesta.getRequestURI(),
                    conIntestazione ? "intestazione Authorization presente" : "intestazione Authorization ASSENTE",
                    eccezione.getMessage());
            risposta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            scriviJson(risposta, HttpStatus.UNAUTHORIZED, "Non autenticato",
                    "Token mancante, scaduto o non valido: rinnovalo con /api/auth/refresh o rifai il login su /api/auth/login");
        };
    }

    // 403 Forbidden: il token è valido ma l'utente non ha il ruolo richiesto (ADMIN).
    private AccessDeniedHandler rispostaAccessoNegato() {
        return (richiesta, risposta, eccezione) -> {
            LOG.warn("API {} {} negata con 403: il token è valido ma l'utente non ha il ruolo richiesto",
                    richiesta.getMethod(), richiesta.getRequestURI());
            scriviJson(risposta, HttpStatus.FORBIDDEN, "Accesso negato", "Non hai i permessi per questa risorsa");
        };
    }

    // Scrive una risposta JSON {status, errore, messaggio}. Il testo è composto solo da costanti nostre (mai da dati
    // della richiesta), quindi si può scrivere come stringa senza rischi di "iniezione".
    private void scriviJson(HttpServletResponse risposta, HttpStatus stato, String errore, String messaggio)
            throws IOException {
        risposta.setStatus(stato.value());
        risposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        risposta.setCharacterEncoding("UTF-8");
        risposta.getWriter().write("{\"status\":" + stato.value() + ",\"errore\":\"" + errore
                + "\",\"messaggio\":\"" + messaggio + "\"}");
    }

    // CATENA 2: le pagine (tutto il resto). Provata dopo la API (@Order(2)).
    // Definisce le regole di sicurezza HTTP. Spring Security funziona con una catena
    // di filtri, equivalente ai middleware di Laravel.
    @Bean
    @Order(2)
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
