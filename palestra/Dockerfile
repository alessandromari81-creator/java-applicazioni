# FILE: Dockerfile
# A COSA SERVE: costruisce l'immagine Docker dell'applicazione, così si avvia senza installare Java né Maven.
# Si usa tramite compose.yaml (`docker compose --profile app up --build`), non serve lanciarlo a mano.
#
# DUE FASI ("multi-stage"):
#   1) "build": un'immagine con il JDK e Maven compila il progetto e produce il file .jar;
#   2) immagine finale: solo il JRE (il necessario per eseguire) e il .jar. Il codice sorgente, Maven e le librerie di
#      compilazione NON finiscono nell'immagine finale, che resta più piccola e con meno superficie d'attacco.
# Le immagini di base (eclipse-temurin) esistono per processori Intel/AMD e ARM (Mac con Apple Silicon): la build usa quello del computer.

# ---------- FASE 1: compilazione ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Prima solo i file di configurazione di Maven e si scaricano le librerie. Docker conserva il risultato di ogni passo: finché
# pom.xml non cambia, le librerie non si riscaricano ad ogni modifica del codice (build molto più veloci).
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

# Poi il codice e la compilazione. I test non si eseguono qui (-DskipTests): richiedono tempo e si lanciano con `./mvnw test`.
COPY src src
RUN ./mvnw -q -B package -DskipTests

# ---------- FASE 2: immagine finale ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# L'applicazione gira con un utente normale, non come "root": se qualcuno ne prendesse il controllo avrebbe meno poteri nel container.
RUN useradd --system --no-create-home palestra
COPY --from=build /build/target/*.jar app.jar
USER palestra

# La porta su cui risponde l'app (la pubblica sul computer compose.yaml: "8080:8080").
EXPOSE 8080

# MaxRAMPercentage: la JVM usa al massimo il 75% della memoria assegnata al container (senza, può occuparne di più e farlo fermare).
# Le impostazioni (database, chiave dei token...) arrivano da variabili d'ambiente: vedi il servizio "app" in compose.yaml.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
