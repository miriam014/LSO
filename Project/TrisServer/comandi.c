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
    char buf[512];
    snprintf(buf,sizeof(buf),
        "STATO_PARTITA %d %s %s turno=%s proprietario=%s ospite=%s",
        p->id, stato_to_str(p->stato), p->scacchiera, p->turno, p->proprietario, (p->ospite[0]?p->ospite:"-"));
    if (p->stato==ST_IN_ATTESA) invia_a_tutti(buf,-1); 
    else invia_ai_giocatori(p,buf);
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
void cmd_crea_partita(int sock, const char *utente){
    printf("DEBUG: cmd_crea_partita invoked sock=%d utente=[%s]\n", sock, utente);
    fflush(stdout);
    pthread_mutex_lock(&g_lock);
    Partita *p=NULL;
    for (int i=0; i<MAX_PARTITE; i++) {
        if (g_partite[i].id==0){ 
            p = &g_partite[i]; 
            break; 
        }
    }
    if (!p) {
        pthread_mutex_unlock(&g_lock); 
        send_line(sock,"ERRORE Troppe partite"); 
        return; 
    }

    p->id = g_prossimoId++;
    snprintf(p->proprietario, sizeof(p->proprietario), "%s", utente);
    p->ospite[0]='\0';
    p->stato = ST_IN_ATTESA;
    azzera_scacchiera(p);
    snprintf(p->turno, sizeof(p->turno), "%s", p->proprietario);
    p->pronto_proprietario = p->pronto_ospite = 0;
    pthread_mutex_unlock(&g_lock);

    // invio al proprietario
    send_line(sock, "ATTESA_AVVERSARIO");

    // notifica a tutti
    char buf[128];
    snprintf(buf, sizeof(buf), "INVITO_PARTECIPAZIONE id=%d proprietario=%s", p->id, p->proprietario);
    invia_a_tutti(buf, sock); // sock escluso
}

void cmd_lista_partite(int sock){
    pthread_mutex_lock(&g_lock);
    char buf[4096]; buf[0]='\0'; 
    strcat(buf,"LISTA_PARTITE\n");
    for (int i=0;i<MAX_PARTITE;i++) {
        if (g_partite[i].id && g_partite[i].stato==ST_IN_ATTESA){
        char item[256];
        snprintf(item,sizeof(item), "  %d %s proprietario=%s ospite=%s nome=%s\n",
                 g_partite[i].id, stato_to_str(g_partite[i].stato),
                 g_partite[i].proprietario,
                 g_partite[i].ospite[0]?g_partite[i].ospite:"-",
                 g_partite[i].nome);
        strcat(buf,item);
        }
    }
    pthread_mutex_unlock(&g_lock);
    send_line(sock, buf);
}

// un utente richiede di entrare in una partita in attesa di un avversario
void cmd_entra_richiesta(int sock, const char *utente, int id_partita){
    pthread_mutex_lock(&g_lock);
    Partita *p = trova_partita(id_partita);
    if (!p){ 
        pthread_mutex_unlock(&g_lock); 
        send_line(sock,"ERRORE Partita non trovata"); 
        return; 
    }
    if (p->stato!=ST_IN_ATTESA || p->ospite[0]){ 
        pthread_mutex_unlock(&g_lock); 
        send_line(sock,"ERRORE Partita non in attesa di avversario"); 
        return; 
    }

    Utente *owner = trova_utente_per_nome(p->proprietario);
    pthread_mutex_unlock(&g_lock);

    if (!owner) { 
        send_line(sock,"ERRORE Proprietario offline");
        return; 
    }

    // invio notifica al proprietario che qualcuno vuole entrare
    char buf[256];
    snprintf(buf,sizeof(buf),"ENTRA_RICHIESTO partita=%d ospite=%s", id_partita, utente);
    send_line(owner->sock, buf);
    // conferma al richiedente
    send_line(sock, "ENTRA_RICHIESTA_INVIATA");
}


// comando per rispondere alla richiesta da parte di un avversario di entrare in partita
void cmd_entra_risposta(int sock, const char *ownerUser, int id_partita, int accetta, const char *ospite){
    pthread_mutex_lock(&g_lock);
    Partita *p = trova_partita(id_partita);
    if (!p || p->stato != ST_IN_ATTESA){ 
        pthread_mutex_unlock(&g_lock); 
        send_line(sock,"ERRORE Partita non disponibile"); 
        return;
    }

    Utente *guest = trova_utente_per_nome(ospite);
    Utente *owner = trova_utente_per_nome(ownerUser);
    if (!guest || !owner){ 
        pthread_mutex_unlock(&g_lock); 
        send_line(sock,"ERRORE Utente non trovato"); 
        return; 
    }

    if (!accetta) { //la richiesta viene rifiutata 
        pthread_mutex_unlock(&g_lock);
        send_line(sock, "ENTRA_ESITO accetta=false");
        return;
    }

    // aggiungo l'ospite alla partita
    snprintf(p->ospite,sizeof(p->ospite), "%s", ospite);
    p->stato = ST_IN_CORSO;
    azzera_scacchiera(p);
    snprintf(p->turno,sizeof(p->turno), "%s", p->proprietario);
    //aggiorno sttao dei giocatori
    owner->idPartitaCorrente = p->id;
    guest->idPartitaCorrente = p->id;
    p->pronto_proprietario = p->pronto_ospite = 0;


    pthread_mutex_unlock(&g_lock);
    // notifico l'esito al richiedente
    char buf[256];
    snprintf(buf,sizeof(buf), "ENTRA_ESITO partita=%d accetta=true ospite=%s", id_partita, ospite);
    send_line(guest->sock, buf);

    invia_stato_partita(p);
}

void cmd_mossa(int sock, const char *utente, int id_partita, int cella){
    pthread_mutex_lock(&g_lock);
    Partita *p = trova_partita(id_partita);
    if (!p){ pthread_mutex_unlock(&g_lock); send_line(sock,"ERRORE Partita non trovata"); return; }
    if (p->stato!=ST_IN_CORSO){ pthread_mutex_unlock(&g_lock); send_line(sock,"ERRORE Partita non in corso"); return; }
    if (strcmp(p->turno, utente)!=0){ pthread_mutex_unlock(&g_lock); send_line(sock,"ERRORE Non è il tuo turno"); return; }
    if (cella<0 || cella>8 || p->scacchiera[cella]!='.'){ pthread_mutex_unlock(&g_lock); send_line(sock,"ERRORE Cella non valida"); return; }

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
        char buf[256];
        snprintf(buf,sizeof(buf), "MOSSA_OK partita=%d scacchiera=%s prossimo_turno=%s",
                 id_partita, p->scacchiera, p->turno);
        invia_ai_giocatori(p, buf);
        invia_stato_partita(p);
        return;
    }

    Utente *a = trova_utente_per_nome(p->proprietario);
    Utente *b = (p->ospite[0]) ? trova_utente_per_nome(p->ospite) : NULL;

    if (a){
        const char *ris = (esito=='X') ? "vittoria" : (esito=='='?"pareggio":"sconfitta");
        char buf[256];
        snprintf(buf,sizeof(buf), "PARTITA_FINITA id_partita=%d esito=%s vincitore=%s",
                 id_partita, ris, (esito=='='?"":(esito=='X'?p->proprietario:p->ospite)));
        send_line(a->sock, buf);
        a->idPartitaCorrente = -1;
    }
    if (b){
        const char *ris = (esito=='O') ? "vittoria" : (esito=='='?"pareggio":"sconfitta");
        char buf[256];
        snprintf(buf,sizeof(buf), "PARTITA_FINITA id_partita=%d esito=%s vincitore=%s",
                 id_partita, ris, (esito=='='?"":(esito=='O'?p->ospite:p->proprietario)));
        send_line(b->sock, buf);
        b->idPartitaCorrente = -1;
    }
    invia_stato_partita(p);
    p->pronto_proprietario = p->pronto_ospite = 0;
}

// comando per richiedere una rivincita dopo che la partita è terminata
void cmd_rematch(int sock, const char *utente, int id_partita, int voglio){
    pthread_mutex_lock(&g_lock);
    Partita *p = trova_partita(id_partita);
    if (!p){ pthread_mutex_unlock(&g_lock); send_line(sock,"ERRORE Partita non trovata"); return; }
    if (p->stato!=ST_TERMINATA){ pthread_mutex_unlock(&g_lock); send_line(sock,"ERRORE Partita non terminata"); return; }

    if (strcmp(utente,p->proprietario)==0) p->pronto_proprietario = voglio?1:0;
    else if (strcmp(utente,p->ospite)==0)  p->pronto_ospite = voglio?1:0;
    else { pthread_mutex_unlock(&g_lock); send_line(sock,"ERRORE Non fai parte della partita"); return; }

    int prp=p->pronto_proprietario, pro=p->pronto_ospite;
    pthread_mutex_unlock(&g_lock);

    char buf[256];
    snprintf(buf,sizeof(buf), "REMATCH_STATO id_partita=%d pronto_proprietario=%s pronto_ospite=%s",
             id_partita, prp?"true":"false", pro?"true":"false");
    invia_ai_giocatori(p, buf);

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
