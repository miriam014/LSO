package tris.Controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import tris.Main;
import tris.MessaggiBuilder;
import tris.Sessione;

import static tris.Sessione.getIdPartita;
import static tris.Sessione.getUsername;

public class PartitaController {
    @FXML public Button back;
    @FXML private Label labelResult;
    @FXML private Button replayButton;

    @FXML private Button btn00, btn01, btn02,
                         btn10, btn11, btn12,
                         btn20, btn21, btn22;
    @FXML private GridPane trisGrid;
    @FXML private Button abandonButton;

    private String lastScacchiera = ".........";

    @FXML
    public void initialize() {
        labelResult.setVisible(false);
        labelResult.setManaged(true);
        replayButton.setVisible(false);
        replayButton.setManaged(false);

        boolean owner = Sessione.isSonoProprietario();
        if (!owner) {
            aggiornaLabel("In attesa che il proprietario inizi la partita...");
            trisGrid.setDisable(true);
        }

        Main.getNetClient().setOnMessage(msg -> {
            Platform.runLater(() -> handleServerMessage(msg.trim()));
        });
        Main.getNetClient().send("STATO_PARTITA " + getIdPartita());
    }

    @FXML
    private void handleMove(ActionEvent e) {
        Button btn = (Button) e.getSource();

        // Se la cella è già occupata, non mando nulla
        if (!btn.getText().isEmpty()) { return; }

        String id = btn.getId(); // es: "btn01"
        int row = Character.getNumericValue(id.charAt(3));
        int col = Character.getNumericValue(id.charAt(4));
        int cella = row * 3 + col;

        String utente = getUsername();
        int idPartita = getIdPartita();

        System.out.println("[DEBUG] Invio mossa: " + utente + " cella=" + cella);
        Main.getNetClient().send(MessaggiBuilder.mossa(utente, idPartita, cella));
    }

    private void handleServerMessage(String msg) {
        System.out.println("[PartitaController] Ricevuto: " + msg);

        // === MOSSA_OK e STATO_PARTITA ===
        if (msg.startsWith("MOSSA_OK") || msg.startsWith("STATO_PARTITA")) {
            aggiornaScacchiera(msg);
        }

        // === PARTITA_FINITA ===
        else if (msg.startsWith("PARTITA_FINITA")) {
            int idPartita = -1;
            String vincitore = "";
            boolean abbandono = msg.contains("abbandono=true");

            // Estrai id partita e vincitore
            for (String tok : msg.split("\\s+")) {
                if (tok.startsWith("id_partita=")) {
                    idPartita = Integer.parseInt(tok.substring("id_partita=".length()));
                } else if (tok.startsWith("vincitore=")) {
                    vincitore = tok.substring("vincitore=".length()).trim();
                }
            }

            // Ignora messaggi di altre partite
            if (idPartita != getIdPartita()) { return; }

            String me = getUsername();

            // Caso speciale: abbandono
            if (abbandono) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Partita terminata");
                alert.setHeaderText(null);
                alert.setContentText("Il tuo avversario ha abbandonato la partita.");
                alert.getButtonTypes().setAll(new ButtonType("OK", ButtonBar.ButtonData.OK_DONE));

                alert.showAndWait();
                aggiornaLabel ("il tuo avversario ha abbandonato la partita.");
            }// Mostra il risultato
            else if ("pareggio".equalsIgnoreCase(vincitore)) {
                aggiornaLabel("Pareggio!");
            } else if (vincitore.equalsIgnoreCase(me)) {
                aggiornaLabel("Hai vinto!");
            } else {
                aggiornaLabel("Hai perso!");
            }

            abandonButton.setVisible(false);
            abandonButton.setManaged(false);
            replayButton.setVisible(true);
            replayButton.setManaged(true);
            trisGrid.setDisable(true);
        }

        // === REMATCH_RICHIESTA ===
        else if (msg.startsWith("REMATCH_RICHIESTA")) {
            String[] parts = msg.split("\\s+");
            if (parts.length < 3) return;

            String avversario = parts[1];
            int idPartita = Integer.parseInt(parts[2]);
            if (idPartita != getIdPartita()) { return; }

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Richiesta di rivincita");
            alert.setHeaderText(null);
            alert.setContentText(avversario + " vuole fare una rivincita. Accetti?");
            ButtonType accettaBtn = new ButtonType("Accetta");
            ButtonType rifiutaBtn = new ButtonType("Rifiuta", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(accettaBtn, rifiutaBtn);

            alert.showAndWait().ifPresent(response -> {
                boolean accetta = (response == accettaBtn);
                Main.getNetClient().send(MessaggiBuilder.rematchRisposta(getUsername(), idPartita, accetta));
                if (!accetta) {
                    try {
                        Main.setRoot("home.fxml");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // === REMATCH_ESITO ===
        else if (msg.startsWith("REMATCH_ESITO")) {
            if (!msg.contains("accetta=true")) {
                try {
                    Main.setRoot("home.fxml");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }

            // trova il nuovo id partita direttamente dal prossimo STATO_PARTITA che il server manderà
            // (invece di leggerlo da MIE_PARTITE, così è sempre aggiornato)
            Main.getNetClient().setOnMessage(msgRematch -> {
                Platform.runLater(() -> {
                    if (msgRematch.startsWith("STATO_PARTITA")) {
                        String[] tokens = msgRematch.split("\\s+");
                        if (tokens.length > 1) {
                            try {
                                int nuovoId = Integer.parseInt(tokens[1]);
                                Sessione.setIdPartita(nuovoId);

                                // riporta il listener normale
                                Main.getNetClient().setOnMessage(innerMsg ->
                                        Platform.runLater(() -> handleServerMessage(innerMsg.trim()))
                                );

                                // aggiorna subito la UI
                                resetBoard();
                                labelResult.setVisible(false);
                                replayButton.setVisible(false);
                                replayButton.setManaged(false);
                                trisGrid.setDisable(false);
                                abandonButton.setVisible(true);
                                abandonButton.setManaged(true);

                                // forza il recupero stato
                                Main.getNetClient().send("STATO_PARTITA " + nuovoId);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                });
            });

            // chiede al server di inviare subito lo stato per la nuova partita
            Main.getNetClient().send(MessaggiBuilder.miePartite());
        }

        // === ERRORI ===
        else if (msg.startsWith("ERRORE")) {
            System.out.println("[Server ERRORE] " + msg);
        }
    }


    private void resetBoard() {
        btn00.setText(""); btn01.setText(""); btn02.setText("");
        btn10.setText(""); btn11.setText(""); btn12.setText("");
        btn20.setText(""); btn21.setText(""); btn22.setText("");
    }

    private void aggiornaScacchiera(String msg) {
        String[] tokens = msg.split("\\s+");
        String scacchiera = "";
        String turno = "";

        for (String t : tokens) {
            if (t.startsWith("scacchiera=")) {
                scacchiera = t.substring("scacchiera=".length());
            } else if (t.matches("[XO.]{9}")) {
                // caso STATO_PARTITA: scacchiera senza prefisso
                scacchiera = t;
            }

            if (t.startsWith("prossimo_turno=")) {
                turno = t.substring("prossimo_turno=".length());
            } else if (t.startsWith("turno=")) {
                turno = t.substring("turno=".length());
            }
        }

        // Abilita/disabilita la griglia in base al turno
        String me = getUsername();
        boolean myTurn = turno.equals(me);
        boolean idValido = getIdPartita() > 0;

        trisGrid.setDisable(!(myTurn && idValido));
        // Mostra “In attesa…” solo se la partita è ancora in corso
        // Aggiorna label durante il gioco
        if (scacchiera.length() == 9) {
            if (!trisGrid.isDisabled()) {
                aggiornaLabel("È il tuo turno");
            } else {
                aggiornaLabel("In attesa dell’avversario...");
            }

            // aggiorno solo se la scacchiera è cambiata
            btn00.setText(charToText(scacchiera.charAt(0)));
            btn01.setText(charToText(scacchiera.charAt(1)));
            btn02.setText(charToText(scacchiera.charAt(2)));
            btn10.setText(charToText(scacchiera.charAt(3)));
            btn11.setText(charToText(scacchiera.charAt(4)));
            btn12.setText(charToText(scacchiera.charAt(5)));
            btn20.setText(charToText(scacchiera.charAt(6)));
            btn21.setText(charToText(scacchiera.charAt(7)));
            btn22.setText(charToText(scacchiera.charAt(8)));

            lastScacchiera = scacchiera; // aggiorno la memoria
        }
    }


    private String charToText(char c) {
        return (c == '.' ? "" : String.valueOf(c));
    }

    private void aggiornaLabel(String msg) {
        labelResult.setText(msg);
        labelResult.setVisible(true);
        labelResult.setManaged(true);
    }

    @FXML
    private void replayGame() {
        Main.getNetClient().send(MessaggiBuilder.rematchRichiesta(getUsername(), getIdPartita()));
        labelResult.setText("In attesa dell’altro giocatore...");
    }

    @FXML
    public void backHome(ActionEvent actionEvent) {
        try {
            if (replayButton.isVisible()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setHeaderText(null);
                alert.setContentText("Sei sicuro di voler tornare alla Home? Continuare?");
                ButtonType okBtn = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
                ButtonType cancelBtn = new ButtonType("Annulla", ButtonBar.ButtonData.CANCEL_CLOSE);
                alert.getButtonTypes().setAll(okBtn, cancelBtn);

                alert.showAndWait().ifPresent(response -> {
                    if (response == okBtn) {
                        // Notifica al server che il giocatore ha abbandonato
                        Main.getNetClient().send(MessaggiBuilder.abbandonaPartita(getUsername(), getIdPartita()));
                        // Azzera l'id partita locale
                        Sessione.setIdPartita(0);

                        try {
                            Main.setRoot("home.fxml");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            } else {
                Main.getNetClient().send(MessaggiBuilder.miePartite()); // aggiorna lista prima di tornare
                Main.setRoot("home.fxml");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    public void abandonGame(ActionEvent actionEvent) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Conferma abbandono");
        alert.setHeaderText(null);
        alert.setContentText("Sei sicuro di voler abbandonare la partita?");
        ButtonType siBtn = new ButtonType("Sì", ButtonBar.ButtonData.YES);
        ButtonType noBtn = new ButtonType("No", ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(siBtn, noBtn);

        alert.showAndWait().ifPresent(response -> {
            if (response == siBtn) {
                Main.getNetClient().send(MessaggiBuilder.abbandonaPartita(getUsername(), getIdPartita()));

                trisGrid.setVisible(false);
                abandonButton.setVisible(false);
                abandonButton.setManaged(false);

                replayButton.setVisible(false);
                replayButton.setManaged(false);

                aggiornaLabel("Hai abbandonato la partita.");
            }
        });
    }

}
