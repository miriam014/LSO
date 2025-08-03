package tris.Controller;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import tris.Main;
import tris.MessaggiBuilder;
import tris.Sessione;

public class HomeController {
    @FXML private TextField InsertName;
    @FXML private Button createNew;

    @FXML
    private void initialize() {
        // Puoi mettere logica iniziale qui se ti serve
    }

    @FXML
    private void CreateNew() {
        String nome = InsertName.getText();
        if (nome.isEmpty()) {
            System.out.println("Inserisci un nome valido.");
            return;
        }
        Sessione.setUsername(nome); //mi salvo il nome

        // Esegui la parte socket su un thread SEPARATO
        new Thread(() -> {
            try {
                String messaggioJSON = MessaggiBuilder.costruisci("crea");
                Main.sendToServer(messaggioJSON);

                String risposta = Main.receiveFromServer();
                System.out.println("Risposta del server: " + risposta);

                // Per cambiare scena, lo faccio sul thread JavaFX
                javafx.application.Platform.runLater(() -> {
                    try {
                        Main.setRoot("partita.fxml");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}

