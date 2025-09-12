package tris;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    private static Stage primaryStage;
    private static NetClient netClient;

    // Avvio dell'applicazione JavaFX
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        netClient = new NetClient("localhost", 5001);
        netClient.start();
        setRoot("home.fxml");
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (netClient != null) {
            netClient.close();
        }
    }

    public static void setRoot(String fxml) throws Exception {
        Parent root = FXMLLoader.load(Main.class.getResource("/interfaccia/" + fxml));
        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("Tris");
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    public static NetClient getNetClient() {
        return netClient;
    }
}

