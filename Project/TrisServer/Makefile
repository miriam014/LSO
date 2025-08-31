CC = gcc 
CFLAGS = -Wall -Wextra -g

all: trisServer

trisServer: main.o server.o router.o comandi.o
	$(CC) $(CFLAGS) -o trisServer main.o server.o router.o comandi.o -lpthread

main.o: main.c server.h
	$(CC) $(CFLAGS) -c main.c

server.o: server.c server.h
	$(CC) $(CFLAGS) -c server.c

router.o: router.c server.h
	$(CC) $(CFLAGS) -c router.c

comandi.o: comandi.c server.h
	$(CC) $(CFLAGS) -c comandi.c

clean:
	rm -f *.o trisServer
