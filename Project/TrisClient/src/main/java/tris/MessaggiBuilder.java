package tris;

public class MessaggiBuilder {

    private static String qs(String s){
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static String ciao(String utente){
        return "{\"tipo\":\"CIAO\",\"utente\":\""+qs(utente)+"\"}";
    }

    public static String creaPartita(String utente, String nome){
        return "{\"tipo\":\"CREA_PARTITA\",\"utente\":\""+qs(utente)+"\",\"nome\":\""+qs(nome)+"\"}";
    }

    public static String listaPartite(){
        return "{\"tipo\":\"LISTA_PARTITE\"}";
    }

    public static String entraRichiesta(String utente, int idPartita){
        return "{\"tipo\":\"ENTRA_RICHIESTA\",\"utente\":\""+qs(utente)+"\",\"id_partita\":"+idPartita+"}";
    }

    // NB: booleans come STRINGHE ("true"/"false") per il tuo router attuale
    public static String entraRisposta(String owner, int idPartita, String ospite, boolean accetta){
        return "{\"tipo\":\"ENTRA_RISPOSTA\",\"utente\":\""+qs(owner)+"\",\"id_partita\":"+idPartita+
                ",\"accetta\":\""+(accetta?"true":"false")+"\",\"ospite\":\""+qs(ospite)+"\"}";
    }

    public static String mossa(String utente, int idPartita, int cella){
        return "{\"tipo\":\"MOSSA\",\"utente\":\""+qs(utente)+"\",\"id_partita\":"+idPartita+",\"cella\":"+cella+"}";
    }

    public static String rematch(String utente, int idPartita, boolean voglio){
        return "{\"tipo\":\"REMATCH\",\"utente\":\""+qs(utente)+"\",\"id_partita\":"+idPartita+
                ",\"voglio\":\""+(voglio? "true":"false")+"\"}";
    }
}
