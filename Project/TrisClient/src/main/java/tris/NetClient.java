package tris;
import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;


//È il codice che apre la socket, legge linee dal server in un thread separato e chiama un Consumer<String> quando arriva una linea.
// I controller faranno Platform.runLater(...) per aggiornare l’UI.

public class NetClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread readerThread;
    private Consumer<String> onMessage; // viene chiamato sul thread di lettura

    public NetClient(String host, int port) throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    public void start() {
        readerThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (onMessage != null) onMessage.accept(line);
                }
            } catch (IOException e) {
                if (onMessage != null) onMessage.accept("{\"tipo\":\"ERROR\",\"msg\":\"" + e.getMessage() + "\"}");
            }
        }, "NetClient-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void send(String msg) {
        out.println(msg);
    }

    public void setOnMessage(Consumer<String> onMessage) {
        this.onMessage = onMessage;
    }

    public void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }
}
