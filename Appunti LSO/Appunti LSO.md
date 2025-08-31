## Server 
###  Funzioni usate nel server c

 **1. `socket()`**
`int socket(int domain, int type, int protocol);`

Socket_se = socket(AF_INET, SOCK_STREAM, 0);

 Crea una nuova socket dove:
- `AF_INET` → IPv4
- `SOCK_STREAM` → connessione TCP
- `0` → protocollo TCP di default
 Restituisce un **file descriptor** (numero intero ≥ 0)
 Se fallisce restituisce `-1`

---

**2. `bind()`**
`int bind(int sockfd, const struct sockaddr *addr, socklen_t addrlen);`

if (bind(Socket_se, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) 

 Associa il socket a un indirizzo IP e una porta
- Socket_se: file descriptor della socket
- `addr`: struttura `sockaddr_in` che contiene IP e porta
Se fallisce → `-1` (es. porta già in uso)

---

**3. `listen()`**
`int listen(int sockfd, int backlog);`

if (listen(Socket_se, MAX_CLIENTS) < 0) 

Mette la socket in modalità "ascolto" per nuove connessioni
- `backlog`: quanti client possono essere in attesa
-  Se fallisce → `-1`
-  Se va bene → `0`

---

**4. `htons()`**
`uint16_t htons(uint16_t hostshort);`

 **Converte un numero (es. porta) da byte order di host a byte order di rete**
- Serve per essere compatibili con altri dispositivi sulla rete
- `htons(12345)` → converte la porta 12345
Imposta la porta su cui il server sarà in ascolto.

---

**5. `INADDR_ANY`**
Costante speciale, Indica che il server può accettare connessioni su qualsiasi indirizzo IP

`server_addr.sin_addr.s_addr = INADDR_ANY;`

---

**6. `struct sockaddr_in`**
 Struttura che rappresenta l’indirizzo IP e porta

---

**7. `perror()`**
 Stampa un messaggio di errore leggibile, in base all’errore dell’ultima syscall fallita
 
`void perror(const char *s);`

`perror("Errore nell'ascolto del server");

--- 
**8. close()**
`close(socket)`
Chiude il socket (o un qualsiasi file descriptor).

--- 
**9. memset()**
`memset(buffer, 0, sizeof(buffer))`
 Riempie il `buffer` con zeri.
 
--- 

#####  Funzioni per leggere e scrivere su una soket
Se si vuole utilizzare codice leggibile e compatto si usano read() e write(), ma nel caso in cui si vogliano utilizzare poi in futuro delle funzionalità avanzate allora si fa uso di recv() e send() che sono le stesse praticamente

**read()**
 `read(int socket, void *buffer, size count)`
Legge **fino a `size` byte** dalla socket e li memorizza in `buffer`.
Restituisce:
-  il numero di byte letti
- `0` se il client ha chiuso la connessione
- `-1` in caso di errore

**write()**
`write(ing socket, cont void *buffer, size count)`
 Scrive count byte su una socket (o file, etc)
 Invia fino a `size` byte del `buffer` al socket.
 È usato per mandare un messaggio di risposta al client.

**recv()**
 `recv(int sockfd, void *buf, size_t len, int flags)`
Fa la stessa cosa di `read()`, ma è **specifica per socket**
Permette di usare **flag avanzati**, ad esempio:
- `MSG_DONTWAIT`: non bloccare se non ci sono dati
- `MSG_PEEK`: guarda i dati senza rimuoverli dal buffer

**send()**
`send(int sockfd, const void *buf, size_t len, int flags)`

 Come `write()`, ma specifica per socket
- Anche qui puoi usare flag (es. `MSG_NOSIGNAL` per non ricevere SIGPIPE su disconnessione)

### Messaggi 
 **Client (giocatore)** manda:
- `crea_partita` → per creare una nuova partita
- `accetta_partecipazione` → per accettare un giocatore nella partita
- `rifiuta_partecipazione` → per rifiutarlo
- `inizia_nuova` → per iniziare una nuova partita dopo una terminata

 **Server** risponde o invia:
- `partecipa_partita` → ti propone un nuovo giocatore che vuole unirsi
- `vittoria` / `sconfitta` / `pareggio` → fine partita    
- `aggiorna_stato` (tipo `"è il tuo turno"`, `"mossa avversaria"`, ecc.)
- `errore` → messaggi di errore vari


### non so cosa sia
- **Limiti**: `MAX_CLIENTI`, `MAX_PARTITE` → array statici semplici.
    
- **Stati**: `StatoPartita { ST_NUOVA, ST_IN_ATTESA, ST_IN_CORSO, ST_TERMINATA }`.
    
- **Dati runtime**:
    
    - `Utente` (socket, username, partita corrente),
        
    - `Partita` (id, name, owner, guest, stato, scacchiera, turno).
        
- **Funzioni “perché servono”**:
    
    - `invia_riga` / `ricevi_riga`: su TCP i messaggi possono spezzarsi; con “JSON per riga” ogni messaggio termina a `\n`. Queste 2 funzioni fanno il **framing** minimo e affidabile.
        
    - `estrai_stringa_json`: micro-parser per leggere una chiave (“tipo”, “utente”, …) senza librerie.
        
    - `gestisci_comando`: router che smista ai vari `cmd_*` (uno per requisito).
        
    - (Più avanti) `azzera_scacchiera`, `esito_scacchiera`, `invia_stato_partita`, `cmd_crea_partita`, `cmd_lista_partite`, `cmd_richiesta_entrare`, `cmd_risposta_entrare`, `cmd_mossa`, `cmd_rematch`.
        
- **Database** (lo mettiamo dopo): prototipi tipo `db_inizializza`, `db_crea_partita`, … per persistere.
    

---

### Nomi ITA da usare nel **protocollo JSON**

Client → Server:

- `{"tipo":"CIAO","utente":"miriam"}`
    
- `{"tipo":"CREA_PARTITA","utente":"miriam","nome":"Partita di miriam"}`
    
- `{"tipo":"LISTA_PARTITE"}`
    
- `{"tipo":"ENTRA_RICHIESTA","utente":"luca","id_partita":3}`
    
- `{"tipo":"ENTRA_RISPOSTA","utente":"miriam","id_partita":3,"accetta":true,"ospite":"luca"}`
    
- `{"tipo":"MOSSA","utente":"miriam","id_partita":3,"cella":4}`
    
- `{"tipo":"REMATCH","utente":"miriam","id_partita":3,"voglio":true}`
    

Server → Client:

- `{"tipo":"BENVENUTO","utente":"miriam"}`
    
- `{"tipo":"PARTITA_CREATA","id_partita":3,"proprietario":"miriam","nome":"..."}`
    
- `{"tipo":"ELENCO_PARTITE","partite":[...]}`
    
- `{"tipo":"ENTRA_RICHIESTO","id_partita":3,"da_utente":"luca"}` (solo al proprietario)
    
- `{"tipo":"ENTRA_ESITO","id_partita":3,"accetta":true,"ospite":"luca"}` (allo sfidante)
    
- `{"tipo":"STATO_PARTITA", ... }` (stato/bacheca/turno)
    
- `{"tipo":"MOSSA_OK","id_partita":3,"scacchiera":"...","prossimo_turno":"..."}`
    
- `{"tipo":"PARTITA_FINITA","id_partita":3,"esito":"vittoria|sconfitta|pareggio","vincitore":"...?"}`
    
- `{"tipo":"REMATCH_STATO","id_partita":3,"pronto_prop":true,"pronto_ospite":false}`
    
- `{"tipo":"ERRORE","messaggio":"..."}`


# La logica di vittoria resta **sul server**; il client si limita a inviare mosse e ad aggiornare la griglia quando riceve `STATO_PARTITA` / `PARTITA_FINITA`.