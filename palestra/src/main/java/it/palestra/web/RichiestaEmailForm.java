/*
 * FILE: RichiestaEmailForm.java
 * A COSA SERVE: i dati del modulo «Password dimenticata»: la sola email. Regole di validazione sul formato (la verifica che l'indirizzo esista
 * NON si fa e non si mostra mai: la risposta è uguale in ogni caso).
 */
package it.palestra.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RichiestaEmailForm {

    @NotBlank(message = "L'email è obbligatoria")
    @Email(message = "Email non valida")
    @Size(max = 255, message = "Massimo 255 caratteri")
    private String email;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
