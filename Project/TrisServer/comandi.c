#include "server.h"

static Utente  g_utenti[MAX_CLIENTS];
static Partita g_partite[MAX_PARTITE];
static int     g_prossimoId = 1;
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;

/* == API usate da server.c == */
Utente* trova_utente_per_sock(int sock){
    for (int i=0;i<MAX_CLIENTS;i++)
        if (g_utenti[i].attivo && g_utenti[i].sock==sock) return &g_utenti[i];
    return NULL;
}

/* == Helpers locali == */
static Utente* trova_utente_per_nome(const char *nome){
    for (int i=0;i<MAX_CLIENTS;i++)
        if (g_utenti[i].attivo && strcmp(g_utenti[i].username, nome)==0) return &g_utenti[i];
    return NULL;
}
static Partita* trova_partita(int id){
    for (int i=0;i<MAX_PARTITE;i++)
        if (g_partite[i].id==id) return &g_partite[i];
    return NULL;
}
static const char* stato_to_str(StatoPartita s){
    switch(s){
        case ST_NUOVA:     return "NUOVA";
        case ST_IN_ATTESA: return "IN_ATTESA";
        case ST_IN_CORSO:  return "IN_CORSO";
        case ST_TERMINATA: return "TERMINATA";
        default:           return "SCONOSCIUTO";
    }
}

/* == I/O a riga == */
ssize_t send_line(int sock, const char *s){
    size_t n=strlen(s);
    if (write(sock,s,n)<0) return -1;
    if (write(sock,"\n",1)<0) return -1;
    return (ssize_t)(n+1);
}

/* == Broadcast / Stato == */
void invia_a_tutti(const char *msg, int escludi_sock){
    for (int i=0;i<MAX_CLIENTS;i++)
        if (g_utenti[i].attivo && g_utenti[i].sock!=escludi_sock) send_line(g_utenti[i].sock, msg);
}
static void invia_ai_giocatori(Partita *p, const char *msg){
    Utente *a = trova_utente_per_nome(p->proprietario);
    Utente *b = (p->ospite[0])? trova_utente_per_nome(p->ospite) : NULL;
    if (a) send_line(a->sock,msg);
    if (b) send_line(b->sock,msg);
}
void invia_stato_partita(Partita *p){
    char js[512];
    snprintf(js,sizeof(js),
        "{\"tipo\":\"STATO_PARTITA\",\"id_partita\":%d,\"stato\":\"%s\",\"scacchiera\":\"%s\","
        "\"turno\":\"%s\",\"proprietario\":\"%s\",\"ospite\":\"%s\"}",
        p->id, stato_to_str(p->stato), p->scacchiera, p->turno, p->proprietario, p->ospite);
    if (p->stato==ST_IN_ATTESA) invia_a_tutti(js,-1); else invia_ai_giocatori(p,js);
}

/* == Gioco (tris) == */
void azzera_scacchiera(Partita *p){ memset(p->scacchiera,'.',9); p->scacchiera[9]='\0'; }
static int riga_ok(const char b[10], int a,int bb,int c){ return b[a]!='.' && b[a]==b[bb] && b[a]==b[c]; }
char esito_scacchiera(const char b[10]){
    int L[8][3]={{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
    for(int i=0;i<8;i++) if(riga_ok(b,L[i][0],L[i][1],L[i][2])) return b[L[i][0]];
    for(int i=0;i<9;i++) if(b[i]=='.') return '.';
    return '=';
}

/* == Comandi == */
void cmd_ciao(int sock, const char *utente){
    pthread_mutex_lock(&g_lock);
    Utente *slot = trova_utente_per_sock(sock);
    if (!slot){
        for (int i=0;i<MAX_CLIENTS;i++) if (!g_utenti[i].attivo){
            slot = &g_utenti[i];
            slot->attivo=1; slot->sock=sock; slot->idPartitaCorrente=-1;
            break;
        }
    }
    if (slot) snprintf(slot->username,sizeof(slot->username),"%s",utente);
    pthread_mutex_unlock(&g_lock);

    char js[256];
    snprintf(js,sizeof(js), "{\"tipo\":\"BENVENUTO\",\"utente\":\"%s\"}", utente);
    send_line(sock, js);
}

void cmd_crea_partita(int sock, const char *utente, const char *nome){
    pthread_mutex_lock(&g_lock);
    Partita *p=NULL;
    for (int i=0;i<MAX_PARTITE;i++) if (g_partite[i].id==0){ p=&g_partite[i]; break; }
    if (!p){ pthread_mutex_unlock(&g_lock); send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Troppe partite\"}"); return; }

    p->id = g_prossimoId++;
    snprintf(p->nome,sizeof(p->nome), "%s", nome?nome:"Partita");
    snprintf(p->proprietario,sizeof(p->proprietario), "%s", utente);
    p->ospite[0]='\0';
    p->stato = ST_IN_ATTESA;
    azzera_scacchiera(p);
    snprintf(p->turno,sizeof(p->turno), "%s", p->proprietario);
    p->pronto_proprietario = p->pronto_ospite = 0;
    pthread_mutex_unlock(&g_lock);

    char js[256];
    snprintf(js,sizeof(js), "{\"tipo\":\"PARTITA_CREATA\",\"id_partita\":%d,\"proprietario\":\"%s\",\"nome\":\"%s\"}",
             p->id, p->proprietario, p->nome);
    invia_a_tutti(js,-1);
    invia_stato_partita(p);
}

void cmd_lista_partite(int sock){
    pthread_mutex_lock(&g_lock);
    char buf[4096]; buf[0]='\0'; strcat(buf,"{\"tipo\":\"ELENCO_PARTITE\",\"partite\":[");
    int first=1;
    for (int i=0;i<MAX_PARTITE;i++) if (g_partite[i].id){
        char item[256];
        snprintf(item,sizeof(item), "%s{\"id\":%d,\"stato\":\"%s\",\"proprietario\":\"%s\",\"ospite\":\"%s\",\"nome\":\"%s\"}",
                 first?"":",", g_partite[i].id, 
                 (g_partite[i].stato==ST_IN_ATTESA?"IN_ATTESA":(g_partite[i].stato==ST_IN_CORSO?"IN_CORSO":"TERMINATA")),
                 g_partite[i].proprietario, g_partite[i].ospite, g_partite[i].nome);
        first=0; if (strlen(buf)+strlen(item)+3<sizeof(buf)) strcat(buf,item);
    }
    strcat(buf, "]}");
    pthread_mutex_unlock(&g_lock);
    send_line(sock, buf);
}

void cmd_entra_richiesta(int sock, const char *utente, int id_partita){
    pthread_mutex_lock(&g_lock);
    Partita *p = trova_partita(id_partita);
    if (!p){ pthread_mutex_unlock(&g_lock); send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Partita non trovata\"}"); return; }
    if (p->stato!=ST_IN_ATTESA){ pthread_mutex_unlock(&g_lock); send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Partita non in attesa\"}"); return; }
    if (p->ospite[0]){ pthread_mutex_unlock(&g_lock); send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Partita piena\"}"); return; }
    Utente *owner = trova_utente_per_nome(p->proprietario);
    pthread_mutex_unlock(&g_lock);

    if (!owner){ send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Proprietario offline\"}"); return; }

    char js[256];
    snprintf(js,sizeof(js), "{\"tipo\":\"ENTRA_RICHIESTO\",\"id_partita\":%d,\"da_utente\":\"%s\"}", id_partita, utente);
    send_line(owner->sock, js);
    send_line(sock, "{\"tipo\":\"ENTRA_RICHIESTA_INVIATA\"}");
}

void cmd_entra_risposta(int sock, const char *ownerUser, int id_partita, int accetta, const char *ospite){
    pthread_mutex_lock(&g_lock);
    Partita *p = trova_partita(id_partita);
    if (!p){ pthread_mutex_unlock(&g_lock); send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Partita non trovata\"}"); return; }
    if (strcmp(p->proprietario, ownerUser)!=0){ pthread_mutex_unlock(&g_lock); send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Non sei il proprietario\"}"); return; }

    Utente *guest = trova_utente_per_nome(ospite);
    Utente *owner = trova_utente_per_nome(ownerUser);
    if (!guest || !owner){ pthread_mutex_unlock(&g_lock); send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Utente non trovato\"}"); return; }

    if (accetta){
        if (owner->idPartitaCorrente!=-1 || guest->idPartitaCorrente!=-1){
            pthread_mutex_unlock(&g_lock);
            send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Qualcuno è già in partita\"}");
            return;
        }
        snprintf(p->ospite,sizeof(p->ospite), "%s", ospite);
        p->stato = ST_IN_CORSO;
        azzera_scacchiera(p);
        snprintf(p->turno,sizeof(p->turno), "%s", p->proprietario);
        owner->idPartitaCorrente = p->id;
        guest->idPartitaCorrente = p->id;
        p->pronto_proprietario = p->pronto_ospite = 0;
    }
    pthread_mutex_unlock(&g_lock);

    char js[256];
    snprintf(js,sizeof(js), "{\"tipo\":\"ENTRA_ESITO\",\"id_partita\":%d,\"accetta\":%s,\"ospite\":\"%s\"}",
             id_partita, accetta?"true":"false", ospite);
    send_line(guest->sock, js);

    invia_stato_partita(p);
}

void cmd_mossa(int sock, const char *utente, int id_partita, int cella){
    pthread_mutex_lock(&g_lock);
    Partita *p = trova_partita(id_partita);
    if (!p){ pthread_mutex_unlock(&g_lock); send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Partita non trovata\"}"); return; }
    if (p->stato!=ST_IN_CORSO){ pthread_mutex_unlock(&g_lock); send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Partita non in corso\"}"); return; }
    if (strcmp(p->turno, utente)!=0){ pthread_mutex_unlock(&g_lock); send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Non è il tuo turno\"}"); return; }
    if (cella<0 || cella>8 || p->scacchiera[cella]!='.'){ pthread_mutex_unlock(&g_lock); send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Cella non valida\"}"); return; }

    char segno = (strcmp(utente,p->proprietario)==0) ? 'X' : 'O';
    p->scacchiera[cella] = segno;

    char esito = esito_scacchiera(p->scacchiera);
    if (esito == '.' ){
        snprintf(p->turno,sizeof(p->turno), "%s",
                 (strcmp(p->turno,p->proprietario)==0) ? p->ospite : p->proprietario);
    } else {
        p->stato = ST_TERMINATA;
    }
    pthread_mutex_unlock(&g_lock);

    if (esito == '.'){
        char js[256];
        snprintf(js,sizeof(js), "{\"tipo\":\"MOSSA_OK\",\"id_partita\":%d,\"scacchiera\":\"%s\",\"prossimo_turno\":\"%s\"}",
                 id_partita, p->scacchiera, p->turno);
        invia_ai_giocatori(p, js);
        invia_stato_partita(p);
        return;
    }

    Utente *a = trova_utente_per_nome(p->proprietario);
    Utente *b = (p->ospite[0]) ? trova_utente_per_nome(p->ospite) : NULL;

    if (a){
        const char *ris = (esito=='X') ? "vittoria" : (esito=='='?"pareggio":"sconfitta");
        char js[256];
        snprintf(js,sizeof(js), "{\"tipo\":\"PARTITA_FINITA\",\"id_partita\":%d,\"esito\":\"%s\",\"vincitore\":\"%s\"}",
                 id_partita, ris, (esito=='='?"":(esito=='X'?p->proprietario:p->ospite)));
        send_line(a->sock, js);
        a->idPartitaCorrente = -1;
    }
    if (b){
        const char *ris = (esito=='O') ? "vittoria" : (esito=='='?"pareggio":"sconfitta");
        char js[256];
        snprintf(js,sizeof(js), "{\"tipo\":\"PARTITA_FINITA\",\"id_partita\":%d,\"esito\":\"%s\",\"vincitore\":\"%s\"}",
                 id_partita, ris, (esito=='='?"":(esito=='O'?p->ospite:p->proprietario)));
        send_line(b->sock, js);
        b->idPartitaCorrente = -1;
    }
    invia_stato_partita(p);
    p->pronto_proprietario = p->pronto_ospite = 0;
}

void cmd_rematch(int sock, const char *utente, int id_partita, int voglio){
    pthread_mutex_lock(&g_lock);
    Partita *p = trova_partita(id_partita);
    if (!p){ pthread_mutex_unlock(&g_lock); send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Partita non trovata\"}"); return; }
    if (p->stato!=ST_TERMINATA){ pthread_mutex_unlock(&g_lock); send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Partita non terminata\"}"); return; }

    if (strcmp(utente,p->proprietario)==0) p->pronto_proprietario = voglio?1:0;
    else if (strcmp(utente,p->ospite)==0)  p->pronto_ospite = voglio?1:0;
    else { pthread_mutex_unlock(&g_lock); send_line(sock,"{\"tipo\":\"ERRORE\",\"messaggio\":\"Non fai parte della partita\"}"); return; }

    int prp=p->pronto_proprietario, pro=p->pronto_ospite;
    pthread_mutex_unlock(&g_lock);

    char js[256];
    snprintf(js,sizeof(js), "{\"tipo\":\"REMATCH_STATO\",\"id_partita\":%d,\"pronto_proprietario\":%s,\"pronto_ospite\":%s}",
             id_partita, prp?"true":"false", pro?"true":"false");
    invia_ai_giocatori(p, js);

    if (prp && pro){
        pthread_mutex_lock(&g_lock);
        p->stato = ST_IN_CORSO;
        azzera_scacchiera(p);
        snprintf(p->turno,sizeof(p->turno), "%s", p->proprietario);
        Utente *a = trova_utente_per_nome(p->proprietario);
        Utente *b = trova_utente_per_nome(p->ospite);
        if (a) a->idPartitaCorrente = p->id;
        if (b) b->idPartitaCorrente = p->id;
        p->pronto_proprietario = p->pronto_ospite = 0;
        pthread_mutex_unlock(&g_lock);
        invia_stato_partita(p);
    }
}
