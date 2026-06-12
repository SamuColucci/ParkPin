ParkPin : progetto per il contest di MobileProgramming 2025/2026
# ParkPin 🚗
> **Trova. Parcheggia. Ritrova.**

ParkPin è un'applicazione Android nativa progettata per semplificare la gestione della sosta urbana. L'app risolve tre problemi fondamentali dell'automobilista: la ricerca di un posto, il posizionamento del veicolo (con gestione dei tempi di sosta) e il percorso di ritorno a piedi.

---

### 🌟 Caratteristiche Principali

* **Ricerca Geospaziale Intelligente:** Individuazione in tempo reale di parcheggi (gratuiti e a pagamento) nel raggio di 2 km tramite query geospaziali asincrone.
* **Interfaccia Dinamica a Stati:** Una sola dashboard che si adatta autonomamente alle necessità dell'utente (Stato di ricerca, Stato di sosta con timer, Stato di navigazione attiva).
* **Navigazione Multimodale:** Calcolo dinamico dei percorsi stradali (modalità auto) per raggiungere il parcheggio e percorsi pedonali (modalità a piedi) per ritornare al veicolo.
* **Promemoria per la Sosta:** Sistema di avviso integrato con il sistema operativo per notificare la scadenza imminente del ticket del parcheggio.
* **Geocodifica Inversa:** Traduzione immediata delle coordinate GPS in indirizzi stradali leggibili per una migliore comprensione del punto di sosta.

---

### 🛠️ Architettura Tecnica e Stack Tecnologico

L'applicazione è sviluppata in **Java** seguendo le linee guida del framework Android Jetpack, garantendo una netta separazione tra la gestione dei dati e l'interfaccia grafica.

| Modulo | Tecnologia / Libreria | Scopo |
| :--- | :--- | :--- |
| **Mappe & Routing** | OSMdroid & OSRM Engine | Rendering cartografico offline/online e calcolo delle polilinee di percorso. |
| **Persistenza Dati** | Jetpack Room (SQLite) | Memorizzazione permanente e sicura della posizione del veicolo e delle note dell'utente. |
| **Networking & API** | Retrofit 2 & Overpass API | Comunicazione asincrona con i server OpenStreetMap per il download dei parcheggi. |
| **Stato dell'App** | SharedPreferences | Persistenza dei flag di stato per garantire la ripresa della sessione in caso di riavvio. |
| **Background & Avvisi** | AlarmManager & BroadcastReceiver | Gestione dei timer di scadenza in background senza consumo energetico della CPU. |

---

### 💡 Note di Ottimizzazione

Per ottimizzare le prestazioni e ridurre il traffico dati, l'applicazione implementa una classe di **Caching Geografico** (`ParkingCache`). Se l'utente naviga tra le schermate nella stessa area urbana, i dati vengono caricati istantaneamente dalla memoria locale senza attendere nuove risposte dal server Overpass, aumentando la fluidità complessiva dell'app.
