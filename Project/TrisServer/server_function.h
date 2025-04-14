#ifndef SERVER_H
#define SERVER_H //impedisce inclusioni multiple dello stesso file

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <pthread.h>
#include <sys/socket.h>

#define PORT 5001 // Porta su cui il server ascolta
#define MAX_CLIENTS 2

extern int clients[MAX_CLIENTS]; // Dichiarazione esterna dell'array dei client così può essere usato in altri file

void *handle_client(void *arg);  // Dichiarazione della funzione
int setup_server();              // Funzione per configurare il server

#endif