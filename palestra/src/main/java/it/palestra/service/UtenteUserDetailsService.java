/*
 * FILE: UtenteUserDetailsService.java
 * A COSA SERVE: dice a Spring Security COME TROVARE chi fa login. Spring riceve l'email dal form, chiama
 * loadUserByUsername() qui, e confronta da solo la password scritta con l'hash restituito.
 * PARAGONE LARAVEL: l'"user provider" di config/auth.php (provider eloquent, model User).
 */
package it.palestra.service;

import it.palestra.model.Ruolo;
import it.palestra.model.Utente;
import it.palestra.repository.UtenteRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Basta che esista un bean UserDetailsService: Spring Security lo trova e lo usa da solo per il login.
@Service
public class UtenteUserDetailsService implements UserDetailsService {

    private final UtenteRepository utenti;

    public UtenteUserDetailsService(UtenteRepository utenti) {
        this.utenti = utenti;
    }

    // @Transactional: i ruoli sono caricati "pigramente" dal database; serve una transazione aperta mentre
    // li leggiamo qui sotto (poi la transazione si chiude e l'oggetto non è più collegato al DB).
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Utente u = utenti.findWithRuoliByEmailIgnoreCase(email)
                // Senza password (iscritti, istruttori) non si può fare login: per Spring è "non trovato".
                // Stesso errore per email sbagliata, password sbagliata o utente senza password:
                // così non si scopre quali email esistono.
                .filter(x -> x.getPasswordHash() != null)
                .orElseThrow(() -> new UsernameNotFoundException("Utente non trovato"));

        // I nomi dei ruoli diventano le "autorità" di Spring Security: roles("ADMIN") produce ROLE_ADMIN,
        // quella che SecurityConfig controlla con hasRole("ADMIN").
        String[] ruoli = u.getRuoli().stream().map(Ruolo::getNome).toArray(String[]::new);
        return User.withUsername(u.getEmail())
                .password(u.getPasswordHash())   // l'hash letto dal database
                .roles(ruoli)
                .build();
    }
}
