#include "server.h"

int clients[MAX_CLIENTS];
int client_count = 0;
pthread_mutex_t client_count_lock = PTHREAD_MUTEX_INITIALIZER;

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
    char buffer[1024]; //buffer per la ricezione dei messaggi

    while (1) {
        memset(buffer, 0, sizeof(buffer)); //pulisco il buffer
        int byte_received = recv(cSocket, buffer, sizeof(buffer)-1, 0); //ricevo il messaggio
        
        if (byte_received <= 0) { //client chiuso o un errore
            perror("Client disconnesso");
            fflush(stdout);

            pthread_mutex_lock(&client_count_lock);
            // Rimuove il client dall'array
            for (int i = 0; i < client_count; i++) {
                if (clients[i] == cSocket) {
                    clients[i] = clients[client_count - 1];
                    client_count--;
                    break;
                }
            }
            pthread_mutex_unlock(&client_count_lock);

            close(cSocket);
            rimuovi_partite_di_sock(cSocket);
            return NULL;
        }

        buffer[byte_received] = '\0';  // assicurati che sia una stringa terminata
        // rimuovi eventuali \r o \n finali
        for (int i = 0; i < byte_received; i++) {
            if (buffer[i] == '\r' || buffer[i] == '\n') {
                buffer[i] = '\0';
                break;
            }
        }

        printf("[sock=%d] -> %s\n", cSocket, buffer);
        fflush(stdout); // forza la stampa immediata nel terminale del container


        // --- PARSING TESTUALE ---
        char cmd[32], utente[32], nome[64], ospite[32];
        int id=0, cella=-1, accetta=0, voglio=0, risposta=0;

        // sscanf legge dal buffer secondo il formato e %31s legge una sftringa lunga fino a 31 carateri e la mette poi nella variabile utente
        if (sscanf(buffer, "CREA_PARTITA %31s", utente) == 1) {
            cmd_crea_partita(cSocket, utente);
        }
        else if (strcmp(buffer, "LISTA_ATTESA") == 0) {
            cmd_partite_in_attesa(cSocket);
        }
        else if (strcmp(buffer, "MIE_PARTITE") == 0) {
            cmd_mie_partite(cSocket);
        }
        else if (sscanf(buffer, "ANNULLA_PARTITA %d", &id) == 1) {
            cmd_annulla_partita(cSocket, id);
        }
        else if (sscanf(buffer, "ENTRA_RICHIESTA %31s %d", utente, &id) == 2) {
            cmd_entra_richiesta(cSocket, utente, id);
        }
        else if (sscanf(buffer, "ENTRA_RISPOSTA %31s %d %d %31s", utente, &id, &accetta, ospite) == 4) {
            cmd_entra_risposta(cSocket, utente, id, accetta, ospite);
        }
        else if (sscanf(buffer, "MOSSA %31s %d %d", utente, &id, &cella) == 3) {
            cmd_mossa(cSocket, utente, id, cella);
        }
        else if (sscanf(buffer, "REMATCH_RICHIESTA %31s %d", utente, &id) == 2) {
        cmd_rematch_richiesta(cSocket, utente, id);
        }
        else if (sscanf(buffer, "REMATCH_RISPOSTA %31s %d %d", utente, &id, &accetta) == 3) {
            cmd_rematch_risposta(cSocket, utente, id, accetta);
        }
        else {
            send_msg(cSocket, "ERRORE comando non riconosciuto");
        }
    }
}