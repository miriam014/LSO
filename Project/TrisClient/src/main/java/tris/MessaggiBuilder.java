package tris;

import com.google.gson.JsonObject;

public class MessaggiBuilder {

    public static String costruisci(String comando) {
        JsonObject json = new JsonObject();

        switch (comando.toLowerCase()) {

            case "crea":
                json.addProperty("type", "crea_partita");
                json.addProperty("giocatore", Sessione.getUsername());
                break;

            case "accetta":
                json.addProperty("type", "accetta_partecipazione");
                json.addProperty("id_partita", Sessione.getIdPartita());
                break;

            case "rifiuta":
                json.addProperty("type", "rifiuta_partecipazione");
                json.addProperty("id_partita", Sessione.getIdPartita());
                break;

            case "inizia_nuova":
                json.addProperty("type", "inizia_nuova");
                json.addProperty("id_partita", Sessione.getIdPartita());
                json.addProperty("giocatore", Sessione.getUsername());
                break;

            default:
                return null;
        }

        return json.toString();
    }
}
