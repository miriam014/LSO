package tris.Controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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


    @FXML
    public void initialize() {
        labelResult.setVisible(false);
        replayButton.setVisible(false);
        replayButton.setManaged(false);

        Main.getNetClient().setOnMessage(msg -> {
            Platform.runLater(() -> handleServerMessage(msg));
        });
    }

    //Questo metodo serve a mandare al server la mossa scelta dall’utente
    @FXML
    private void handleMove(ActionEvent e) {
        Button btn = (Button) e.getSource();
        String id = btn.getId();
        int row = Character.getNumericValue(id.charAt(3));
        int col = Character.getNumericValue(id.charAt(4));
        int cella = row * 3 + col;

        String utente = Sessione.getUsername();
        int idPartita = Sessione.getIdPartita();

        Main.getNetClient().send(MessaggiBuilder.mossa(utente, idPartita, cella));
    }

    private void handleServerMessage(String msg) {
        System.out.println("[PartitaController] Ricevuto: " + msg);

        if(msg.startsWith("MOSSA_OK")) {
            aggiornaScacchiera(msg);

        } else if (msg.startsWith("PARTITA_FINITA")) {
            String vincitore = msg.split("vincitore=")[1];
            String me = Sessione.getUsername();
            if ("pareggio" .equals(vincitore)) {
                labelResult.setText("Pareggio!");
            } else if (vincitore.equals(me)) {
                labelResult.setText("Hai vinto!");
            } else {
                labelResult.setText("Hai perso!");
            }
            labelResult.setVisible(true);
            replayButton.setVisible(true);
            replayButton.setManaged(true);

        } else if (msg.startsWith("REMATCH_STATO")) {
            if(msg.contains("true true")) {
                resetBoard();
                labelResult.setVisible(false);
                replayButton.setVisible(false);
                replayButton.setManaged(false);
            }
        }
    }

    private void resetBoard() {
        btn00.setText(""); btn01.setText(""); btn02.setText("");
        btn10.setText(""); btn11.setText(""); btn12.setText("");
        btn20.setText(""); btn21.setText(""); btn22.setText("");
    }


    private void aggiornaScacchiera(String msg) {
        // es: MOSSA_OK partita=1 scacchiera=XO..O.... prossimo_turno=Mario
        String[] tokens = msg.split("\\s+"); //divide la stringa in pezzi separati da spazi bianchi
        String scacchiera = "";
        for (String t : tokens) {
            if (t.startsWith("scacchiera=")) {
                scacchiera = t.substring("scacchiera=".length());
            }
        }

        if (scacchiera.length() == 9) {
            btn00.setText(charToText(scacchiera.charAt(0)));
            btn01.setText(charToText(scacchiera.charAt(1)));
            btn02.setText(charToText(scacchiera.charAt(2)));
            btn10.setText(charToText(scacchiera.charAt(3)));
            btn11.setText(charToText(scacchiera.charAt(4)));
            btn12.setText(charToText(scacchiera.charAt(5)));
            btn20.setText(charToText(scacchiera.charAt(6)));
            btn21.setText(charToText(scacchiera.charAt(7)));
            btn22.setText(charToText(scacchiera.charAt(8)));
        }
    }

    private String charToText(char c) {
        return (c == '.' ? "" : String.valueOf(c));
    }

    @FXML
    private void replayGame() {
        String utente = Sessione.getUsername();
        int idPartita = Sessione.getIdPartita();
        Main.getNetClient().send(MessaggiBuilder.rematch(utente, idPartita, true));

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
