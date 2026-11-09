/*
 * FILE: AdminIniziale.java
 * A COSA SERVE: all'avvio, se nel database NON c'è nessun utente con ruolo ADMIN, ne crea uno con le
 * credenziali di application.properties (app.admin.email / app.admin.password). Serve per poter fare il
 * primo login: senza admin nessuno potrebbe entrare nel pannello. Se un admin esiste già non fa niente:
 * cambiare le proprietà DOPO non modifica quello già salvato.
 * PARAGONE LARAVEL: un seeder che crea il primo utente amministratore con Hash::make().
 */
package it.palestra.config;

import it.palestra.model.Ruolo;
import it.palestra.model.Utente;
import it.palestra.repository.RuoloRepository;
import it.palestra.repository.UtenteRepository;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminIniziale {

    // CommandLineRunner = codice eseguito UNA volta all'avvio dell'app. I parametri e i valori @Value
    // arrivano da Spring.
    @Bean
    CommandLineRunner creaPrimoAdmin(UtenteRepository utenti,
                                     RuoloRepository ruoli,
                                     PasswordEncoder encoder,
                                     @Value("${app.admin.email}") String email,
                                     @Value("${app.admin.password}") String password) {
        return args -> {
            if (utenti.countByRuoliNome(Ruolo.ADMIN) > 0) return;   // un admin c'è già: niente da fare

            // Il ruolo ADMIN esiste già (lo crea la migration V4). Se mancasse (es. nei test, dove il database
            // lo crea Hibernate e non Flyway) lo creiamo qui.
            Ruolo admin = ruoli.findByNome(Ruolo.ADMIN).orElseGet(() -> ruoli.save(new Ruolo(Ruolo.ADMIN)));

            Utente u = new Utente();
            u.setNome("Admin");
            u.setCognome("Palestra");
            u.setEmail(email);
            u.setPasswordHash(encoder.encode(password));   // si salva solo l'hash bcrypt
            u.setRuoli(Set.of(admin));
            utenti.save(u);
        };
    }
}
