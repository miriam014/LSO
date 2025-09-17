package tris.Controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import tris.Main;

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
    public void inizialize() {
        labelResult.setVisible(false);
        replayButton.setVisible(false);
        replayButton.setManaged(false);
    }

    @FXML
    private void handleMove(ActionEvent e) {
        
    }

    public void backHome(ActionEvent actionEvent) {
        try {
            Main.setRoot("home.fxml");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
