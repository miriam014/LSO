package tris.Controller;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
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
    @FXML private MenuButton partiteDisponibili;
    @FXML public TableColumn<PartitaRow, String> columAvversario;
    @FXML public TableColumn<PartitaRow, String> columStato;
    @FXML public TableColumn<PartitaRow, String> coulmRisultato;
    @FXML public TableColumn<PartitaRow, Void> columAzione;
    @FXML private TableView<PartitaRow> tablePartite;

    // Timer
    private PauseTransition waitNoOpponent; // timeout 40s in attesa avversario
    private PauseTransition hidePopupDelay; // 3s per chiudere messaggi
    private boolean navigated = false;

    @FXML
    public void initialize() {
        initializeTableView();
        Main.getNetClient().send(MessaggiBuilder.miePartite());

        InsertName.textProperty().addListener((obs, vecchioValore, nuovoValore) -> {
            Sessione.setUsername(nuovoValore.trim());
        });
        Main.getNetClient().setOnMessage(msg ->
                Platform.runLater(() -> handleServerMessage(msg))
        );
        partiteDisponibili.setOnMouseClicked(   event -> {
            Main.getNetClient().send(MessaggiBuilder.listaAttesa());
        });
        Platform.runLater(() -> {
            Main.getNetClient().send(MessaggiBuilder.listaAttesa());
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
        InsertName.setDisable(true);
        partiteDisponibili.setDisable(true);
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
                partiteDisponibili.setDisable(false);
                Main.getNetClient().send(MessaggiBuilder.annullaPartita(Sessione.getIdPartita()));
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
            String[] parts = msg.split("\\s+");
            for (String p : parts) {
                if (p.startsWith("id=")) {
                    try { Sessione.setIdPartita(Integer.parseInt(p.substring(3))); } catch (Exception ignore) {}
                }
            }

        } else if (msg.startsWith("LISTA_ATTESA")) {
            partiteDisponibili.getItems().clear();
            String[] righe = msg.split("\n"); // per riga siccome i messaggi vengono separati per riga dal server
            String me = Sessione.getUsername() == null ? "" : Sessione.getUsername();

            for (int i = 1; i < righe.length; i++) { // salto la prima riga "LISTA_ATTESA"
                String riga = righe[i].trim();
                if (riga.isEmpty()) continue;

                String[] tokens = riga.split("\\s+");
                int idPartita = Integer.parseInt(tokens[0]);
                String proprietario = tokens[2].split("=", 2)[1];

                MenuItem item = new MenuItem("Unisciti alla partita di " + proprietario);
                item.setOnAction(e -> {
                    String utente = InsertName.getText().trim().replace(" ", "_");
                    if (utente.isEmpty()) {
                        popupLabel.setText("Inserisci prima nome utente.");
                        popupAttesa.setVisible(true);
                        return;
                    }
                    Sessione.setUsername(utente);
                    Main.getNetClient().send(MessaggiBuilder.entraRichiesta(utente, idPartita));
                    popupLabel.setText("Richiesta inviata. Attendi la risposta del proprietario...");
                    popupAttesa.setVisible(true);
                    partiteDisponibili.setDisable(true);
                });
                partiteDisponibili.getItems().add(item);
            }
            if (partiteDisponibili.getItems().isEmpty()) {
                partiteDisponibili.getItems().add(new MenuItem("nessuna partita disponibile"));
            }

        } else if (msg.startsWith("MIE_PARTITE")) {
            ObservableList<PartitaRow> items = tablePartite.getItems();
            items.clear();

            String[] righe = msg.split("\n");
            for (int i = 1; i < righe.length; i++) {
                String riga = righe[i].trim();
                if (riga.isEmpty()) continue;

                String[] tokens = riga.split("\\s+");
                int idPartita = Integer.parseInt(tokens[0]);
                String stato = tokens[1];
                String proprietario = tokens[2].split("=")[1];
                String ospite = tokens[3].split("=")[1];
                String me = Sessione.getUsername();
                String avversario = me.equals(proprietario) ? ospite : proprietario;

                String risultato = "-";
                if ("TERMINATA".equals(stato)) {
                    for (String t : tokens) {
                        if (t.startsWith("vinc=")) {
                            String vinc = t.substring("vinc=".length());
                            if ("=".equals(vinc)) risultato = "Pareggio";
                            else if (vinc.equals(me)) risultato = "Hai vinto";
                            else risultato = "Hai perso";
                        }
                    }
                }
                items.add(new PartitaRow(idPartita, avversario, stato, risultato));
            }
            System.out.println("[DEBUG] Dopo parsing, table size: " + tablePartite.getItems().size());
            tablePartite.refresh();

        } else if (msg.startsWith("ENTRA_RICHIESTA_INVIATA")) {
            popupLabel.setText("Richiesta inviata. Attendi la risposta del proprietario...");
            popupAttesa.setVisible(true);
            partiteDisponibili.setDisable(true);
            createNew.setDisable(true);

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
                Main.getNetClient().send(MessaggiBuilder.miePartite());
                try{ Main.setRoot("partita.fxml"); } catch (Exception e){ e.printStackTrace(); }
            } else {
                popupLabel.setText("Richiesta rifiutata dal proprietario.");
                popupAttesa.setVisible(true);

                PauseTransition wait = new PauseTransition(Duration.seconds(3));
                wait.setOnFinished(event -> {
                    popupAttesa.setVisible(false);
                    createNew.setDisable(false);
                    partiteDisponibili.setDisable(false);
                });
                wait.play();
            }
        }
        else if (msg.startsWith("AVVERSARIO_DISCONNESSO")) {
            String[] parts = msg.split("\\s+");
            int idPartita = Integer.parseInt(parts[1].split("=")[1]);
            popupLabel.setText("Il tuo avversario si è disconnesso (partita " + idPartita + ").");
            popupAttesa.setVisible(true);

            // aggiorna tabella -> stato TERMINATA
            int idx = -1;
            for (int k = 0; k < tablePartite.getItems().size(); k++) {
                if (tablePartite.getItems().get(k).getId() == idPartita) {
                    idx = k; break;
                }
            }
            if (idx >= 0) {
                PartitaRow old = tablePartite.getItems().get(idx);
                tablePartite.getItems().set(idx, new PartitaRow(idPartita, old.getAvversario(), "Terminata", "Avversario disconnesso"));
            } else {
                tablePartite.getItems().add(new PartitaRow(idPartita, "?", "TERMINATA", "Avversario disconnesso"));
            }

        } else if (msg.startsWith("STATO_PARTITA")) {
            String[] tokens = msg.split("\\s+");
            if (tokens.length < 7) return;

            int idPartita = Integer.parseInt(tokens[1]);
            String stato = tokens[2];
            String proprietario = tokens[5].split("=")[1];
            String ospite = tokens[6].split("=")[1];
            String me = Sessione.getUsername();
            String avversario = me.equals(proprietario) ? ospite : proprietario;

            int idx = -1;
            for (int k = 0; k < tablePartite.getItems().size(); k++) {
                if (tablePartite.getItems().get(k).getId() == idPartita) { idx = k; break; }
            }

            PartitaRow row = new PartitaRow(idPartita, avversario, stato, "-");
            if (idx >= 0) tablePartite.getItems().set(idx, row);
            else tablePartite.getItems().add(row);
        }
        createNew.setDisable(false);
        partiteDisponibili.setDisable(false);
    }

    private void initializeTableView() {
        tablePartite.setItems(FXCollections.observableArrayList());
        tablePartite.setPlaceholder(new Label("Nessuna partita"));

        columAvversario.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue()!= null ? c.getValue().getAvversario() : ""));
        columStato.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue()!= null ? c.getValue().getStato() : ""));
        coulmRisultato.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue()!= null ? c.getValue().getRisultato() : ""));

        columAzione.setCellFactory(col -> new TableCell<PartitaRow, Void>() {
            private final Button actionButton = new Button("Continua");
            {
                actionButton.setOnAction(e -> {
                    PartitaRow partita = getTableView().getItems().get(getIndex());
                    System.out.println("[DEBUG] Bottone premuto per partita ID=" + partita.getId());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(actionButton);
                }
            }
        });
    }

    public static final class PartitaRow {
        private final int id;
        private final String avversario;
        private final String stato;
        private final String risultato;
        public PartitaRow(int id, String avversario, String stato, String risultato) {
            this.id = id; this.avversario = avversario; this.stato = stato; this.risultato = risultato;
        }
        public int getId() { return id; }
        public String getAvversario() { return avversario; }
        public String getStato() { return stato; }
        public String getRisultato() { return risultato; }
    }
}

