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

        // === 1. AGGIORNAMENTO STATO GIOCO ===
        if (msg.startsWith("MOSSA_OK") || msg.startsWith("STATO_PARTITA")) {
            aggiornaScacchiera(msg);
        }

        // === 2. FINE PARTITA (Vittoria/Sconfitta/Abbandono) ===
        else if (msg.startsWith("PARTITA_FINITA")) {
            int idPartita = -1;
            String vincitore = "";
            boolean abbandono = msg.contains("abbandono=true");

            // Parsing dei token
            for (String tok : msg.split("\\s+")) {
                if (tok.startsWith("id_partita=")) {
                    try {
                        idPartita = Integer.parseInt(tok.substring("id_partita=".length()));
                    } catch (NumberFormatException e) { e.printStackTrace(); }
                } else if (tok.startsWith("vincitore=")) {
                    vincitore = tok.substring("vincitore=".length()).trim();
                }
            }

            // Ignora messaggi vecchi o di altre partite
            if (idPartita != getIdPartita()) { return; }

            // --- CASO SPECIALE: L'AVVERSARIO HA ABBANDONATO ---
            if (abbandono) {
                // Mostra alert e poi TORNA ALLA HOME
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Partita terminata");
                    alert.setHeaderText(null);
                    alert.setContentText("Il tuo avversario ha abbandonato la partita. Tornerai alla Home.");
                    alert.showAndWait(); // Blocca finché l'utente non clicca OK

                    // Pulizia e cambio scena
                    Sessione.setIdPartita(0);
                    Sessione.setSonoProprietario(false);
                    try {
                        Main.setRoot("home.fxml");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                return; // Importante: esci per non eseguire il resto
            }

            // --- CASO NORMALE: VITTORIA / PAREGGIO ---
            String me = getUsername();
            if ("pareggio".equalsIgnoreCase(vincitore)) {
                aggiornaLabel("Pareggio!");
            } else if (vincitore.equalsIgnoreCase(me)) {
                aggiornaLabel("Hai vinto!");
            } else {
                aggiornaLabel("Hai perso!");
            }

            // Aggiorna i pulsanti: nascondi abbandona, mostra rigioca
            abandonButton.setVisible(false);
            abandonButton.setManaged(false);
            replayButton.setVisible(true);
            replayButton.setManaged(true);
            trisGrid.setDisable(true);
        }

        // === 3. RICHIESTA DI RIVINCITA (REMATCH) ===
        else if (msg.startsWith("REMATCH_RICHIESTA")) {
            String[] parts = msg.split("\\s+");
            if (parts.length < 3) return;

            String avversario = parts[1];
            int idPartita = Integer.parseInt(parts[2]);

            // Sicurezza: controllo ID
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

                // Se rifiuto, torno alla home
                if (!accetta) {
                    try {
                        Main.setRoot("home.fxml");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // === 4. RISPOSTA ALLA RIVINCITA ===
        else if (msg.startsWith("REMATCH_ESITO")) {
            // Caso Rifiuto: L'altro ha detto no -> torno alla Home
            if (!msg.contains("accetta=true")) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Rivincita rifiutata");
                    alert.setHeaderText(null);
                    alert.setContentText("L'avversario ha rifiutato la rivincita.");
                    alert.showAndWait();
                    try {
                        Main.setRoot("home.fxml");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                return;
            }

            // Caso Accettato: Prendo il NUOVO ID
            int nuovoId = -1;
            String[] parts = msg.split("\\s+");
            for (String p : parts) {
                if (p.startsWith("nuovo_id=")) {
                    try {
                        nuovoId = Integer.parseInt(p.substring("nuovo_id=".length()));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (nuovoId != -1) {
                System.out.println("[Partita] Rematch accettato! Passo al nuovo ID: " + nuovoId);

                // 1. Aggiorno ID Sessione
                Sessione.setIdPartita(nuovoId);

                // 2. Resetto la UI per la nuova partita
                resetBoard();
                labelResult.setVisible(false);

                replayButton.setVisible(false);
                replayButton.setManaged(false);

                abandonButton.setVisible(true);
                abandonButton.setManaged(true);

                trisGrid.setDisable(false);

                // 3. Richiedo lo stato pulito della nuova partita
                Main.getNetClient().send("STATO_PARTITA " + nuovoId);
            }
        }

        // === 5. ERRORI GENERICI ===
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
                // 1. Avvisa il server
                Main.getNetClient().send(MessaggiBuilder.abbandonaPartita(getUsername(), getIdPartita()));

                // 2. Pulisci la sessione locale
                Sessione.setIdPartita(0);
                Sessione.setSonoProprietario(false);

                // 3. TORNA SUBITO ALLA HOME
                try {
                    Main.setRoot("home.fxml");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}