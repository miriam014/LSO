package tris;

public class Sessione {
    private static String username;
    private static int idPartita;
    private static boolean sonoProprietario;

    public static void setUsername(String nome) {
        username = nome;
    }

    public static String getUsername() {
        return username;
    }

    public static void setIdPartita(int id) {
        idPartita = id;
    }

    public static int getIdPartita() {
        return idPartita;
    }

    public static boolean isSonoProprietario() {
        return sonoProprietario;
    }

    public static void setSonoProprietario(boolean v) {
        sonoProprietario = v;
    }
}
