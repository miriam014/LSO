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

    /* == I/O  == */
    ssize_t send_msg(int sock, const char *s){
        ssize_t n = strlen(s);
        if (write(sock, s, n) < 0) { 
            perror("write");
            return -1;
        }
        if (write(sock, "\n", 1) < 0) {
            perror("write");
            return -1;
        }
        return n+1;
    }

    /* == Broadcast / Stato == */
    void invia_a_tutti(const char *msg, int escludi_sock){
        for (int i=0; i<MAX_CLIENTS; i++) {
            if (g_utenti[i].attivo && g_utenti[i].sock != escludi_sock) 
                send_msg(g_utenti[i].sock, msg);
        }
    }

    static void invia_ai_giocatori(Partita *p, const char *msg){
        if (p->proprietario_sock > 0) 
            send_msg(p->proprietario_sock,msg);
        if (p->ospite_sock > 0)      
            send_msg(p->ospite_sock,msg);
    }

    void invia_stato_partita(Partita *p){
        if (!p || p->id==0) return;
        char buf[512];
        snprintf(buf,sizeof(buf),
            "STATO_PARTITA %d %s %s turno=%s proprietario=%s ospite=%s",
            p->id, stato_to_str(p->stato), p->scacchiera, p->turno, p->proprietario, (p->ospite[0]?p->ospite:"-"));
        
        if (p->stato==ST_IN_ATTESA) invia_a_tutti(buf,-1); 
        else invia_ai_giocatori(p,buf);
    }

    void rimuovi_partite_di_sock(int sock) {
        Partita *da_avvisare[MAX_PARTITE];
        int cnt = 0;

        pthread_mutex_lock(&g_lock);
        for (int i=0; i<MAX_PARTITE; i++) {
            Partita *p = &g_partite[i];
            if (p->id == 0) continue;

            if (p->proprietario_sock == sock || p->ospite_sock == sock) {
                // Avvisa l’altro giocatore se c’è
                int avversario_sock = (p->proprietario_sock == sock) ? p->ospite_sock : p->proprietario_sock;
                if (avversario_sock > 0) {
                    da_avvisare[cnt++] = p; // salviamo la partita da notificare dopo unlock
                }

                // Non cancellare la partita, solo rimuovi il socket
                if (p->proprietario_sock == sock) p->proprietario_sock = 0;
                if (p->ospite_sock == sock) p->ospite_sock = 0;

                // Se la partita non ha più giocatori, allora puoi liberarla
                if (p->proprietario_sock == 0 && p->ospite_sock == 0) {
                    memset(p, 0, sizeof(*p));
                }
            }
        }
        pthread_mutex_unlock(&g_lock);

        // Notifica ai clienti dopo aver rilasciato il lock
        for (int i=0; i<cnt; i++) {
            Partita *p = da_avvisare[i];
            int avversario_sock = (p->proprietario_sock == 0) ? p->ospite_sock : p->proprietario_sock;
            if (avversario_sock > 0) {
                char buf[256];
                snprintf(buf, sizeof(buf), "AVVERSARIO_DISCONNESSO partita=%d", p->id);
                send_msg(avversario_sock, buf);
                cmd_mie_partite(avversario_sock);
            
                // --- RIFIUTO AUTOMATICO REMATCH SE LA PARTITA ERA TERMINATA ---
                if (p->stato == ST_TERMINATA) {
                    send_msg(avversario_sock, "REMATCH_ESITO accetta=false");
                }
            }
        }
    }


    /* == Gioco (tris) == */
    void azzera_scacchiera(Partita *p){ 
        memset(p->scacchiera,'.',9); 
        p->scacchiera[9]='\0'; 
    }

    static int riga_ok(const char b[10], int a,int bb,int c){ 
        return b[a]!='.' && b[a]==b[bb] && b[a]==b[c];
    }

    char esito_scacchiera(const char b[10]){
        int L[8][3]={{0,1,2},{3,4,5},{6,7,8},{0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for(int i=0;i<8;i++) {
            if(riga_ok(b,L[i][0],L[i][1],L[i][2]))
            return b[L[i][0]];      //se trova tre righe o colonne uguali ritorna quel carattere o X o 0
        }
        for(int i=0;i<9;i++){       // se vede ancora un "." vuol dire che ci sono ancora celle vuote
            if(b[i]=='.') return '.';
        }
        return '=';                 // se non ci sono più celle vuote e non ha trovato un vincitore allora è pareggio
    }

    /* == Comandi == */
    void cmd_crea_partita(int sock, const char *utente){
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
            send_msg(sock,"ERRORE Troppe partite"); 
            return; 
        }

        p->id = g_prossimoId++;
        snprintf(p->proprietario, sizeof(p->proprietario), "%s", utente);
        p->proprietario_sock = sock;
        p->ospite[0]='\0';
        p->stato = ST_IN_ATTESA;
        azzera_scacchiera(p);
        snprintf(p->turno, sizeof(p->turno), "%s", utente);
        p->pronto_proprietario = p->pronto_ospite = 0;

        // reset vincitore per la nuova partita
        p->vincitore[0] = '\0';    

        pthread_mutex_unlock(&g_lock);

        // invio al proprietario
        char buf[64];
        snprintf(buf, sizeof(buf), "ATTESA_AVVERSARIO id=%d", p->id);
        send_msg(sock, buf);
    }

    //questo restituisce la lista delle mie partite in corso e terminate
    void cmd_mie_partite(int sock){
        pthread_mutex_lock(&g_lock);
        send_msg(sock, "MIE_PARTITE");

            for (int i=0; i<MAX_PARTITE; i++) {
                Partita *p = &g_partite[i];
                if (!p->id) continue;
                if (p->proprietario_sock <= 0 && p->ospite_sock <= 0)  continue;

                if (p->stato == ST_IN_CORSO &&
                    (p->proprietario_sock == sock || p->ospite_sock == sock)) {

                    char buf[256];
                    snprintf(buf, sizeof(buf),
                        "%d %s proprietario=%s ospite=%s scacchiera=%s turno=%s",
                        p->id, stato_to_str(p->stato),
                        p->proprietario, p->ospite,
                        p->scacchiera, p->turno );
                    send_msg(sock, buf);
                    printf("[DEBUG][cmd_mie_partite] inviando lista a sock=%d\n", sock);

                } else if (p->stato == ST_TERMINATA &&
                        (p->proprietario_sock == sock || p->ospite_sock == sock)) {

                    // usa SEMPRE il campo vincitore salvato
                    const char *vinc = (p->vincitore[0] ? p->vincitore : "=");

                    char buf[256];
                    snprintf(buf, sizeof(buf),
                        "%d %s proprietario=%s ospite=%s vinc=%s",
                        p->id, stato_to_str(p->stato),
                        p->proprietario, p->ospite[0] ? p->ospite : "-",
                        vinc);
                    send_msg(sock, buf);
                    printf("[DEBUG][cmd_mie_partite] inviando lista a sock=%d\n", sock);
                }
            }

            send_msg(sock, ""); // riga vuota finale
            pthread_mutex_unlock(&g_lock);
        }
    

    //lista partite in attesa di un partecipante
    void cmd_partite_in_attesa(int sock){
        pthread_mutex_lock(&g_lock);
        send_msg(sock, "LISTA_ATTESA");

        for (int i=0; i<MAX_PARTITE; i++) {
            Partita *p = &g_partite[i];
            if (!p->id) continue;

            if (p->stato==ST_IN_ATTESA && p->proprietario_sock != sock){
                char buf[256];
                snprintf(buf,sizeof(buf),
                    "%d %s proprietario=%s ospite=%s",
                    p->id, stato_to_str(p->stato), p->proprietario, p->ospite[0]?p->ospite:"-" );
                send_msg(sock, buf);
            } 
        }
        send_msg(sock, ""); // riga vuota finale
        pthread_mutex_unlock(&g_lock);
    }

    void cmd_annulla_partita(int sock, int id_partita){
        pthread_mutex_lock(&g_lock);
        Partita *p = trova_partita(id_partita);
        if (p && p->stato ==ST_IN_ATTESA && p->proprietario_sock == sock) {
        memset(p, 0, sizeof(*p)); // libera lo slot
        }
        pthread_mutex_unlock(&g_lock);
    }

    // un utente richiede di entrare in una partita in attesa di un avversario
    void cmd_entra_richiesta(int sock, const char *utente, int id_partita){
        pthread_mutex_lock(&g_lock);
        Partita *p = trova_partita(id_partita);
        if (!p){ 
            pthread_mutex_unlock(&g_lock); 
            send_msg(sock,"ERRORE Partita non trovata"); 
            return; 
        }
        if (p->stato!=ST_IN_ATTESA || p->ospite[0]){ 
            pthread_mutex_unlock(&g_lock); 
            send_msg(sock,"ERRORE Partita non in attesa di avversario"); 
            return; 
        }

        // salvo ospite temporaneamente (verrà confermato da cmd_entra_risposta)
        snprintf(p->ospite, sizeof(p->ospite), "%s", utente);
        p->ospite_sock = sock;
        int owner_sock = p->proprietario_sock;
        pthread_mutex_unlock(&g_lock);

        // invio notifica al proprietario che qualcuno vuole entrare
        char buf[256];
        snprintf(buf,sizeof(buf),"ENTRA_RICHIESTA %s %d", utente, id_partita);
        send_msg(owner_sock, buf);
        // conferma al richiedente
        send_msg(sock, "ENTRA_RICHIESTA_INVIATA");
    }


    // comando per rispondere alla richiesta da parte di un avversario di entrare in partita
    void cmd_entra_risposta(int sock, const char *ownerUser, int id_partita, int accetta, const char *ospite){
        pthread_mutex_lock(&g_lock);
        Partita *p = trova_partita(id_partita);
        if (!p || p->stato != ST_IN_ATTESA){ 
            pthread_mutex_unlock(&g_lock); 
            send_msg(sock,"ERRORE Partita non disponibile"); 
            return;
        }
        int guest_sock = p->ospite_sock;

        if (!accetta) { // resetto ospite se rifiutato
            p->ospite[0] = '\0';
            p->ospite_sock = 0;
            pthread_mutex_unlock(&g_lock);

            if(guest_sock > 0) 
                send_msg(guest_sock, "ENTRA_ESITO accetta=false"); // notifico l'esito al richiedente
            return;
        }

        p->stato = ST_IN_CORSO;
        azzera_scacchiera(p);
        snprintf(p->turno,sizeof(p->turno), "%s", p->proprietario);
        p->pronto_proprietario = p->pronto_ospite = 0;

        pthread_mutex_unlock(&g_lock);

        // notifico l'esito al richiedente
        char buf[256];
        snprintf(buf,sizeof(buf), "ENTRA_ESITO partita=%d accetta=true ospite=%s", id_partita, p->ospite);
        if (guest_sock > 0) send_msg(guest_sock, buf);

        cmd_mie_partite(p->proprietario_sock); //invia le mie partite lo riceve solo il proprietario
        cmd_mie_partite(p->ospite_sock); //invia le mie partite lo riceve solo l'ospite
        invia_stato_partita(p);
    }

    void cmd_mossa(int sock, const char *utente, int id_partita, int cella){
        pthread_mutex_lock(&g_lock);
        Partita *p = trova_partita(id_partita);
        if (!p){ pthread_mutex_unlock(&g_lock); send_msg(sock,"ERRORE Partita non trovata"); return; }
        if (p->stato!=ST_IN_CORSO){ pthread_mutex_unlock(&g_lock); send_msg(sock,"ERRORE Partita non in corso"); return; }
        
        int is_owner = (sock == p->proprietario_sock);
        int is_guest = (sock == p->ospite_sock);
        if (!is_owner && !is_guest){ 
            pthread_mutex_unlock(&g_lock); 
            send_msg(sock,"ERRORE Non fai parte della partita"); 
            return; 
        }

        //di chi è il turno? confronta col nome salvato nel server, non con 'utente' della richiesta
        int owner_turn = (strcmp(p->turno, p->proprietario) == 0);
        if ((owner_turn && !is_owner) || (!owner_turn && !is_guest)){
            pthread_mutex_unlock(&g_lock); 
            send_msg(sock,"ERRORE Non è il tuo turno"); 
            return;
        }

        if (cella<0 || cella>8 || p->scacchiera[cella]!='.'){ 
            pthread_mutex_unlock(&g_lock); 
            send_msg(sock,"ERRORE Cella non valida"); 
            return; 
        }

        char segno = is_owner ? 'X' : 'O';
        p->scacchiera[cella] = segno;

        char esito = esito_scacchiera(p->scacchiera);
        if (esito == '.' ){
            snprintf(p->turno,sizeof(p->turno), "%s", owner_turn ? p->ospite : p->proprietario);
            pthread_mutex_unlock(&g_lock);
            char buf[256];
            snprintf(buf,sizeof(buf), "MOSSA_OK partita=%d scacchiera=%s prossimo_turno=%s", id_partita, p->scacchiera, p->turno);
            invia_ai_giocatori(p, buf);
            invia_stato_partita(p);
            return;
        }else {
            p->stato = ST_TERMINATA;

            // Salva il vincitore nella struct
            if (esito == '=') {
                snprintf(p->vincitore, sizeof(p->vincitore), "pareggio");
            } else if (esito == 'X') {
                snprintf(p->vincitore, sizeof(p->vincitore), "%s", p->proprietario);
            } else if (esito == 'O') {
                snprintf(p->vincitore, sizeof(p->vincitore), "%s", p->ospite);
            }

            pthread_mutex_unlock(&g_lock);

            char buf[256];
            snprintf(buf,sizeof(buf),
                "PARTITA_FINITA id_partita=%d scacchiera=%s vincitore=%s",
                id_partita, p->scacchiera, p->vincitore);
            invia_ai_giocatori(p, buf);

            // aggiorno le mie partite di entrambi
            cmd_mie_partite(p->proprietario_sock);
            cmd_mie_partite(p->ospite_sock);
        }
        pthread_mutex_unlock(&g_lock);
    }

        // comando per richiedere una rivincita dopo che la partita è terminata
        // Richiesta di rematch
        void cmd_rematch_richiesta(int sock, const char *utente, int id_partita) {
            pthread_mutex_lock(&g_lock);
            Partita *p = trova_partita(id_partita);
            if (!p || p->stato != ST_TERMINATA) {
                pthread_mutex_unlock(&g_lock);
                send_msg(sock, "ERRORE Partita non disponibile per rematch");
                return;
            }
            int avversario_sock = (sock == p->proprietario_sock) ? p->ospite_sock : p->proprietario_sock;
            pthread_mutex_unlock(&g_lock);

            if (avversario_sock > 0) {
                char buf[256];
                snprintf(buf, sizeof(buf), "REMATCH_RICHIESTA %s %d", utente, id_partita);
                send_msg(avversario_sock, buf);
            }
        }

        // Risposta al rematch
        void cmd_rematch_risposta(int sock, const char *utente, int id_partita, int accetta) {
        pthread_mutex_lock(&g_lock);
        Partita *old_p = trova_partita(id_partita);
        
        // Controlli di sicurezza
        if (!old_p) {
            pthread_mutex_unlock(&g_lock);
            send_msg(sock, "ERRORE Partita non trovata");
            return;
        }

        int owner_sock = old_p->proprietario_sock;
        int guest_sock = old_p->ospite_sock;

        // Se RIFIUTA
        if (!accetta) {
            pthread_mutex_unlock(&g_lock);
            if (owner_sock > 0) send_msg(owner_sock, "REMATCH_ESITO accetta=false");
            if (guest_sock > 0) send_msg(guest_sock, "REMATCH_ESITO accetta=false");
            return;
        }

        // Se ACCETTA: Creiamo NUOVA partita
        Partita *nuova = NULL;
        for (int i = 0; i < MAX_PARTITE; i++) {
            if (g_partite[i].id == 0) {
                nuova = &g_partite[i];
                break;
            }
        }

        if (!nuova) {
            pthread_mutex_unlock(&g_lock);
            send_msg(sock, "ERRORE Troppe partite attive");
            return;
        }

        // Configura la nuova partita copiando i dati dalla vecchia
        nuova->id = g_prossimoId++;
        strcpy(nuova->proprietario, old_p->proprietario);
        strcpy(nuova->ospite, old_p->ospite);
        nuova->proprietario_sock = owner_sock;
        nuova->ospite_sock = guest_sock;
        nuova->stato = ST_IN_CORSO;
        azzera_scacchiera(nuova);
        
        // Il turno parte dal proprietario (o inverti se vuoi)
        snprintf(nuova->turno, sizeof(nuova->turno), "%s", nuova->proprietario);
        nuova->vincitore[0] = '\0';

        // IMPORTANTE: Distruggiamo la VECCHIA partita per liberare lo slot
        // (Non serve più perché ora esiste quella nuova)
        //memset(old_p, 0, sizeof(*old_p)); 

        pthread_mutex_unlock(&g_lock);

        // Costruiamo il messaggio CON IL NUOVO ID
        char msg[256];
        snprintf(msg, sizeof(msg), "REMATCH_ESITO accetta=true nuovo_id=%d", nuova->id);

        // Avvisiamo entrambi
        if (owner_sock > 0) send_msg(owner_sock, msg);
        if (guest_sock > 0) send_msg(guest_sock, msg);

        // Inviamo subito lo stato della NUOVA partita
        invia_stato_partita(nuova);

        // Aggiorniamo le liste partite dei client
        cmd_mie_partite(owner_sock);
        cmd_mie_partite(guest_sock);
    }
        


        void cmd_abbandona_partita(int sock, const char *utente, int id_partita) {
        Partita *p = NULL;
        // Acquisisco lock solo per modificare lo stato della partita
        pthread_mutex_lock(&g_lock);
        p = trova_partita(id_partita);
        if (!p) {
            pthread_mutex_unlock(&g_lock);
            send_msg(sock, "ERRORE Partita non trovata");
            return;
        }

        int is_owner = (sock == p->proprietario_sock);
        int is_guest = (sock == p->ospite_sock);
        if (!is_owner && !is_guest) {
            pthread_mutex_unlock(&g_lock);
            send_msg(sock, "ERRORE Non fai parte della partita");
            return;
        }

        // Imposto lo stato a terminata
        p->stato = ST_TERMINATA;
        // Chi non abbandona è il vincitore
        const char *vincitore_nome = is_owner ? p->ospite : p->proprietario;
        snprintf(p->vincitore, sizeof(p->vincitore), "%s", vincitore_nome);
        
        // Notifica all’altro giocatore che l’avversario ha abbandonato
        int avversario_sock = is_owner ? p->ospite_sock : p->proprietario_sock;
        printf("[DEBUG] %s ha abbandonato la partita %d\n", utente, id_partita);

        pthread_mutex_unlock(&g_lock);

        if (avversario_sock > 0) {
            char buf[256];
            snprintf(buf, sizeof(buf),
                    "PARTITA_FINITA id_partita=%d abbandono=true scacchiera=%s vincitore=%s",
                    id_partita, p->scacchiera, p->vincitore);
            send_msg(avversario_sock, buf);
            cmd_mie_partite(avversario_sock); // aggiorna lista avversario
        }

        // Aggiorno le mie partite del giocatore che ha abbandonato
        cmd_mie_partite(sock);
    }

    // Incolla questo in fondo a comandi.c

    void cmd_stato_partita(int sock, int id_partita) {
        pthread_mutex_lock(&g_lock);
        Partita *p = trova_partita(id_partita);
        if (p) {
            invia_stato_partita(p);
        } else {
            send_msg(sock, "ERRORE Partita non trovata");
        }
        pthread_mutex_unlock(&g_lock);
    }

