/*
 * FILE: LimiteTentativiLogin.java
 * A COSA SERVE: il limite ai login FALLITI (vedi LimiteTentativi per il funzionamento): vale per il login della API (POST /api/auth/login) e per il form
 * del pannello (POST /login), con gli stessi contatori. Soglie da application.properties: app.sicurezza.login-max-tentativi (per coppia IP+email,
 * predefinito 5), app.sicurezza.login-max-tentativi-per-ip (20) e app.sicurezza.login-finestra-minuti (15).
 */
package it.palestra.service;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LimiteTentativiLogin extends LimiteTentativi {

    public LimiteTentativiLogin(Clock clock,
                                @Value("${app.sicurezza.login-max-tentativi:5}") int maxPerCoppia,
                                @Value("${app.sicurezza.login-max-tentativi-per-ip:20}") int maxPerIp,
                                @Value("${app.sicurezza.login-finestra-minuti:15}") long finestraMinuti) {
        super(clock, maxPerCoppia, maxPerIp, finestraMinuti);
    }
}
