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