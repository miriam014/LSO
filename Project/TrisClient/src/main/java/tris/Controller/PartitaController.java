package tris.Controller;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import tris.Main;

public class PartitaController {
    public Button back;

    public void backHome(ActionEvent actionEvent) {
        try {
            Main.setRoot("home.fxml");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
