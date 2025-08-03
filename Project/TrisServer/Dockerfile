FROM gcc:bullseye

WORKDIR /app

# Copia il codice server nel container
COPY . .

# Compila il server
RUN make clean && make

# Espone la porta usata nel server (es. 5001)
EXPOSE 5001

# Comando di avvio
CMD ["./trisServer"]
