
CC = gcc
CFLAGS = -Wall -Wextra -g
OBJ = server.o server_function.o

trisServer: $(OBJ)
	$(CC) $(CFLAGS) -o trisServer $(OBJ)

server.o: server.c server_function.h
	$(CC) $(CFLAGS) -c server.c

server_function.o: server_function.c server_function.h
	$(CC) $(CFLAGS) -c server_function.c

clean:
	rm -f $(OBJ) trisServer

