#include "server.h"

int main() {
   printf("Il server si sta avviando...\n");
   fflush(stdout);

   int sSocket = setup_server(); //socket del server
   int cSocket; //socket del client
   
   struct sockaddr_in client_addr;
   socklen_t addr_size; 
   pthread_t threads[MAX_CLIENTS]; //salvo gli ID dei thread
   int client_count = 0; //contatore dei client connessi

   printf("clients address: %p\n", clients);  
   fflush(stdout);

   while (client_count < MAX_CLIENTS) {
      addr_size = sizeof(client_addr); //dimensione dell'indirizzo del client
      cSocket = accept(sSocket, (struct sockaddr *)&client_addr, &addr_size); //accetto la connessione del client

      if (cSocket < 0) {
         perror("Errore nell'accettazione della connessione");
         exit(1);
      }

      printf("client_count: %d\n", client_count);
      printf("nuovo giocatore connesso! \n");
      fflush(stdout);

      clients[client_count] = cSocket; //aggiungo il client all'array dei client

      //per ogni nuova connessione, alloco mempria per un nuovo client che verrà poi liberata nella funzione handle_client
      int *new_sock = malloc(sizeof(int));
      if (new_sock == NULL) {
         perror("Errore malloc");
         close(cSocket);
         continue; 
      }
      
      *new_sock = cSocket; //in questo modo sto copiando il valore del csocket del client nella memoria allocata
      //creo un thread per gestire il client con il nuovo puntatore new_sock
      if (pthread_create(&threads[client_count], NULL, handle_client, new_sock) != 0) {
         perror("Errore nella creazione del thread");
         free(new_sock);
         close(cSocket);
         continue;
      }

      client_count++; //incremento il contatore dei client connessi
   }

   // Attendo la terminazione dei thread, se non lo faccio termiato il main mi si chiude tutto anche se i client non sono finiti.
   //potevo farlo anche con while (1) pause() ma non è elegante
   for (int i = 0; i < client_count; i++) {
      pthread_join(threads[i], NULL);
   }

   close(sSocket); //chiudo il socket del server
   return 0;
}
