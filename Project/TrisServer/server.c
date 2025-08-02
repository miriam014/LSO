#include "server.h"

int clients[MAX_CLIENTS];

// Funzione per configurare il server (apre la socket e mette il server in ascolto)
int setup_server() {
    int sSocket; //socket del server
    struct sockaddr_in server_addr; //struttura per l'indirizzo del server

    //1. creo il socket
    sSocket = socket(AF_INET, SOCK_STREAM, 0);
    if (sSocket == -1) {
        perror("Errore nella creazione del socket");
        exit(1);
    }

    //2. configuro l'indirizzo del server
    server_addr.sin_family = AF_INET; //Usa l’indirizzamento IPv4
    server_addr.sin_addr.s_addr = INADDR_ANY; //Indica che il server può accettare connessioni su qualsiasi indirizzo IP
    server_addr.sin_port = htons(PORT); //imposta la porta su cui il server sarà in ascolto.

    //3. assegno l'indirizzo al socket
    //bind: associa il socket a un indirizzo IP e una porta
    if (bind(sSocket, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        perror("Errore nell'assegnazione dell'indirizzo al socket");
        exit(1);
    }
    printf("bind eseguito sulla porta %d\n", PORT); //stampo il messaggio di avvenuta assegnazione dell'indirizzo al socket

    //4. metto il server in ascolto
    if (listen(sSocket, MAX_CLIENTS) < 0) {
        perror("Errore nell'ascolto del server");
        exit(1);
    }

    printf("Server in ascolto sulla porta %d\n", PORT); //stampo il messaggio di avvenuta messa in ascolto del server
    return sSocket; //ritorno il socket del server
}




void *handle_client(void *arg) {
    int cSocket = *(int *)arg; //prendo il socket passato come argomento e lo assegno ad una variabile
    free(arg); //libero la memoria allocata per il nuovo socket
    
    char buffer[1024] = {0}; //buffer per la ricezione dei messaggi

    while (1) {
        memset(buffer, 0, sizeof(buffer)); //pulisco il buffer

        int byte_received = recv(cSocket, buffer, sizeof(buffer), 0); //ricevo il messaggio
        if (byte_received <= 0) { //se il messaggio è vuoto o c'è un errore
            perror("Un client disconnesso\n");
            close(cSocket); 
            return NULL; 
        }

        buffer[byte_received] = '\0';  // assicurati che sia una stringa terminata
        printf("Messaggio ricevuto: %s\n", buffer);

        // strncmp verifica che l'inizio della mia stringa sia "exit"
        if (strncmp(buffer, "exit", 4) == 0) {
            close(cSocket);
            return NULL;
        }

        const char *response = "Messaggio ricevuto dal server\n";
        if (write(cSocket, response, strlen(response)) < 0) {
            perror("Errore nell'invio della risposta");
            close(cSocket);
            return NULL;
        }
    }
}