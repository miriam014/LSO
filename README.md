## Istruzioni per la compilazione

**1. Avviare il progetto con Docker**
Posizionati nella cartella contenente il fil `docker-compose.yml` ed esegui sul terminale 

	docker-compose build

**2**. Successivamente avvia il **server** 

	docker-compose up server

In questo modo il server si metterà in ascolto sulla porta 5001 e sarà pronto per ricevere connessioni dai client.

**3.** Successivamente avvia il **client** 
Apri un nuovo terminale e posizionati nella cartella del client (o se preferisci quello della tua IDE) ed eseguire la compilazione e l'avio della GUI JavaFX:  (**solo dopo** che il server è attivo)

	mvn clean install
	mvn javafx:run

A questo punto si aprirà l'interfaccia grafica e si potrà iniziare ad interagire con il server.

## Istruzioni per l'uscita e lo spegnimento del progetto
- Per chiuder il client basta chiudere la finestra dell'interfaccia 
- Per spegnere il server si deve aprire il terminale dove si è eseguito  `docker-compose up server` e premere: `CTRL + C`
  oppure eseguire il comando `docker-compose down` che arresta e rimuove tutti i container e la rete Docker associata.