#include "server_function.h"

void *handle_client(void *arg) {
    int cSocket = *(int *)arg; //prendo il socket passato come argomento e lo assegno ad una variabile
    char buffer[1024] = {0}; //buffer per la ricezione dei messaggi

    while (1) {
        memset(buffer, 0, sizeof(buffer)); //pulisco il buffer
        int byte_received = recv(cSocket, buffer, sizeof(buffer), 0); //ricevo il messaggio

        if (byte_received <= 0) { //se il messaggio è vuoto o c'è un errore
            printf("Un client disconnesso\n");
            break; //esco dal ciclo
        }

        printf("Messaggio ricevuto: %s\n", buffer); //stampo il messaggio ricevuto

        //invio il messaggio ricevuto a tutti i client connessi (altro giocatore)
        for (int i = 0; i < MAX_CLIENTS; i++) {
            if (clients[i] != cSocket && clients[i] != 0) {
                send(clients[i], buffer, strlen(buffer), 0);
            }
        }

          // Rispondi direttamente al client con un messaggio di conferma o altro
          const char* response = "Messaggio ricevuto!";
          send(cSocket, response, strlen(response), 0);
    }
        close(cSocket); //chiudo il socket
        return NULL; //termino il thread
    
}

int setup_server() {
    int sSocket; //socket del server
    struct sockaddr_in server_addr; //struttura per l'indirizzo del server

    //creo il socket
    printf("Tentativo di creazione del socket\n"); //stampo il messaggio di tentativo di creazione del socket
    sSocket = socket(AF_INET, SOCK_STREAM, 0);
    if (sSocket == -1) {
        perror("Errore nella creazione del socket");
        exit(1);
    }
    printf ("Socket creato con successo\n"); 

    //configuro l'indirizzo del server
    server_addr.sin_family = AF_INET; //Usa l’indirizzamento IPv4
    server_addr.sin_addr.s_addr = INADDR_ANY; //Indica che il server può accettare connessioni su qualsiasi indirizzo IP
    server_addr.sin_port = htons(PORT); //imposta la porta su cui il server sarà in ascolto.

    //assegno l'indirizzo al socket
    printf("Tentativo di bind sulla porta %d\n", PORT); //stampo il messaggio di tentativo di assegnazione dell'indirizzo al socket
    //bind: associa il socket a un indirizzo IP e una porta
    if (bind(sSocket, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        perror("Errore nell'assegnazione dell'indirizzo al socket");
        exit(1);
    }
    printf("bind eseguito sulla porta %d\n", PORT); //stampo il messaggio di avvenuta assegnazione dell'indirizzo al socket

    //metto il server in ascolto
    if (listen(sSocket, MAX_CLIENTS) == 0) {
        printf("Server in ascolto sulla porta %d\n", PORT);
    } else {
        perror("Errore nell'ascolto del server");
        exit(1);
    }

    return sSocket; //ritorno il socket del server
}