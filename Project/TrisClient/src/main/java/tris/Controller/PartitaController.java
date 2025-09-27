package tris.Controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import tris.Main;
import tris.MessaggiBuilder;
import tris.Sessione;

public class PartitaController {
    @FXML public Button back;
    @FXML private Label labelResult;
    @FXML private Button replayButton;

    @FXML private Button btn00;
    @FXML private Button btn01;
    @FXML private Button btn02;
    @FXML private Button btn10;
    @FXML private Button btn11;
    @FXML private Button btn12;
    @FXML private Button btn20;
    @FXML private Button btn21;
    @FXML private Button btn22;

    @FXML private GridPane trisGrid;

    private String lastScacchiera = ".........";

    @FXML
    public void initialize() {
        labelResult.setVisible(false);
        replayButton.setVisible(false);
        replayButton.setManaged(false);

        Main.getNetClient().setOnMessage(msg -> {
            Platform.runLater(() -> handleServerMessage(msg.trim()));
        });
    }

    // Mando al server la mossa scelta dall’utente
    @FXML
    private void handleMove(ActionEvent e) {
        
        Button btn = (Button) e.getSource();

        // Se la cella è già occupata, non mando nulla
        if (!btn.getText().isEmpty()) {
            System.out.println("[DEBUG] Cella già occupata, ignoro click");
            return;
        }

        String id = btn.getId(); // es: "btn01"
        int row = Character.getNumericValue(id.charAt(3));
        int col = Character.getNumericValue(id.charAt(4));
        int cella = row * 3 + col;

        String utente = Sessione.getUsername();
        int idPartita = Sessione.getIdPartita();

        System.out.println("[DEBUG] Invio mossa: " + utente + " cella=" + cella);
        Main.getNetClient().send(MessaggiBuilder.mossa(utente, idPartita, cella));
    }

    private void handleServerMessage(String msg) {
        System.out.println("[PartitaController] Ricevuto: " + msg);

        if (msg.startsWith("MOSSA_OK")) {
            aggiornaScacchiera(msg);

        } else if (msg.startsWith("STATO_PARTITA")) {
            // aggiorna subito la scacchiera
            aggiornaScacchiera(msg);

            if (msg.contains("IN_CORSO")) {
                // Confermo che la partita è iniziata
                labelResult.setVisible(false);
                replayButton.setVisible(false);
                replayButton.setManaged(false);

                // Se non hai già salvato l'idPartita, fallo ora
                try {
                    String[] tokens = msg.split("\\s+");
                    int id = Integer.parseInt(tokens[1]);
                    Sessione.setIdPartita(id);
                    System.out.println("[DEBUG] Entrata in partita IN_CORSO con id=" + id);
                } catch (Exception e) {
                    System.out.println("[DEBUG] Non riesco a parsare l'id della partita da: " + msg);
                }

                // 🔹 Forza di nuovo la sincronizzazione del turno al primo STATO_PARTITA
                aggiornaScacchiera(msg);
            }

        } else if (msg.startsWith("PARTITA_FINITA")) {
            String vincitore = "";
            if (msg.contains("vincitore=")) {
                vincitore = msg.split("vincitore=")[1].trim();
            }
            String me = Sessione.getUsername();
            if ("pareggio".equalsIgnoreCase(vincitore)) {
                labelResult.setText("Pareggio!");
            } else if (vincitore.equals(me)) {
                labelResult.setText("Hai vinto!");
            } else {
                labelResult.setText("Hai perso!");
            }
            labelResult.setVisible(true);
            replayButton.setVisible(true);
            replayButton.setManaged(true);
            trisGrid.setDisable(true); // blocco griglia a fine partita

        } else if (msg.startsWith("REMATCH_STATO")) {
            if (msg.contains("pronto_proprietario=true") && msg.contains("pronto_ospite=true")) {
                resetBoard();
                labelResult.setVisible(false);
                replayButton.setVisible(false);
                replayButton.setManaged(false);
                trisGrid.setDisable(false); // nuova partita attiva
            }

        } else if (msg.startsWith("ERRORE")) {
            System.out.println("[Server ERRORE] " + msg);
        }
        else if(msg.startsWith("REMATCH_RICHIESTA")) {
            String[] parts = msg.split("\\s+");
            if (parts.length < 3) return;

            String avversario = parts[1];
            int idPartita = Integer.parseInt(parts[2]);

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Richiesta di rivincita");
            alert.setHeaderText(null);
            alert.setContentText(avversario + " vuole fare una rivincita. Accetti?");
            ButtonType accettaBtn = new ButtonType("Accetta");
            ButtonType rifiutaBtn = new ButtonType("Rifiuta", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(accettaBtn, rifiutaBtn);

            alert.showAndWait().ifPresent(response -> {
                boolean accetta = (response == accettaBtn);
                Main.getNetClient().send(MessaggiBuilder.rematchRisposta(Sessione.getUsername(), idPartita, accetta));
                if (!accetta) {
                    try {
                        Main.setRoot("home.fxml"); // Torna subito alla home
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        else if (msg.startsWith("REMATCH_ESITO")) {
            // Messaggio: REMATCH_ESITO accetta=true/false
            if (msg.contains("accetta=true")) {
                resetBoard();
                labelResult.setVisible(false);
                replayButton.setVisible(false);
                replayButton.setManaged(false);
                trisGrid.setDisable(false); // nuova partita attiva
            } else {
                try {
                    Main.setRoot("home.fxml"); // L’altro ha rifiutato
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
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

        System.out.println("[DEBUG] Aggiorna scacchiera: " + scacchiera + " turno=" + turno);
        // Abilita/disabilita la griglia in base al turno
        if (!turno.isEmpty()) {
            String me = Sessione.getUsername();
            boolean myTurn = turno.equals(me);
            trisGrid.setDisable(!myTurn);
            System.out.println("[DEBUG] È il turno di " + turno + " (io=" + me + ")");
        }

        // aggiorno solo se la scacchiera è cambiata
        if (scacchiera.length() == 9 && !scacchiera.equals(lastScacchiera)) {
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

    @FXML
    private void replayGame() {
        String utente = Sessione.getUsername();
        int idPartita = Sessione.getIdPartita();

        Main.getNetClient().send(MessaggiBuilder.rematchRichiesta(utente, idPartita));
        labelResult.setText("In attesa dell’altro giocatore...");
    }

    public void backHome(ActionEvent actionEvent) {
        try {
            Main.setRoot("home.fxml");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
