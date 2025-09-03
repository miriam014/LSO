package tris.Controller;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import tris.Main;
import tris.MessaggiBuilder;
import tris.Sessione;

public class HomeController {
    @FXML private Button createNew;
    @FXML private TextField InsertName;
    @FXML private Label popupLabel;
    @FXML private VBox popupAttesa;

    @FXML
    public void initialize() {
        // Qui registro il listener per i messaggi dal server
        Main.getNetClient().setOnMessage(msg ->
                Platform.runLater(() -> handleServerMessage(msg))
        );
    }

    @FXML
    private void CreateNew() {
        String nomeUtente = InsertName.getText().trim();
        if (nomeUtente.isEmpty()) {
            System.out.println("Inserisci un nome valido.");
            return;
        }
        Sessione.setUsername(nomeUtente);
        createNew.setDisable(true);         // disabilito il bottone per evitare che il client invia più richieste prima di ricevere almeno una risposta

        // invia comando al server
        Main.getNetClient().send(MessaggiBuilder.creaPartita(nomeUtente));
        popupLabel.setText("In attesa di un avversario...");
        popupAttesa.setVisible(true);
    }

    private void handleServerMessage(String msg) {
        System.out.println("[DEBUG] Ricevuto: " + msg);

        if (msg.startsWith("ATTESA_AVVERSARIO")) {
            popupLabel.setText("In attesa di un avversario...");
            popupAttesa.setVisible(true);

            // Nasconde automaticamente il popup dopo 10 secondi se nessuno accetta
            PauseTransition wait10sec = new PauseTransition(Duration.seconds(10));
            wait10sec.setOnFinished(event -> {
                popupLabel.setText("Non ci sono giocatori disponibili al momento.");

                // Aspetta 3 secondi e poi nasconde il popup
                PauseTransition wait3sec = new PauseTransition(Duration.seconds(3));
                wait3sec.setOnFinished(e -> popupAttesa.setVisible(false));
                wait3sec.play();
                createNew.setDisable(false); //riabilito il pulsante
            });
            wait10sec.play();

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
                wait3sec.setOnFinished(event -> {
                    popupAttesa.setVisible(false);
                    createNew.setDisable(false); // riabilita il pulsante
                });
                wait3sec.play();
            }
        }
    }
}

