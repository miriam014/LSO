package tris;

public class MessaggiBuilder {

    public static String creaPartita(String utente){
        return "CREA_PARTITA " + utente;
    }

    public static String listaAttesa(){
        return "LISTA_ATTESA";
    }

    public static String miePartite(){
        return "MIE_PARTITE";
    }

    public static String annullaPartita(Integer idPartita){
        return "ANNULLA_PARTITA " + idPartita;
    }

    public static String entraRichiesta(String utente, int idPartita){
        return "ENTRA_RICHIESTA " + utente + " " + idPartita;
    }

    // NB: booleans come STRINGHE ("true"/"false") per il tuo router attuale
    public static String entraRisposta(String owner, int idPartita, String ospite, boolean accetta){
        return "ENTRA_RISPOSTA " + owner + " " + idPartita + " " + (accetta?1:0) + " " + ospite;
    }

    public static String mossa(String utente, int idPartita, int cella){
        return "MOSSA " + utente + " " + idPartita + " " + cella;
    }

    public static String rematch(String utente, int idPartita, boolean voglio){
        return "REMATCH " + utente + " " + idPartita + " " + (voglio? "1":"0");
    }

    public static String rematchRichiesta(String utente, int idPartita) {
        return "REMATCH_RICHIESTA " + utente + " " + idPartita;
    }

    public static String rematchRisposta(String utente, int idPartita, boolean accetta) {
        return "REMATCH_RISPOSTA " + utente + " " + idPartita + " " + (accetta ? 1 : 0);
    }

}
