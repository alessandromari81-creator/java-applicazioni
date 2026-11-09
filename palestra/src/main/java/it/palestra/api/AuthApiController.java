/*
 * FILE: AuthApiController.java
 * A COSA SERVE: il login della API: POST /api/auth/login con email e password restituisce un token JWT. Poi il client
 * invia il token in ogni richiesta: "Authorization: Bearer <token>". È l'unico indirizzo della API raggiungibile senza
 * token (vedi SecurityConfig).
 * PARAGONE LARAVEL: l'endpoint di login di Sanctum/Passport che restituisce un token.
 *
 * FLUSSO:  client --(email+password)--> /api/auth/login --(token)--> client --(Bearer token)--> /api/corsi ...
 */
package it.palestra.api;

import it.palestra.api.dto.LoginRequest;
import it.palestra.api.dto.TokenResponse;
import it.palestra.service.AutenticazioneService;
import it.palestra.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController = @Controller + @ResponseBody: ciò che i metodi restituiscono diventa JSON (niente template).
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final AutenticazioneService autenticazione;
    private final JwtService jwt;

    public AuthApiController(AutenticazioneService autenticazione, JwtService jwt) {
        this.autenticazione = autenticazione;
        this.jwt = jwt;
    }

    // @RequestBody = "il corpo della richiesta è un JSON: trasformalo in un oggetto LoginRequest". @Valid esegue le
    // regole di validazione (campi obbligatori); se falliscono parte da sola la risposta 400 (GestoreErroriApi).
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest richiesta) {
        // Se email o password sono sbagliate lancia BadCredentialsException -> 401 (vedi GestoreErroriApi).
        Authentication utente = autenticazione.autentica(richiesta.email().trim(), richiesta.password());

        // Nel token vanno SOLO i ruoli. Un utente autenticato di Spring ha una lista di "autorità", e non tutte sono ruoli:
        // Spring Security 7 aggiunge anche FACTOR_PASSWORD, che registra COME ci si è autenticati (con la password) e
        // non è un ruolo. I ruoli veri hanno il prefisso "ROLE_" (ROLE_ADMIN): si filtra su quello e si toglie, così nel
        // token c'è il nome pulito ("ADMIN").
        var ruoli = utente.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(nome -> nome.startsWith("ROLE_"))
                .map(nome -> nome.substring("ROLE_".length()))
                .sorted()
                .toList();

        JwtService.TokenEmesso token = jwt.emetti(utente.getName(), ruoli);
        return TokenResponse.bearer(token.token(), token.scadeTraSecondi());
    }
}
