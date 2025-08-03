package tris;

import java.io.*;
import java.net.*;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    private static Stage primaryStage;
    private static Socket socket;
    private static BufferedReader input;
    private static PrintWriter output;

    // Avvio dell'applicazione JavaFX
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        connectToServer();
        setRoot("home.fxml");
    }

    public static void setRoot(String fxml) throws IOException {
        Parent root = FXMLLoader.load(Main.class.getResource("/interfaccia/" + fxml));
        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("Tris");
        primaryStage.show();
    }


    // Connessione al server (una volta sola all'avvio)
    public static void connectToServer() throws IOException {
        final int SERVER_PORT = 5001; // porta per connettersi al server
        final String SERVER_IP = "localhost"; // il nome del servizio/server nel docker-compose

        socket = new Socket(SERVER_IP, SERVER_PORT);
        input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        output = new PrintWriter(socket.getOutputStream(), true);

        System.out.println("Connesso al server!");
    }

    // Metodo per inviare messaggi al server
    public static void sendToServer(String messaggio) {
        if (output != null) {
            output.println(messaggio);
            System.out.println("[DEBUG] JSON inviato: " + messaggio);
        }
    }

    // Metodo per ricevere messaggi dal server
    public static String receiveFromServer() throws IOException {
        if (input != null) {
            return input.readLine();
        }
        return null;
    }

    // Chiudi la connessione (facoltativo)
    public static void closeConnection() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            System.out.println("Connessione chiusa.");
        }
    }
}

