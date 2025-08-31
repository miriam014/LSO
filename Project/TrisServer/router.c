#include "server.h"

// Helpers JSON minimi locali a questo file
static int estrai_stringa_json(const char *js, const char *chiave, char *out, size_t cap){
    char ago[64];
    snprintf(ago, sizeof(ago), "\"%s\"", chiave);
    const char *p = strstr(js, ago);
    if (!p) return 0;
    p = strchr(p, ':'); if (!p) return 0;
    p++; while (*p && isspace((unsigned char)*p)) p++;
    if (*p != '"') return 0;
    p++;
    size_t i=0; while (*p && *p!='"' && i<cap-1) out[i++]=*p++;
    out[i]='\0';
    return 1;
}


static int estrai_intero_json(const char *js, const char *chiave, int *out){
    char ago[64];
    snprintf(ago, sizeof(ago), "\"%s\"", chiave);
    const char *p = strstr(js, ago);
    if (!p) return 0;
    p = strchr(p, ':'); if (!p) return 0;
    p++; while (*p && isspace((unsigned char)*p)) p++;
    *out = atoi(p);
    return 1;
}


static int estrai_bool_json(const char *js, const char *chiave, int *out){
    char tmp[16];
    if (!estrai_stringa_json(js, chiave, tmp, sizeof(tmp))) return 0;
    for(char *q=tmp; *q; ++q) *q=(char)tolower((unsigned char)*q);
    if (!strcmp(tmp,"true") || !strcmp(tmp,"1"))  { *out=1; return 1; }
    if (!strcmp(tmp,"false")|| !strcmp(tmp,"0"))  { *out=0; return 1; }
    return 0;
}


static void uppercase(char *s){ for(;*s;++s) *s=(char)toupper((unsigned char)*s); }

void handle_command(int sock, const char *line){
    char tipo[32]={0}, utente[64]={0}, nome[64]={0}, ospite[64]={0};
    int id=0, cella=-1, accetta=0, voglio=0;

    estrai_stringa_json(line, "tipo",   tipo,   sizeof(tipo));
    estrai_stringa_json(line, "utente", utente, sizeof(utente));
    estrai_stringa_json(line, "nome",   nome,   sizeof(nome));
    estrai_stringa_json(line, "ospite", ospite, sizeof(ospite));
    estrai_intero_json   (line, "id_partita", &id);
    estrai_intero_json   (line, "cella",      &cella);
    estrai_bool_json     (line, "accetta",    &accetta);
    estrai_bool_json     (line, "voglio",     &voglio);

    uppercase(tipo);

    if      (strcmp(tipo,"CIAO")==0 && utente[0])                { cmd_ciao(sock, utente); }
    else if (strcmp(tipo,"CREA_PARTITA")==0 && utente[0])        { cmd_crea_partita(sock, utente, nome[0]?nome:NULL); }
    else if (strcmp(tipo,"LISTA_PARTITE")==0)                    { cmd_lista_partite(sock); }
    else if (strcmp(tipo,"ENTRA_RICHIESTA")==0 && utente[0] && id>0)
                                                                 { cmd_entra_richiesta(sock, utente, id); }
    else if (strcmp(tipo,"ENTRA_RISPOSTA")==0 && utente[0] && id>0)
                                                                 { cmd_entra_risposta(sock, utente, id, accetta, ospite); }
    else if (strcmp(tipo,"MOSSA")==0 && utente[0] && id>0 && cella>=0)
                                                                 { cmd_mossa(sock, utente, id, cella); }
    else if (strcmp(tipo,"REMATCH")==0 && utente[0] && id>0)     { cmd_rematch(sock, utente, id, voglio); }
    else {
        send_line(sock, "{\"tipo\":\"ERRORE\",\"messaggio\":\"Comando non riconosciuto o parametri mancanti\"}");
    }
}
