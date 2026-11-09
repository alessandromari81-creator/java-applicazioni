/*
 * FILE: PalestraApplicationTests.java
 * A COSA SERVE: test generato da Spring Initializr. Verifica solo che l'applicazione
 * riesca ad avviarsi (che tutti i bean si colleghino senza errori).
 * PARAGONE LARAVEL: un test in tests/Feature che fa solo `$this->get('/')`.
 * Si esegue con `./mvnw test`. (I test veri li scriveremo nella Fase 6.)
 */
package it.palestra;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Avvia l'intero contesto Spring per il test (lento ma realistico).
@SpringBootTest
class PalestraApplicationTests {

	// @Test = questo metodo è un test (come i metodi test* di PHPUnit).
	@Test
	void contextLoads() {
		// Vuoto di proposito: se il contesto non parte, il test fallisce prima di arrivare qui.
	}

}
