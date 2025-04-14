import java.io.*;
import java.net.*;

public class Main {
    public static void main(String[] args) {
        final int SERVER_PORT = 5001; // porta per connettersi al server
        final String SERVER_IP = "127.0.0.1"; // IP del server (localhost)

        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT); // usa try-with-resources per chiudere automaticamente il socket
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream())); // riceve dati dal server
             PrintWriter output = new PrintWriter(socket.getOutputStream(), true); // invia i dati al server
             BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))) { // legge input dell'utente da tastiera

            System.out.println("Connesso al server! Digita un messaggio:");

            // loop per inviare e ricevere messaggi
            while (true) {
                System.out.print("> ");
                String userMessage = stdIn.readLine();

                if (userMessage.equals("exit")) {
                    break; // se l'utente digita "exit", chiude la connessione
                }

                output.println(userMessage); // invia il messaggio al server

                // Verifica se c'è una risposta dal server
                String serverResponse = input.readLine();
                if (serverResponse != null) {
                    System.out.println("Server risponde: " + serverResponse);
                } else {
                    System.out.println("Errore: Il server ha chiuso la connessione.");
                    break;
                }
            }

        } catch (IOException e) {
            System.out.println("Errore: " + e.getMessage());
        }
    }
}
