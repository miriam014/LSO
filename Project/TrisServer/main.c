#include "server.h"

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
   clients[client_count] = cSocket; //aggiungo il client all'array dei client

   //per ogni nuova connessione, alloco mempria per un nuovo client che verrà poi liberata nella funzione handle_client
   int *new_sock = malloc(sizeof(int));
   *new_sock = cSocket; //in questo modo sto copiando il valore del csocket del client nella memoria allocata
   pthread_create(&tid, NULL, handle_client, new_sock); //creo un thread per gestire il client con il nuovo puntatore new_sock
   
   client_count++; //incremento il contatore dei client connessi
   }

   //attenzione, non ho gestito i casi in cui l'allocazione e la creazione dei trads non andasse a buon fine

   close(sSocket); //chiudo il socket del server
   return 0;
}
