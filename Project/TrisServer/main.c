#include "server.h"

int main() {
   printf("Il server si sta avviando...\n");
   fflush(stdout);

   int sSocket = setup_server(); //crea e mette in ascolto il server-- socket
   int cSocket; //socket del client
   
   struct sockaddr_in client_addr;
   socklen_t addr_size; 

   printf("clients address: %p\n", clients);  
   fflush(stdout);

   while (1) { //metto (1) al posto del controllo sui giocatori perchè lo faccio dooo visto che dentro aumento
      addr_size = sizeof(client_addr); //dimensione dell'indirizzo del client
      cSocket = accept(sSocket, (struct sockaddr *)&client_addr, &addr_size); //accetto la connessione del client

      if (cSocket < 0) {
         perror("Errore nell'accettazione della connessione");
         exit(1);
      }

      pthread_mutex_lock(&client_count_lock);
      if (client_count >= MAX_CLIENTS) {
         pthread_mutex_unlock(&client_count_lock);
         printf("Server pieno: rifiutata connessione\n");
         close(cSocket);
         continue;
      }

      printf("nuovo giocatore connesso! \n");
      printf("client_count: %d\n", client_count+1);
      fflush(stdout);

      clients[client_count] = cSocket; //aggiungo il client all'array dei client
      client_count++; //incremento il contatore dei client connessi
      pthread_mutex_unlock(&client_count_lock);

      //per ogni nuova connessione, alloco mempria per un nuovo client che verrà poi liberata nella funzione handle_client
      int *new_sock = malloc(sizeof(int));
      if (!new_sock) {
         perror("Errore malloc");
         close(cSocket);
         pthread_mutex_lock(&client_count_lock);
         client_count--; //decremento il contatore dei client connessi
         pthread_mutex_unlock(&client_count_lock);
         continue; 
      }
      *new_sock = cSocket; //in questo modo sto copiando il valore del csocket del client nella memoria allocata
      
      //creo un thread per gestire il client con il nuovo puntatore new_sock
      pthread_t thread_id;
      if (pthread_create(&thread_id, NULL, handle_client, new_sock) != 0) {
         perror("Errore nella creazione del thread");
         free(new_sock);
         close(cSocket);
         pthread_mutex_lock(&client_count_lock);
         client_count--;
         pthread_mutex_unlock(&client_count_lock);
         continue;
      }

      pthread_detach(thread_id);
   }

   close(sSocket); //chiudo il socket del server
   return 0;
}
