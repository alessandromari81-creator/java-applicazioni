/*
 * FILE: AuthApiController.java
 * A COSA SERVE: le tre operazioni di accesso alla API, tutte raggiungibili SENZA access token (sono il modo di ottenerlo):
 *   POST /api/auth/login    email + password            -> coppia di token (access + refresh)
 *   POST /api/auth/refresh  refresh_token               -> NUOVA coppia di token (il refresh usato viene invalidato)
 *   POST /api/auth/logout   refresh_token               -> 204; il refresh token non vale più
 * Poi il client invia l'access token in ogni richiesta: "Authorization: Bearer <access_token>".
 * PARAGONE LARAVEL: gli endpoint login/refresh/logout di Sanctum o Passport.
 *
 * FLUSSO TIPICO:
 *   login -> access_token (60 min) + refresh_token (7 giorni)
 *   ... richieste con l'access token ...
 *   access token scaduto (401) -> POST /api/auth/refresh con il refresh_token -> nuova coppia, si riparte
 *   refresh token scaduto o revocato (401 anche sul refresh) -> si rifà il login
 */
package it.palestra.api;

import it.palestra.api.dto.LoginRequest;
import it.palestra.api.dto.RefreshRequest;
import it.palestra.api.dto.TokenResponse;
import it.palestra.service.SessioneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// @RestController = @Controller + @ResponseBody: ciò che i metodi restituiscono diventa JSON (niente template).
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final SessioneService sessioni;

    public AuthApiController(SessioneService sessioni) {
        this.sessioni = sessioni;
    }

    // @RequestBody = "il corpo della richiesta è un JSON: trasformalo in un oggetto LoginRequest". @Valid esegue le
    // regole di validazione (campi obbligatori); se falliscono parte da sola la risposta 400 (GestoreErroriApi).
    // Credenziali sbagliate -> BadCredentialsException -> 401 (messaggio uguale per email inesistente e password errata).
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest richiesta) {
        return TokenResponse.da(sessioni.login(richiesta.email().trim(), richiesta.password()));
    }

    // Rinnovo: il refresh token viene USATO (e invalidato) e si riceve una coppia nuova. Se il token è sconosciuto, scaduto o
    // già usato -> 401. Il motivo preciso lo scrive solo il log del server.
    @PostMapping("/refresh")
    public TokenResponse rinnova(@RequestBody RefreshRequest richiesta) {
        return TokenResponse.da(sessioni.rinnova(tokenDi(richiesta)));
    }

    // Logout: revoca il refresh token. Risponde SEMPRE 204 (anche se il token non esiste o è già revocato), così non si
    // possono scoprire quali token esistono. L'access token già emesso vale fino alla scadenza (60 minuti).
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
