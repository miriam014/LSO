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
    
// Stati di gioco
typedef enum { ST_NUOVA=0, ST_IN_ATTESA=1, ST_IN_CORSO=2, ST_TERMINATA=3 } StatoPartita;

// Strutture runtime 
typedef struct {
    int  sock;
    char username[32];
    int  idPartitaCorrente; // -1 se non sta giocando
    int  attivo;
} Utente;

typedef struct {
    int         id;
    char        nome[32];
    char        proprietario[32];
    char        ospite[32];
    StatoPartita stato;
    char        scacchiera[10];  // 9 celle + '\0', '.' = vuota
    char        turno[32];       // username di chi deve giocare
    int         pronto_proprietario;
    int         pronto_ospite;
} Partita;

void *handle_client(void *arg);  // Dichiarazione della funzione
int setup_server();              // Funzione per configurare il server (apre la soket e mette il server in ascolto)



// Framing messaggi (JSON-per-riga)
ssize_t send_line(int sock, const char *s);
ssize_t receive_line(int sock, char *buf, size_t cap);

// Router comandi
void handle_command(int sock, const char *line);

// Handler richiesti dalla traccia
void cmd_ciao(int sock, const char *utente);
void cmd_crea_partita(int sock, const char *utente, const char *nome);
void cmd_lista_partite(int sock);
void cmd_entra_richiesta(int sock, const char *utente, int id_partita);
void cmd_entra_risposta(int sock, const char *ownerUser, int id_partita, int accetta, const char *ospite);
void cmd_mossa(int sock, const char *utente, int id_partita, int cella);
void cmd_rematch(int sock, const char *utente, int id_partita, int voglio);

// Gioco / broadcast
void azzera_scacchiera(Partita *p);
char esito_scacchiera(const char b[10]);
void invia_a_tutti(const char *msg, int escludi_sock);
void invia_stato_partita(Partita *p);

#endif