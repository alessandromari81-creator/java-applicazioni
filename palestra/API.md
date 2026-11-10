# API REST della Palestra

La stessa applicazione che mostra le pagine HTML espone anche una API in **JSON**, sotto `/api`. Usa gli stessi
dati e le stesse regole delle pagine (stessi *service*). Avvio: vedi `CLAUDE.md` (`docker compose up -d` e
`./mvnw spring-boot:run`), indirizzo base `http://localhost:8080`.

## 1. Autenticazione (JWT + refresh token)

La risposta di login segue lo **standard OAuth2** (lo stesso formato di Keycloak):
```json
{ "access_token": "eyJ...", "token_type": "Bearer", "expires_in": 3600,
  "refresh_token": "fFUZ...", "refresh_expires_in": 604800 }
```
| Campo | Significato |
|---|---|
| `access_token` | il JWT da mandare in ogni richiesta; dura **60 minuti** (`expires_in` è in secondi; la data assoluta è nel JWT, campo `exp`) |
| `token_type` | sempre `Bearer` |
| `refresh_token` | serve a ottenere un nuovo `access_token` senza rifare il login; dura **7 giorni** (`refresh_expires_in`) |

1. **Login**: `POST /api/auth/login` con email e password di un utente che ha una password.
   ```bash
   curl -s -X POST http://localhost:8080/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"admin@palestra.local","password":"LA-TUA-PASSWORD"}'
   ```
2. **Usa l'access token** in ogni richiesta, con l'intestazione `Authorization: Bearer <access_token>`:
   ```bash
   curl -s http://localhost:8080/api/corsi -H "Authorization: Bearer $ACCESS"
   ```
   Serve il ruolo **ADMIN** (un iscritto con password ottiene i token ma riceve 403).
3. **Quando l'access token scade** (le richieste danno 401) si rinnova senza rifare il login:
   ```bash
   curl -s -X POST http://localhost:8080/api/auth/refresh \
     -H 'Content-Type: application/json' -d '{"refresh_token":"fFUZ..."}'
   ```
   Si riceve una **nuova coppia** di token. Il refresh token usato **non vale più** (*rotazione*): si usa sempre l'ultimo ricevuto.
4. **Logout**: `POST /api/auth/logout` con `{"refresh_token":"..."}` revoca il refresh token (risponde sempre 204) e anche
   **tutti gli access token già emessi** per quell'utente: da quel momento danno 401 (non si aspetta più la scadenza). Gli altri
   dispositivi dello stesso utente ricevono un 401 e si rinnovano da soli con il proprio refresh token. Se il refresh token è
   già usato o inesistente il logout risponde 204 ma non revoca niente.

Se anche il rinnovo dà 401 (refresh token scaduto, già usato o revocato) si rifà il login.

**Attenzione al riuso:** presentare due volte lo stesso refresh token è trattato come un possibile **furto** (il ladro e il
proprietario ne hanno una copia ciascuno): vengono revocati **tutti** i refresh token e **tutti** gli access token di quell'utente e dovrà rifare il login.
Per questo, con il refresh, bisogna sostituire sempre il vecchio token con quello nuovo (Postman lo fa da solo).

### In Postman: collection ed Environment già pronti
1. Postman → **Import** → importa **due file** (puoi trascinarli insieme nella finestra):
   `palestra/postman/palestra.postman_collection.json` e `palestra/postman/palestra.postman_environment.json`.
2. In alto a destra **seleziona l'Environment "Palestra locale"** (il menu a tendina che dice "No Environment").
3. Apri *Environments* → **Palestra locale** e nella riga `password` scrivi la password dell'admin nella colonna
   **Current value** (è quella che hai impostato tu, non `admin`), poi **Save**. `base` ed `email` sono già a posto.
4. Esegui **Auth → Login**. Nella scheda **Test Results** della risposta deve comparire **"Login riuscito (200)"** in verde.
   Il token viene salvato **da solo** nella variabile **`jwt_token`** dell'Environment (come `pm.environment.set` nei tuoi
   altri progetti): lo vedi in *Environments → Palestra locale → jwt_token*. Non devi copiare niente.
5. Da qui in poi tutte le altre richieste usano `{{jwt_token}}` (Bearer Token impostato su ogni richiesta). Le richieste
   "Crea ..." salvano l'id creato, che dettaglio/modifica/elimina riusano. Il Login salva anche `refresh_token`.
6. **Quando ricevi 401** perché l'access token è scaduto (60 minuti) esegui **Auth → Refresh token**: usa il `refresh_token`
   salvato, ottiene una nuova coppia e aggiorna `jwt_token` e `refresh_token` da solo (controlla *Test Results*: "Rinnovo riuscito").
   Se anche il rinnovo dà 401, rifai **Auth → Login**. **Auth → Logout** revoca il refresh token e lo svuota.

Se non selezioni nessun Environment funziona lo stesso: il Login salva il token anche tra le variabili della collection
(in quel caso `password` va scritta nelle *Variables* della collection). Con un Environment selezionato, i suoi valori
hanno la precedenza su quelli della collection.

**Se il Login non dà 200** (Test Results rosso) il token resta vuoto e TUTTE le altre richieste partono senza intestazione
`Authorization`: nel log dell'app vedrai `intestazione Authorization ASSENTE`. Quasi sempre è la `password` (vuota o sbagliata,
o scritta solo in *Initial value*), oppure l'Environment non è selezionato.

### Aggiornare la collection quando cambia (senza cancellare)
La collection e l'Environment hanno un **identificativo fisso**. Quando il file cambia (nuove richieste o script):
1. **Import** → scegli di nuovo `palestra.postman_collection.json`.
2. Postman, riconoscendo la stessa collection, propone di **sostituirla** (*Replace*) invece di crearne una nuova:
   scegli **Replace**. Non cancellare niente a mano.
3. L'**Environment non va reimportato**: contiene la tua `password`. Reimportarlo la azzererebbe. Se una nuova versione
   aggiunge una variabile (come è successo con `refresh_token`), aggiungila a mano in *Environments → Palestra locale*.
La sostituzione riporta la collection a come è nel file: eventuali modifiche fatte a mano dentro Postman si perdono.

### In Bruno / Postman (a mano)
- Crea una richiesta `POST {{base}}/api/auth/login` con corpo JSON; nello script post-risposta salva `access_token` e `refresh_token`
  in variabili d'ambiente (come fai per Keycloak con `pm.environment.set`).
- Nelle altre richieste: scheda **Auth** → tipo **Bearer Token** → `{{jwt_token}}` (e `base` = `http://localhost:8080`).
- Per i corpi JSON ricorda l'intestazione `Content-Type: application/json` (di solito la imposta da solo).

## 2. Endpoint

| Metodo | Indirizzo | Risposta | Note |
|---|---|---|---|
| POST | `/api/auth/login` | 200 / 400 / 401 | pubblico; restituisce `access_token` e `refresh_token` |
| POST | `/api/auth/refresh` | 200 / 400 / 401 | pubblico; `{"refresh_token":...}` -> nuova coppia (il vecchio refresh token non vale più) |
| POST | `/api/auth/logout` | 204 / 400 | pubblico; `{"refresh_token":...}` -> lo revoca (204 anche se non esiste) |
| GET | `/api/corsi` | 200 / 400 | elenco **paginato** (vedi sotto); ricerca `q` sul nome |
| GET | `/api/corsi/{id}` | 200 / 404 | |
| POST | `/api/corsi` | 201 / 400 | `Location` con l'indirizzo del nuovo corso |
| PUT | `/api/corsi/{id}` | 200 / 400 / 404 | **sostituisce** tutti i campi |
| DELETE | `/api/corsi/{id}` | 204 / 404 / 409 | 409 se ha iscritti |
| GET | `/api/utenti` | 200 / 400 | **paginato**; ruoli, corsi frequentati e corsi tenuti; ricerca `q` su nome, cognome, email |
| GET | `/api/utenti/{id}` | 200 / 404 | |
| POST | `/api/utenti` | 201 / 400 | |
| PUT | `/api/utenti/{id}` | 200 / 400 / 404 / 409 | **sostituisce** ruoli e corsi; password omessa = invariata |
| DELETE | `/api/utenti/{id}` | 204 / 404 / 409 | 409: ultimo admin, istruttore di corsi, ha abbonamenti |
| GET | `/api/abbonamenti` | 200 / 400 | **paginato**; ricerca `q` sull'iscritto (nome, cognome, email) |
| GET | `/api/abbonamenti/{id}` | 200 / 404 | |
| DELETE | `/api/abbonamenti/{id}` | 204 / 404 / 409 | solo quelli non ancora iniziati |
| GET | `/api/utenti/{id}/abbonamenti` | 200 / 400 / 404 | storico **paginato** dell'utente, dal più recente (senza `q`) |
| GET | `/api/utenti/{id}/abbonamenti/primo-giorno-libero` | 200 | `{"data":"aaaa-mm-gg"}` per un rinnovo |
| POST | `/api/utenti/{id}/abbonamenti` | 201 / 400 / 404 | |

### Liste paginate (corsi, utenti, abbonamenti, storico)

Le liste non restituiscono più un array ma una **pagina**. Parametri (tutti facoltativi):

| Parametro | Significato | Default / limiti |
|---|---|---|
| `page` | numero di pagina, parte da **0** | 0; negativo = 400 |
| `size` | elementi per pagina | 20; da 1 a **100**, altrimenti 400 (non viene corretto in silenzio) |
| `sort` | `campo` oppure `campo,asc` / `campo,desc` | ordinamento di partenza della lista; solo i campi ammessi (l'errore 400 li elenca) |
| `q` | testo da cercare (parziale, senza maiuscole, max 100 caratteri) | nessuna ricerca; `%` e `_` valgono come caratteri normali |

Campi di `sort`: utenti `cognome` (default), `nome`, `email` · corsi `nome` (default), `id`, `livello`, `durataMinuti` · abbonamenti `dataFine,desc` (default), `dataInizio`, `prezzo`, `tipo`, `iscritto` · storico `dataInizio,desc` (default), `dataFine`, `prezzo`, `tipo`.
A parità di valore si ordina sempre anche per `id`: scorrendo le pagine nessuna riga si ripete o sparisce.

```json
{ "content": [ ... ], "number": 0, "size": 20, "totalElements": 46, "totalPages": 3,
  "first": true, "last": false, "sort": "cognome,asc", "q": "ross" }
```

`q` compare solo se hai cercato. Una pagina oltre l'ultima risponde **200 con `content` vuoto** (e i totali giusti). Esempio:
`GET /api/utenti?q=ross&size=10&page=1&sort=email,desc`. Parametri sbagliati: 400 con `campi` (es. `{"campi":{"size":["Deve essere compresa tra 1 e 100"]}}`).

## 2b. Swagger / OpenAPI (documentazione interattiva)

La API si documenta da sola (libreria springdoc, legge i controller e i DTO): non c'è un file da aggiornare a mano.

| Cosa | Indirizzo | Note |
|---|---|---|
| Pagina interattiva (Swagger UI) | `http://localhost:8080/swagger-ui.html` | provi gli endpoint dal browser |
| Descrizione OpenAPI (JSON) | `http://localhost:8080/v3/api-docs` | importabile in Postman / Bruno / generatori di client |

- **Accesso riservato agli ADMIN**: non sono pubbliche. Si aprono dal browser dopo aver fatto **login nel pannello** (stessa sessione delle pagine); senza login si finisce sulla pagina di login.
- **Provare gli endpoint**: in Swagger UI fai `POST /api/auth/login` (Try it out), copia l'`access_token`, premi **Authorize** in alto e incollalo (solo il token, senza «Bearer»). Il token resta ricordato nel browser. Scade in 60 minuti: rifai il login o usa `/api/auth/refresh`.
- Ogni operazione elenca le risposte possibili (200/201, 400, 401, 403, 404, 409) con la forma `ErroreApi`; le liste descrivono `page`, `size`, `sort` (con i campi ammessi) e `q`.
- **Importare in Postman**: Import → Link (o File) → `http://localhost:8080/v3/api-docs`. Serve per esplorare; la collection già pronta in `postman/` (con login e salvataggio dei token) resta la più comoda per lavorare.
- **Spegnerle** (es. in produzione): variabile d'ambiente `SPRINGDOC_ENABLED=false`.

## 3. Esempi di corpo (JSON)

**Corso** (`POST`/`PUT /api/corsi`):
```json
{ "nome": "Zumba", "descrizione": "Danza", "durataMinuti": 55, "livello": "INTERMEDIO", "istruttoreId": 3 }
```
`livello`: `PRINCIPIANTE`, `INTERMEDIO`, `AVANZATO`. `istruttoreId` facoltativo (un utente con ruolo `ISTRUTTORE`).

**Utente** (`POST`/`PUT /api/utenti`):
```json
{ "nome": "Mario", "cognome": "Rossi", "email": "mario@example.com",
  "password": "obbligatoria-solo-per-ADMIN", "indirizzo": "Via Roma 1", "cap": "00100", "citta": "Roma",
  "ruoli": ["ISCRITTO"], "corsiIds": [1, 2] }
```
Ruoli per **nome** (`ADMIN`, `ISTRUTTORE`, `ISCRITTO`). `corsiIds`: per **aggiungere** un corso a un `ISCRITTO` serve un abbonamento attivo oggi, altrimenti `400` con l'errore su
`corsiIds` (i corsi già assegnati restano e si possono sempre togliere; un iscritto appena creato non ha ancora abbonamenti, quindi si crea con `corsiIds` vuoto). `indirizzo`, `cap` e `citta` (indirizzo di casa) sono facoltativi; `PUT` sostituisce tutti i campi,
quindi un `PUT` senza indirizzo lo svuota. La risposta non contiene mai la password: c'è solo `haPassword`. I consensi privacy non si modificano dalla API.

**Abbonamento** (`POST /api/utenti/{id}/abbonamenti`):
```json
{ "tipo": "TRIMESTRALE", "dataInizio": "2026-12-09" }
```
`tipo`: `MENSILE`, `TRIMESTRALE`, `ANNUALE`. Data di fine e prezzo li calcola il server; la risposta ha anche `stato`
(`FUTURO`, `ATTIVO`, `SCADUTO`).

## 4. Errori

Sempre JSON, con questa forma:
```json
{ "status": 400, "errore": "Dati non validi", "messaggio": "Alcuni campi non sono validi",
  "campi": { "nome": ["Il nome è obbligatorio"] } }
```

| Codice | Quando |
|---|---|
| 400 | dati sbagliati: JSON rotto, campi obbligatori, regole sui campi (email già usata, password, data nel passato, sovrapposizione...). Se riguarda dei campi c'è `campi` |
| 401 | access token mancante, scaduto, modificato o **revocato**; credenziali di login errate; refresh token non valido, scaduto o già usato |
| 403 | token valido ma senza il ruolo ADMIN |
| 404 | risorsa inesistente |
| 409 | operazione rifiutata da una regola sullo stato attuale (ultimo admin, corso con iscritti, abbonamento già iniziato...) |
| 429 | solo `POST /api/auth/login`: troppi tentativi falliti (vedi «Limite ai tentativi di login»). L'intestazione `Retry-After` dice quanti secondi aspettare |

Nota: un indirizzo `/api/...` che non esiste risponde 404 nel formato standard di Spring Boot
(`timestamp`, `status`, `error`, `path`), non in quello sopra.

## 5. Se ricevi 401 o 403: come capire perché

La risposta al client è volutamente generica ("Token mancante, scaduto o non valido"). Il **motivo vero** lo scrive l'app nel
suo **log**, cioè nel terminale di VS Code in cui gira. Cerca la riga `WARN ... API GET /api/... rifiutata con 401`:

| Cosa dice il log | Significato | Cosa fare |
|---|---|---|
| `intestazione Authorization ASSENTE` | il client non ha inviato il token. In Postman succede **quasi sempre perché la variabile `jwt_token` è vuota**: se il token è vuoto, Postman non aggiunge nemmeno l'intestazione | rifai **Auth → Login** e controlla che risponda **200** (se dà 401 controlla `email` e `password` nelle Variables della collection); poi riprova |
| `Bearer token is malformed` | l'intestazione c'è ma il token è vuoto o rotto | la variabile `jwt_token` è vuota o non risolta: rifai **Auth → Login** e controlla che dia 200 |
| `Jwt expired at ...` | access token scaduto (dura 60 minuti) | esegui **Refresh token** (o rifai il Login) |
| `Invalid signature` | token firmato con un'altra chiave (es. emesso da un'altra installazione o con un `JWT_SECRET` diverso) | rifai il Login sull'app che stai interrogando |
| `token revocato (logout, cambio password o ruoli, ...)` | l'access token è stato revocato prima della scadenza: logout, cambio di password o di ruoli dell'utente, sospetto furto del refresh token | esegui **Refresh token** (se il refresh è ancora valido) o rifai il Login |
| `l'utente del token non esiste: eliminato o mai creato` | l'utente a cui appartiene il token è stato eliminato | rifai il Login con un utente esistente |
| `il token non contiene la versione` | token emesso prima dell'introduzione della revoca | rifai il Login (o il Refresh) |
| `negata con 403` | token valido ma senza ruolo ADMIN | usa un admin |

In Postman, per vedere cosa parte davvero: **View → Show Postman Console** e rilancia la richiesta: si vede l'intestazione
`Authorization` inviata. Se vedi `Bearer {{token}}` (con le graffe) o `Bearer ` vuoto, la variabile non è stata sostituita:
controlla che `jwt_token` abbia un *Current value* nell'Environment selezionato (o nella collection se non ne usi uno).

Per il **rinnovo** (`/api/auth/refresh`) il motivo del 401 si legge nel log con `Rinnovo con refresh token rifiutato: ...`
(*token sconosciuto*, *token scaduto*, *token già usato o revocato (possibile riuso)*, *l'utente non ha più una password*).

Il log compare solo dopo aver **riavviato l'app** con il codice aggiornato.

## 6. Sicurezza in breve

- Niente sessioni né cookie: ogni richiesta porta il proprio token (per questo non serve il token CSRF).
- Il token è firmato con la chiave `app.jwt.secret` (**in produzione va impostata con la variabile d'ambiente
  `JWT_SECRET`**, lunga e casuale). Chi la conosce può fabbricare token validi.
- Il contenuto di un token è leggibile da chiunque (provalo su jwt.io): non vi si mette mai nulla di segreto.
- L'**access token** vale al massimo 60 minuti, ma si può **revocare prima**: ogni token contiene la «versione» dei token dell'utente
  (claim `v`) e a ogni richiesta il server la confronta con quella nel database (una `SELECT` per indice, `ValidatoreRevocaToken`).
  La versione aumenta, e quindi tutti i token già emessi smettono di valere, quando l'utente fa **logout**, **cambia password**
  (in quel caso anche i refresh token), **cambia i suoi ruoli** (compresa la rimozione di ADMIN) o quando si sospetta il **furto**
  di un refresh token. Un utente eliminato non passa più perché non esiste. Un login o un rinnovo danno un token con la versione nuova.
- Il **refresh token** invece è controllato a ogni uso: è una stringa casuale (non un JWT) di cui nel database c'è solo l'hash
  SHA-256; si può revocare (logout), ruota a ogni rinnovo, il riuso chiude tutte le sessioni, sparisce se l'utente viene eliminato,
  e il rinnovo emette un access token con i ruoli **attuali** dell'utente.
- **Pulizia:** ogni notte (03:30, `app.pulizia.cron`) l'app cancella dal database i refresh token scaduti. Quelli revocati ma non ancora
  scaduti restano, perché servono a riconoscere il riuso.

### Limite ai tentativi di login

Per frenare chi prova a indovinare le password (vale per `POST /api/auth/login` e per il form del pannello, con gli stessi contatori):

- **5 login falliti** (`app.sicurezza.login-max-tentativi`) dallo stesso indirizzo per la stessa email nella finestra di **15 minuti**
  (`app.sicurezza.login-finestra-minuti`, contata dal primo errore) bloccano quella coppia: il tentativo successivo riceve **429** con
  `Retry-After` (secondi da aspettare), **anche con la password giusta**. Nel pannello si torna a `/login?bloccato`.
- **20 login falliti** (`app.sicurezza.login-max-tentativi-per-ip`) dallo stesso indirizzo, su qualsiasi email, bloccano quell'indirizzo.
- Un login riuscito azzera il contatore della coppia indirizzo+email. Un altro indirizzo non è toccato dal blocco.
- Non contano i dati non validi (400). L'email si confronta senza maiuscole né spazi.
- **Limiti noti:** i contatori stanno in memoria (si azzerano al riavvio e non sono condivisi tra più istanze); l'indirizzo è quello visto
  dal server: dietro un proxy sono tutti lo stesso finché non si configura `server.forward-headers-strategy`.
- Non c'è un limite sul rinnovo (`/api/auth/refresh`): il refresh token è lungo e casuale, non indovinabile.
