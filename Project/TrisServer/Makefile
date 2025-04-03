# Nome dell'eseguibile
TARGET = trisServer

# Compilatore
CC = g++

# Flags di compilazione
CFLAGS = -Wall -Wextra -g

# File sorgente
SRC = server.c server_function.c

# File oggetto
OBJ = $(SRC:.c=.o)

# Regola principale per creare l'eseguibile
all: $(TARGET)

# Creazione dell'eseguibile
$(TARGET): $(OBJ)
	$(CC) $(CFLAGS) -o $(TARGET) $(OBJ)

# Regola per compilare i file sorgente in file oggetto
%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

# Pulizia dei file generati
clean:
	rm -f $(OBJ) $(TARGET)

# Ricompilazione forzata
rebuild: clean all
