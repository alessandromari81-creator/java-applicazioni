/*
 * FILE: AutenticazioneService.java
 * A COSA SERVE: verifica email e password di chi chiede un token alla API. Usa lo stesso meccanismo del login delle
 * pagine: cerca l'utente con UtenteUserDetailsService (nel database) e confronta la password con l'hash bcrypt.
 * Così gli utenti e le regole sono UNICI: chi può entrare dal form può ottenere un token, e chi non ha una password
 * non può nessuna delle due cose.
 */
package it.palestra.service;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AutenticazioneService {

    // DaoAuthenticationProvider è il componente standard di Spring Security che fa il confronto con la password
    // salvata. Qui lo usiamo a mano (il login a pagine lo usa dietro le quinte).
    private final DaoAuthenticationProvider provider;

    public AutenticazioneService(UserDetailsService utenti, PasswordEncoder encoder) {
        this.provider = new DaoAuthenticationProvider(utenti);
        this.provider.setPasswordEncoder(encoder);
    }

    // Controlla le credenziali. Se sono giuste restituisce l'utente autenticato (con i suoi ruoli); altrimenti lancia
    // BadCredentialsException. L'errore è IDENTICO per email inesistente, password sbagliata e utente senza password:
    // così chi attacca non può scoprire quali email esistono.
    public Authentication autentica(String email, String password) {
        return provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
    }
}
