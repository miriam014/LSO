#include "server_function.h"

int clients[MAX_CLIENTS]={0}; //variabile globale clients per vederla anche all'esterno del codice

int main() {
    int sSocket = setup_server(); //socket del server
    int cSocket; //socket del client
    struct sockaddr_in client_addr;
    socklen_t addr_size; 
    pthread_t tid; //thread id

    printf("clients address: %p\n", clients);  
    int client_count = 0; //contatore dei client connessi

    while (client_count < MAX_CLIENTS) {
       addr_size = sizeof(client_addr); //dimensione dell'indirizzo del client
       cSocket = accept(sSocket, (struct sockaddr *)&client_addr, &addr_size); //accetto la connessione del client

         if (cSocket < 0) {
              perror("Errore nell'accettazione della connessione");
              exit(1);
         }

         printf("nuovo giocatore connesso! \n");

        //aggiungo il client all'array dei client
        clients[client_count] = cSocket;
        pthread_create(&tid, NULL, handle_client, &cSocket); //creo un thread per gestire il client
        client_count++; //incremento il contatore dei client connessi
     }

        close(sSocket); //chiudo il socket del server
        return 0;
    
}
