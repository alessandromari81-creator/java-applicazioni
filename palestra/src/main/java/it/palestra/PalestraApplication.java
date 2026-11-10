/*
 * FILE: PalestraApplication.java
 * A COSA SERVE: è il punto di ingresso dell'intera applicazione. Quando lanci
 * `./mvnw spring-boot:run` (o il .jar) parte da qui.
 * PARAGONE LARAVEL: è l'insieme di `public/index.php` + `bootstrap/app.php`.
 *
 * ATTENZIONE: qui NON è scritto quale sia la prima pagina, e non ci sono rotte (non c'è un
 * routes/web.php). Questo file si limita ad avviare Spring. Le pagine si decidono altrove:
 *  - le rotte stanno sui metodi dei controller (@GetMapping/@PostMapping in web/);
 *  - "chi vede il login" lo decide SecurityConfig (config/).
 * Percorso di una visita a http://localhost:8080/ :
 *  1. Il browser chiede GET /.
 *  2. Spring Security (un filtro che si mette davanti ai controller) controlla: sei loggato? No ->
 *     redirect a /login (regola in SecurityConfig: anyRequest().authenticated() + loginPage("/login")).
 *  3. GET /login arriva a HomeController.login(), che restituisce "login" -> templates/login.html.
 *  4. Dopo il login corretto vai a /utenti (UtenteController.lista() -> templates/utenti/lista.html).
 */

// "package" = il namespace della classe. Deve coincidere con le cartelle: it/palestra/
package it.palestra;

// "import" = porta dentro le classi che usiamo qui sotto.
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication è un'annotazione (un'etichetta che dà alla classe un significato speciale).
// Dice a Spring: "cerca tutte le mie classi (@Controller, @Service, @Configuration...)
// in questo package e nei sotto-package, crea gli oggetti e collegali tra loro"
// (questo si chiama "dependency injection").
@SpringBootApplication
public class PalestraApplication {

	// main = la funzione che la JVM esegue per prima. "static" = si può chiamare senza creare un oggetto.
	public static void main(String[] args) {
		// Avvia Spring: scansiona i package, crea i bean (controller, service, repository...),
		// li collega e avvia il server web (Tomcat) sulla porta 8080.
		SpringApplication.run(PalestraApplication.class, args);
	}

}
