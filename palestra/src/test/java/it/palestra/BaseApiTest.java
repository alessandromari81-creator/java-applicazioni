/*
 * FILE: BaseApiTest.java
 * A COSA SERVE: classe di base per i test della API REST (/api/**). Estende BaseWebTest (stesso database di test,
 * stessi helper per creare dati, rollback a fine test) e aggiunge gli strumenti per parlare in JSON.
 *
 * COME SI SIMULA UN CLIENTE AUTENTICATO: nella maggior parte dei test non serve fare ogni volta il login e firmare un
 * token vero: jwt() di spring-security-test inserisce direttamente nella richiesta un utente già autenticato come se il
 * token fosse valido (come actingAs() in Laravel). Il flusso VERO (login, token scaduto, manomesso...) lo prova
 * ApiAuthTest. Nei test della API non serve mai .with(csrf()): la API non usa cookie, quindi non c'è CSRF.
 */
package it.palestra;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

abstract class BaseApiTest extends BaseWebTest {

    // Un cliente della API già autenticato con il ruolo ADMIN (come un token valido di un amministratore).
    protected RequestPostProcessor comeAdminApi() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    // Aggiunge a una richiesta l'identità da admin e il corpo JSON (con l'intestazione Content-Type giusta).
    protected MockHttpServletRequestBuilder conJson(MockHttpServletRequestBuilder richiesta, String json) {
        return richiesta.with(comeAdminApi()).contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
