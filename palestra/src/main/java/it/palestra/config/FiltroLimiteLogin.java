/*
 * FILE: FiltroLimiteLogin.java
 * A COSA SERVE: blocca i tentativi di login del PANNELLO (POST /login) di chi ha già sbagliato troppe volte (vedi LimiteTentativiLogin).
 * Si mette PRIMA del filtro che controlla la password: se l'indirizzo è bloccato la richiesta non arriva nemmeno a quel controllo
 * (quindi anche con la password giusta non si entra finché dura il blocco) e l'utente torna alla pagina di login con l'avviso
 * "troppi tentativi" (/login?bloccato).
 * Le richieste diverse da POST /login passano senza alcun controllo.
 */
package it.palestra.config;

import it.palestra.exception.TroppiTentativiException;
import it.palestra.service.LimiteTentativiLogin;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

public class FiltroLimiteLogin extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(FiltroLimiteLogin.class);

    private final LimiteTentativiLogin limite;

    public FiltroLimiteLogin(LimiteTentativiLogin limite) {
        this.limite = limite;
    }

    // Il filtro agisce solo sull'invio del form di login: POST su /login.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest richiesta) {
        return !("POST".equals(richiesta.getMethod()) && (richiesta.getContextPath() + "/login").equals(richiesta.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest richiesta, HttpServletResponse risposta, FilterChain catena)
            throws ServletException, IOException {
        try {
            limite.verifica(richiesta.getRemoteAddr(), richiesta.getParameter("username"));
        } catch (TroppiTentativiException e) {
            LOG.warn("Login del pannello bloccato per troppi tentativi falliti da {}: riprovare tra {} secondi",
                    richiesta.getRemoteAddr(), e.getSecondiAttesa());
            risposta.sendRedirect(richiesta.getContextPath() + "/login?bloccato");
            return;
        }
        catena.doFilter(richiesta, risposta);
    }
}
