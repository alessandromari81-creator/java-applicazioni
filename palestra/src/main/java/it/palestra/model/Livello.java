/*
 * FILE: Livello.java
 * A COSA SERVE: definisce i valori ammessi per il livello di un corso.
 * PARAGONE LARAVEL/PHP: un enum di PHP 8.1 (enum Livello: string {...}).
 * Si trova in "model/" perché fa parte del dominio dell'applicazione (come app/Models).
 */
package it.palestra.model;

// Un enum limita il campo a questi tre valori: non si può scrivere "ESPERTO" per sbaglio.
public enum Livello {
    PRINCIPIANTE, INTERMEDIO, AVANZATO
}
