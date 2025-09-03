package tris.Controller;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import tris.Main;
import tris.MessaggiBuilder;
import tris.Sessione;

public class HomeController {
    @FXML private TextField InsertName;
    @FXML private Button createNewPartita;
    @FXML private Label popupLabel;
    @FXML private VBox popupAttesa;


    @FXML
    private void CreateNew() {
        String nomeUtente = InsertName.getText().trim();
        if (nomeUtente.isEmpty()) {
            System.out.println("Inserisci un nome valido.");
            return;
        }
        Sessione.setUsername(nomeUtente);
        // invia comando al server
        Main.sendToServer(MessaggiBuilder.creaPartita(nomeUtente));
        popupLabel.setText("In attesa di un avversario...");
        popupAttesa.setVisible(true);

        // Esegui la parte socket su un thread SEPARATO
        Main.serverThread = new Thread(() -> {
            try {
                String msg;
                while (!Thread.currentThread().isInterrupted() && (msg = Main.receiveFromServer()) != null) {
                    String finalMsg = msg;
                    javafx.application.Platform.runLater(() -> handleServerMessage(finalMsg));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Main.serverThread.setDaemon(true);
        Main.serverThread.start();
    }

    private void handleServerMessage(String msg) {
        if (msg.startsWith("ATTESA_AVVERSARIO")) {
            popupLabel.setText("In attesa di un avversario...");
            popupAttesa.setVisible(true);

            // Nasconde automaticamente il popup dopo 30 secondi se nessuno accetta
            PauseTransition wait30sec = new PauseTransition(Duration.seconds(30));
            wait30sec.setOnFinished(event -> {
                popupLabel.setText("Non ci sono giocatori disponibili al momento.");
                popupAttesa.setVisible(false);
            });
            wait30sec.play();

        } else if (msg.startsWith("ENTRA_ESITO")) {
            if (msg.contains("accetta=true")){
                popupAttesa.setVisible(false);
                try{
                    Main.setRoot("partita.fxml");
                } catch (Exception e){
                    e.printStackTrace();
                }
            } else {
                popupLabel.setText("Richiesta rifiutata dal proprietario.");
                popupAttesa.setVisible(true);

                PauseTransition wait3sec = new PauseTransition(Duration.seconds(3));
                wait3sec.setOnFinished(event -> popupAttesa.setVisible(false));
                wait3sec.play();
            }
        }
    }
}

