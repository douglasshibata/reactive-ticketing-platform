# 🎟️ Reactive Ticketing & Credentialing Platform (`reactive-ticketing-platform`)

Uma plataforma completa de bilhética e credenciamento de altíssima concorrência construída com **Quarkus 3.x (Java 21, Vert.x, Mutiny)** e **Angular 19 (Signals, RxJS, PWA Offline-First)**. A solução resolve o desafio primordial do setor de eventos: **concorrência simultânea massiva sem overbooking (venda dupla)** e **credenciamento em catracas em menos de 100ms** mesmo sob falha total de rede 4G/Wi-Fi.

---

## 🏛️ Arquitetura e Decisões de Engenharia

O sistema é desenhado com estrita separação entre o **Estado Quente** (Redis), a **Mensageria Assíncrona** (Apache Kafka) e o **Estado Frio** (PostgreSQL):

```
                        ┌────────────────────────────────────────────────────────┐
                        │                   Frontend (Angular 19)                │
                        │   • Angular Signals (DOM reativo instantâneo)          │
                        │   • RxJS (retryWhen, switchMap, takeUntilDestroyed)    │
                        │   • PWA & IndexedDB (Offline-First Gate Scanner)       │
                        └──────────┬─────────────────────────────▲───────────────┘
                                   │                             │
                     POST /lock    │                             │ WebSocket Broadcast
                                   ▼                             │ (TICKET_LOCKED / SOLD)
                        ┌────────────────────────────────────────┴───────────────┐
                        │              Reactive Gateway (Quarkus 3.x)             │
                        │   • Vert.x Event Loop (Non-blocking I/O)               │
                        │   • Mutiny Uni/Multi Fluxos Reativos                   │
                        │   • WebSockets Next (`/ws/tickets`)                     │
                        └──────────┬─────────────────────────────┬───────────────┘
                                   │                             │
                      SETNX & TTL  │                             │ Emit ticket.paid
                                   ▼                             ▼
                        ┌───────────────────────┐     ┌──────────────────────────┐
                        │      Redis 7.0        │     │       Apache Kafka       │
                        │  (Estado Quente &     │     │ (Fila Assíncrona &       │
                        │   Distributed Lock)   │     │  Desacoplamento de PDFs) │
                        └───────────────────────┘     └──────────┬───────────────┘
                                                                 │ Consume ticket.paid
                                                                 ▼
                                                      ┌──────────────────────────┐
                                                      │      PostgreSQL 16       │
                                                      │  (Estado Frio / Source   │
                                                      │   of Truth / Relatórios) │
                                                      └──────────────────────────┘
```

### 1. Separação Quente vs Frio
* **Redis (Estado Quente & Locks):** O PostgreSQL não suporta 50.000 requisições simultâneas tentando travar a mesma linha de inventário (*row-level lock contention*). O Redis atua como a barreira atômica inicial com comandos `SET event:101:seat:A12:lock <userId> NX EX 600` (TTL de 10 minutos).
* **Quarkus + Mutiny:** O motor reativo opera com I/O não bloqueante baseado em Vert.x. Conexões WebSocket em larga escala e requisições HTTP consomem pouca memória e CPU.
* **Apache Kafka (Desacoplamento Assíncrono):** Quando o pagamento é aprovado, o Quarkus não gera o QR Code e o PDF na mesma requisição HTTP. Ele despacha um evento no tópico `ticket.paid` e responde imediatamente com sucesso ao cliente, enquanto workers em background realizam a renderização e o envio do e-mail.
* **PostgreSQL (Estado Frio & Source of Truth):** Base relacional otimizada para histórico, relatórios contábeis e auditoria com *Optimistic Locking* (`version`).

### 2. Automatização de Transmissão & Gravação VOD Segura (Jibri, Kafka & S3)
* **Webhook Reativo do Jibri (`/api/webhooks/jibri/upload-success`):** Recebe de forma 100% não-bloqueante o aviso de conclusão de upload do vídeo gerado pelo gravador Jibri.
* **Validação Segura de Bearer Token:** Autentica a chamada contra um segredo partilhado injetado via variável de ambiente (`WEBHOOK_JIBRI_SECRET`), prevenindo falsificação de chamadas para desbloquear conteúdos pagos.
* **Transação Reativa e Desacoplamento via Kafka:** Atualiza o estado do evento para `VOD_READY` no PostgreSQL via Hibernate Reactive Panache e emite imediatamente o evento `VodReadyEvent` no canal Kafka `events.vod.ready`. O **n8n** consome essa mensagem para orquestrar e-mails personalizados em lote (SendGrid/SMTP) sem sobrecarregar a API transacional.
* **S3 Pre-signed URLs (< 1ms em memória):** O front-end nunca conhece a URL física do bucket S3. O endpoint protegido por JWT (`GET /api/events/{id}/vod-access`) valida o bilhete no banco e gera uma URL assinada temporária (3h) usando o `S3Presigner` da AWS sem chamadas de rede externas.
* **Blindagem no Leitor Angular (`VodPlayerComponent`):** O leitor de vídeo utiliza `controlsList="nodownload"` e bloqueio de botão direito `(contextmenu)="preventRightClick($event)"`, garantindo mitigação contra pirataria direta.

### 3. Credenciamento Offline-First (Staff PWA)
* Na entrada do evento físico (onde a rede 4G/Wi-Fi costuma colapsar), a aplicação Angular opera como PWA com armazenamento em **IndexedDB**.
* A validação do ingresso acontece localmente em **< 50ms**, gravando os acessos em uma fila `sync_queue`.
* Quando a rede retorna, um fluxo RxJS captura o evento `online` e executa um `batch-sync` contra o endpoint `POST /api/check-in/batch-sync` no Quarkus.
* Em caso de cópias impressas duplicadas (duplo check-in em portões distintos), o Quarkus utiliza o carimbo de data/hora (`scanned_at`) físico para determinar o acesso legítimo e notificar a equipe patrimonial via WebSocket de potencial fraude.

### 3. Integração Jibri (Jitsi) Webhook, Kafka e Transmissão Segura VOD
Para além do evento físico ao vivo, a plataforma suporta conferência e gravação sob demanda (VOD):
* **Endpoint Webhook Reativo (`POST /api/webhooks/jibri/upload-success`):** Recebe o callback do script Bash do Jibri assim que a gravação é concluída e enviada para o storage S3. A validação de segurança utiliza um Bearer Token secreto partilhado (`webhook.jibri.secret`), retornando `401 Unauthorized` em caso de token incorreto.
* **Transação Reativa e Emissão Kafka:** O Quarkus atualiza atomicamente o estado do evento de `LIVE` para `VOD_READY` e associa o `vodS3Url` via Hibernate Reactive Panache. Em seguida, emite uma mensagem reativa para o tópico Kafka `events.vod.ready` usando `MutinyEmitter<VodReadyEvent>`.
* **Orquestração Assíncrona via n8n:** O n8n subscreve o tópico `events.vod.ready`, consulta o banco ou Odoo para listar compradores com bilhete VIP/VOD e dispara e-mails hiper-personalizados em lotes sem onerar o core transacional do Quarkus.
* **Geração de S3 Pre-signed URLs em Sub-milissegundo (`GET /api/events/{eventId}/vod-access`):** O Quarkus valida se o utilizador autenticado possui bilhete válido e gera localmente uma URL pré-assinada temporária com o SDK da AWS (`S3Presigner` com TTL de 3 horas), sem chamadas de rede à AWS, garantindo resposta em < 1ms.
* **Proteção no Frontend Angular (`VodPlayerComponent`):** A URL segura é sanitizada com `DomSanitizer.bypassSecurityTrustUrl`, e o elemento de vídeo é blindado com `controlsList="nodownload"` e interceção de `contextmenu` (`preventRightClick`), impedindo cliques acidentais ou menus de download fácil.

---

## 📂 Estrutura de Diretórios

```
reactive-ticketing-platform/
├── docker-compose.yml           # Orquestração do PostgreSQL 16, Redis 7, Kafka & Zookeeper
├── infra/
│   ├── redis/redis.conf         # Configuração de persistência AOF e Keyspace Events (KEx)
│   └── kafka/init-topics.sh     # Script de inicialização dos tópicos (ticket.reserved, ticket.paid)
├── backend-quarkus/             # Camada Quarkus (Java 21, Mutiny, Panache Reactive, Redis, Kafka)
│   ├── src/main/java/com/ticket/
│   │   ├── domain/              # Event, Ticket, TicketStatus, User, TicketOrderEvent, CheckInEvent
│   │   ├── repository/          # Panache Reactive Repositories (EventRepository, TicketRepository)
│   │   ├── service/             # LockService, TicketService, VodAccessService, VodAccessResponse
│   │   ├── messaging/           # TicketEventProducer, TicketPaidConsumer, VodReadyEvent (Kafka)
│   │   └── web/                 # REST APIs (Event, Ticket, CheckIn, JibriWebhook, VodResource) e TicketSocket (/ws/tickets)
│   ├── src/main/resources/      # application.properties com perfis de teste e prod
│   ├── src/test/java/com/ticket/# Testes unitários para LockService e TicketService
│   └── pom.xml                  # Dependências Quarkus 3.19.1
├── frontend-angular/            # Camada Angular 19 (Signals, RxJS, PWA Offline-First)
│   ├── src/app/
│   │   ├── core/
│   │   │   ├── models/          # Modelos de dados e eventos WebSocket
│   │   │   ├── services/        # WebSocketService e ApiService
│   │   │   └── store/           # TicketStore com Angular Signals e Computed Metrics
│   │   ├── features/
│   │   │   ├── event-booking/   # Grelha de lugares ao vivo e reserva com hold de 10 min
│   │   │   ├── checkout/        # Contagem decrescente do hold e confirmação de pagamento
│   │   │   └── check-in/        # Terminal PWA com simulação de offline e fila de sync
│   │   ├── shared/              # Cards de métricas e visualização de lotação
│   │   └── app.component.ts     # Shell com alternância reativa de módulos
│   ├── tests/store.test.mjs     # Testes automatizados de transição de estado e batching
│   ├── package.json             # Dependências Angular 19 e scripts
│   └── angular.json             # Configuração do builder e da aplicação
├── README.md                    # Documentação técnica em inglês e visão geral
└── TUTORIAL.md                  # Guia passo a passo em português
```

---

## 🚀 Execução Rápida

### 1. Iniciar Infraestrutura Local (Docker Compose)
```bash
cd reactive-ticketing-platform
docker compose up -d
```
Serviços provisionados:
* **PostgreSQL:** porta `5432` (`ticketing_db`)
* **Redis:** porta `6379`
* **Apache Kafka:** porta `9092`
* **Zookeeper:** porta `2181`
* **Prometheus:** porta `9090` (Scraping de `/q/metrics` a cada 10s)
* **Grafana:** porta `3000` (Painéis pré-provisionados para Distributed Lock e Kafka Lag)
* **Jaeger Tracing:** porta `16686` (UI) e `4317` (Coletor OTLP gRPC)

### 📊 Observabilidade em Tempo Real
A stack de observabilidade monitora os três pilares críticos durante picos extremos de acesso:
1. **Taxa de Conflito de Lock (Redis):** Monitora requisições `200 OK` vs `409 Conflict` em `/api/tickets/{id}/reserve`.
2. **Lag do Consumidor Kafka:** Acompanha o tempo de resposta entre a compra de ingressos (`ticket.paid`) e o processamento de QR Codes em background.
3. **Conexões WebSocket Ativas (Vert.x):** Quantifica o volume instantâneo de usuários conectados no mapa de assentos ao vivo.
4. **Rastreamento Distribuído (Jaeger OTel):** Rastreia o fluxo completo ponta a ponta (Angular -> Quarkus -> Redis -> Kafka -> Postgres).

### 2. Executar e Testar o Back-end (Quarkus)
```bash
cd reactive-ticketing-platform/backend-quarkus

# Executar testes unitários
mvn clean test

# Iniciar em modo de desenvolvimento
mvn quarkus:dev
```
O servidor inicializará na porta `8080`, expondo a API REST em `/api` e o canal WebSocket em `ws://localhost:8080/ws/tickets`.

### 3. Executar e Testar o Front-end (Angular)
```bash
cd reactive-ticketing-platform/frontend-angular

# Executar testes de estado e sincronização
npm test

# Iniciar o servidor de desenvolvimento
npm start
```
Acesse a aplicação no navegador em `http://localhost:4200`.

---

## 🧪 Verificação de Testes

* **Back-end:**
  ```bash
  mvn test -f reactive-ticketing-platform/backend-quarkus/pom.xml
  ```
  Valida o comportamento de *Distributed Lock* com o Redis, fluxo de reserva com hold de 10 minutos, confirmação assíncrona de compra e rejeição de check-ins duplicados ou não autorizados.

* **Front-end:**
  ```bash
  cd reactive-ticketing-platform/frontend-angular && npm test
  ```
  Valida as transições de estado via Angular Signals e o empacotamento da fila de sincronização em lote (*Batch Sync*).
