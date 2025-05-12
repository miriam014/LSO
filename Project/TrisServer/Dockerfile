FROM gcc:latest

WORKDIR /app
COPY . /app

RUN make  # Usa il Makefile per la compilazione
RUN ls -alh /app  # Aggiungi questa riga per vedere i file in /app
RUN chmod +x /app/trisServer  # Assicurati che trisServer sia eseguibile


CMD ["./trisServer"]
