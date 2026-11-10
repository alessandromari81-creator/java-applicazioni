/*
 * FILE: AuthApiController.java
 * A COSA SERVE: le tre operazioni di accesso alla API, tutte raggiungibili SENZA access token (sono il modo di ottenerlo):
 *   POST /api/auth/login    email + password            -> coppia di token (access + refresh)
 *   POST /api/auth/refresh  refresh_token               -> NUOVA coppia di token (il refresh usato viene invalidato)
 *   POST /api/auth/logout   refresh_token               -> 204; il refresh token non vale più
 * Poi il client invia l'access token in ogni richiesta: "Authorization: Bearer <access_token>".
 *
 * FLUSSO TIPICO:
 *   login -> access_token (60 min) + refresh_token (7 giorni)
 *   ... richieste con l'access token ...
 *   access token scaduto (401) -> POST /api/auth/refresh con il refresh_token -> nuova coppia, si riparte
 *   refresh token scaduto o revocato (401 anche sul refresh) -> si rifà il login
 */
package it.palestra.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import it.palestra.api.dto.LoginRequest;
import it.palestra.api.dto.RefreshRequest;
import it.palestra.api.dto.TokenResponse;
import it.palestra.service.LimiteTentativiLogin;
import it.palestra.service.SessioneService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.AuthenticationException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// @RestController = @Controller + @ResponseBody: ciò che i metodi restituiscono diventa JSON (niente template).
@RestController
@Tag(name = "Autenticazione", description = "Login, rinnovo e logout (pubblici): restituiscono/revocano access token e refresh token.")
@RequestMapping("/api/auth")
public class AuthApiController {

    private final SessioneService sessioni;
    private final LimiteTentativiLogin limite;

    public AuthApiController(SessioneService sessioni, LimiteTentativiLogin limite) {
        this.sessioni = sessioni;
        this.limite = limite;
    }

    // @RequestBody = "il corpo della richiesta è un JSON: trasformalo in un oggetto LoginRequest". @Valid esegue le
    // regole di validazione (campi obbligatori); se falliscono parte da sola la risposta 400 (GestoreErroriApi).
    // Credenziali sbagliate -> BadCredentialsException -> 401 (messaggio uguale per email inesistente e password errata).
    @Operation(summary = "Login con email e password (dopo troppi tentativi falliti: 429 con Retry-After)")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest richiesta, HttpServletRequest richiestaHttp) {
        String ip = richiestaHttp.getRemoteAddr();
        String email = richiesta.email().trim();
        limite.verifica(ip, email);                               // troppi tentativi falliti? -> 429 senza guardare la password
        try {
            TokenResponse risposta = TokenResponse.da(sessioni.login(email, richiesta.password()));
            limite.registraSuccesso(ip, email);                   // login riuscito: il contatore di questa coppia riparte da zero
            return risposta;
        } catch (AuthenticationException e) {
            limite.registraFallimento(ip, email);                 // credenziali sbagliate: conta come tentativo fallito
            throw e;                                              // e la risposta resta il 401 di sempre
        }
    }

    @Operation(summary = "Rinnova l'accesso con il refresh token")
    @PostMapping("/refresh")
    public TokenResponse rinnova(@RequestBody RefreshRequest richiesta) {
        return TokenResponse.da(sessioni.rinnova(tokenDi(richiesta)));
    }

    // Logout: revoca il refresh token. Risponde SEMPRE 204 (anche se il token non esiste o è già revocato), così non si
    // possono scoprire quali token esistono. L'access token già emesso vale fino alla scadenza (60 minuti).
    @Operation(summary = "Logout (revoca il refresh token)")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody RefreshRequest richiesta) {
        sessioni.logout(tokenDi(richiesta));
    }

    // Controlla che il refresh token sia stato inviato (non null e non vuoto) e lo restituisce. Se manca: 400 con il campo
    // "refresh_token", cioè con il nome che il client ha usato nel JSON.
    private String tokenDi(RefreshRequest richiesta) {
        String token = richiesta.refreshToken();
        if (token == null || token.isBlank()) {
            throw ErroreSuCampoException.su("refresh_token", "Il refresh_token è obbligatorio");
        }
        return token.trim();
    }
}
