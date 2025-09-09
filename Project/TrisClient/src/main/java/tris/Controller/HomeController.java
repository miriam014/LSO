package tris.Controller;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.util.Duration;
import tris.Main;
import tris.MessaggiBuilder;
import tris.Sessione;

public class HomeController {
    @FXML private Button createNew;
    @FXML private TextField InsertName;
    @FXML private Label popupLabel;
    @FXML private VBox popupAttesa;
   // @FXML private TableView<Partita> tablePartiteInCorso;
    @FXML private MenuButton partiteDisponibili;

    // Timer per attese/timeout
    private PauseTransition waitNoOpponent; // timeout 40s in attesa avversario (owner)
    private PauseTransition hidePopupDelay; // 3s per chiudere messaggi informativi

    // Evita doppi cambi scena
    private boolean navigated = false;

    @FXML
    public void initialize() {
        //initializeTableView();
        InsertName.textProperty().addListener((obs, vecchioValore, nuovoValore) -> {
            Sessione.setUsername(nuovoValore.trim());
        });
        Main.getNetClient().setOnMessage(msg ->
                Platform.runLater(() -> handleServerMessage(msg))
        );
        partiteDisponibili.setOnMouseClicked(   event -> {
            Main.getNetClient().send(MessaggiBuilder.listaPartite());
        });
    }

    @FXML
    private void CreateNew() {
        String nomeUtente = InsertName.getText().trim();
        if (nomeUtente.isEmpty()) {
            popupLabel.setText("Inserisci prima un nome utente.");
            popupAttesa.setVisible(true);
            return;
        }
        Sessione.setUsername(nomeUtente);
        createNew.setDisable(true);
        partiteDisponibili.setDisable(true);
        // disabilito il bottone per evitare che il client invia più richieste prima di ricevere almeno una risposta
        Main.getNetClient().send(MessaggiBuilder.creaPartita(nomeUtente));
        popupLabel.setText("In attesa di un avversario...");
        popupAttesa.setVisible(true);

        // Timeout 40s: se nessuno entra, messaggio e chiudi popup
        if (waitNoOpponent != null) { waitNoOpponent.stop(); waitNoOpponent = null; }
        if (hidePopupDelay != null) { hidePopupDelay.stop(); hidePopupDelay = null; }
        waitNoOpponent = new PauseTransition(Duration.seconds(20));
        waitNoOpponent.setOnFinished(ev -> {
            popupLabel.setText("Non ci sono giocatori disponibili al momento.");
            hidePopupDelay = new PauseTransition(Duration.seconds(4));
            hidePopupDelay.setOnFinished(e -> {
                popupAttesa.setVisible(false);
                createNew.setDisable(false);
            });
            hidePopupDelay.play();
        });
        waitNoOpponent.play();
    }

    private void handleServerMessage(String msg) {
        msg = msg.trim();
        System.out.println("[DEBUG] Ricevuto: " + msg);

        if (msg.startsWith("ATTESA_AVVERSARIO")) {
            popupLabel.setText("In attesa di un avversario...");
            popupAttesa.setVisible(true);

        } else if (msg.startsWith("LISTA_PARTITE")) {
            //tablePartiteInCorso.getItems().clear();
            partiteDisponibili.getItems().clear();
            String[] righe = msg.split("\n"); // per riga siccome i messaggi vengono separati per riga dal server

            for (int i = 1; i < righe.length; i++) { // salto la prima riga "LISTA_PARTITE"
                String riga = righe[i].trim();
                if (riga.isEmpty()) continue;

                // riga = "1 IN_ATTESA proprietario=Alice ospite=-"
                String[] tokens = riga.split("\\s+");
                if (tokens.length < 2) continue;

                int idPartita;
                try { idPartita = Integer.parseInt(tokens[0]); }
                catch (NumberFormatException e) { continue; }

                String stato = tokens[1];
                String proprietario = tokens[2].split("=",2)[1];
                String ospite = tokens[3].split("=",2)[1];
                if (proprietario == null) proprietario = "?";
                if (ospite == null) ospite = "-";

                if ("IN_ATTESA".equals(stato) && (ospite.equals("-") || ospite.isEmpty())) {
                    final int idPartitaFinal = idPartita;
                    if (proprietario.equals(Sessione.getUsername())) { continue; }
                    MenuItem item = new MenuItem("Unisciti alla partita di " + proprietario);
                    item.setOnAction(e -> {
                        String utente = InsertName.getText().trim().replace(" ", "_");
                        if (utente.isEmpty()) {
                            popupLabel.setText("Inserisci prima nome utente.");
                            popupAttesa.setVisible(true);
                            return;
                        }
                        Sessione.setUsername(utente);
                        System.out.println("[DEBUG] Invio ENTRA_RICHIESTA per partita " + idPartitaFinal);
                        Main.getNetClient().send(MessaggiBuilder.entraRichiesta(utente, idPartitaFinal));
                        popupLabel.setText("Richiesta inviata. Attendi la risposta del proprietario...");
                        popupAttesa.setVisible(true);
                    });
                    partiteDisponibili.getItems().add(item);
                }
                if (partiteDisponibili.isShowing()) {
                    partiteDisponibili.hide();
                    partiteDisponibili.show();
                }
            }
            if (partiteDisponibili.getItems().isEmpty()) {
                partiteDisponibili.getItems().add(new MenuItem("nessuna partita disponibile"));
            } else { partiteDisponibili.show(); }
            System.out.println("Items nel menu: " + partiteDisponibili.getItems().size());

        } else if (msg.startsWith("ENTRA_RICHIESTA_INVIATA")) {
            popupLabel.setText("Richiesta inviata. Attendi la risposta del proprietario...");
            popupAttesa.setVisible(true);

        } else if (msg.startsWith("ENTRA_RICHIESTA")) {
            if (waitNoOpponent != null) { waitNoOpponent.stop(); waitNoOpponent = null; }
            if (hidePopupDelay != null) { hidePopupDelay.stop(); hidePopupDelay = null; }
            String[] parts = msg.split("\\s+");
            if (parts.length < 3) return;

            String ospite = parts[1];
            int idPartita = Integer.parseInt(parts[2]);

            // Finestra di conferma
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Richiesta di partecipazione");
            alert.setHeaderText(null);
            alert.setContentText(ospite + " vuole unirsi alla tua partita.");
            ButtonType accettaBtn = new ButtonType("Accetta");
            ButtonType rifiutaBtn = new ButtonType("Rifiuta", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(accettaBtn, rifiutaBtn);

            alert.showAndWait().ifPresent(response -> {
                boolean accetta = (response == accettaBtn);
                Main.getNetClient().send(
                        MessaggiBuilder.entraRisposta(Sessione.getUsername(), idPartita, ospite, accetta)
                );
                if (accetta) {
                    Sessione.setIdPartita(idPartita);
                    if (!navigated) {
                        navigated = true;
                        popupAttesa.setVisible(false);
                        try { Main.setRoot("partita.fxml"); } catch (Exception e) { e.printStackTrace(); }
                    }
                }
            });
        } else if (msg.startsWith("ENTRA_ESITO")) {
            if (waitNoOpponent != null) { waitNoOpponent.stop(); waitNoOpponent = null; }
            if (hidePopupDelay != null) { hidePopupDelay.stop(); hidePopupDelay = null; }
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
                    createNew.setDisable(false);
                });
                wait3sec.play();
            }
        }
    }

   /* private void initializeTableView() {
        TableColumn<Partita, Number> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getId()));

        TableColumn<Partita, String> proprietarioCol = new TableColumn<>("Proprietario");
        proprietarioCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProprietario()));

        TableColumn<Partita, String> ospiteCol = new TableColumn<>("Ospite");
        ospiteCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getOspite()));

        tableView.getColumns().addAll(idCol, proprietarioCol, ospiteCol);
    }*/
}

