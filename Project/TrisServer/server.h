#ifndef SERVER_H
#define SERVER_H //impedisce inclusioni multiple dello stesso file

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <pthread.h>
#include <sys/socket.h>
#include <ctype.h>
#include <errno.h>

#define PORT 5001 
#define MAX_CLIENTS 16 // Numero massimo di client che il server può gestire contemporaneamente
#define MAX_PARTITE 50 

extern int clients[MAX_CLIENTS]; // Dichiarazione esterna dell'array dei client così può essere usato in altri file
extern int client_count; // Dichiarazione esterna del contatore dei client
extern pthread_mutex_t client_count_lock; // Mutex per proteggere l'accesso a client_count

// Stati di gioco
typedef enum { ST_NUOVA=0, ST_IN_ATTESA=1, ST_IN_CORSO=2, ST_TERMINATA=3 } StatoPartita;

// Strutture runtime  UTENTE
typedef struct {
    int  sock;
    char username[32];
    int  idPartitaCorrente; // -1 se non sta giocando
    int  attivo;
} Utente;

// Strutture runtime  PARTITA
typedef struct {
    int         id;
    char        proprietario[32];
    char        ospite[32];
    int         proprietario_sock;
    int         ospite_sock;
    StatoPartita stato;
    char        scacchiera[10];  // 9 celle + '\0', '.' = vuota
    char        turno[32];       // username di chi deve giocare
    int         pronto_proprietario;
    int         pronto_ospite;
} Partita;

int setup_server();              // Funzione per configurare il server (apre la soket e mette il server in ascolto)
void *handle_client(void *arg);  // Dichiarazione della funzione


// Comunicazione
//recive line non ci serve perchè utilizziamo direttamente recv 
//invece abbiamo scritto send line invece di utilizzare direttemente write così da poter leggere i messaggi separati riga per riga senza doverli gestire con strlen()
ssize_t send_msg(int sock, const char *s);

// Comandi
void cmd_crea_partita(int sock, const char *utente);
void cmd_mie_partite(int sock);
void cmd_partite_in_attesa(int sock);
void cmd_annulla_partita(int sock, int id_partita);
void cmd_entra_richiesta(int sock, const char *utente, int id_partita);
void cmd_entra_risposta(int sock, const char *ownerUser, int id_partita, int accetta, const char *ospite);
void cmd_mossa(int sock, const char *utente, int id_partita, int cella);
void cmd_rematch_richiesta(int sock, const char *utente, int id_partita);
void cmd_rematch_risposta(int sock, const char *utente, int id_partita, int accetta);

// Gioco / broadcast
void azzera_scacchiera(Partita *p);
char esito_scacchiera(const char b[10]);
void invia_a_tutti(const char *msg, int escludi_sock);
void invia_stato_partita(Partita *p);

#endif