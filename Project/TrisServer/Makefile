CC = gcc 
CFLAGS = -Wall -Wextra -g

all: trisServer

trisServer: main.o server.o
	$(CC) $(CFLAGS) -o trisServer main.o server.o -lpthread

main.o: main.c server.h
	$(CC) $(CFLAGS) -c main.c

server.o: server.c server.h
	$(CC) $(CFLAGS) -c server.c

clean:
	rm -f *.o trisServer
