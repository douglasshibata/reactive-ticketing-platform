# 📘 Tutorial Passo a Passo: Plataforma Reativa de Venda de Ingressos e Credenciamento

Este tutorial conduz o desenvolvedor e o arquiteto de software pela implementação, execução e validação da plataforma reativa de bilhética de alta concorrência utilizando **Quarkus 3.x (Java 21, Mutiny, Vert.x)** e **Angular 19 (Signals, RxJS, PWA Offline-First)**.

---

## 📋 Pré-requisitos
* **Java 21+** (`java -version`) e **Maven 3.9+** (`mvn -version`)
* **Node.js 18+** (`node -v`) e **npm 9+**
* **Docker** e **Docker Compose** (`docker compose version`)

---

## 1. Subida da Infraestrutura com Docker Compose

Na raiz da pasta `reactive-ticketing-platform`, execute:
```bash
docker compose up -d
```

Verifique o estado dos containers:
```bash
docker compose ps
```
Você deverá ver os serviços em execução:
* `ticketing-postgres` (PostgreSQL 16)
* `ticketing-redis` (Redis 7 com Keyspace Notifications ativadas)
* `ticketing-zookeeper` (Zookeeper 7.5)
* `ticketing-kafka` (Kafka 7.5)
* `ticketing-prometheus` (Prometheus 2.48 na porta 9090)
* `ticketing-grafana` (Grafana 10.2 na porta 3000, credenciais `admin`/`admin`)
* `ticketing-jaeger` (Jaeger 1.51 na porta 16686 e OTLP 4317)

---

## 2. Entendendo o Algoritmo de Distributed Lock no Redis

O serviço `SeatLockService` / `LockService` no Quarkus executa o comando:
```text
SET ticket:<id>:lock <userId> NX EX 600
```
* **NX (Not Exists):** Garante atomicidade. Se 5.000 pessoas clicarem simultaneamente no mesmo ingresso, apenas 1 requisição recebe `true`; todas as outras 4.999 recebem `false` em menos de 1ms.
* **EX 600:** Tempo de vida (TTL) de 10 minutos. Caso o comprador não conclua o pagamento ou feche o navegador, o Redis expira a chave automaticamente e emite o evento de liberação sem sobrecarregar o banco relacional.

---

## 3. Compilando e Executando os Testes do Back-end Quarkus

Execute a suíte de testes unitários:
```bash
cd backend-quarkus
mvn clean test
```
Resultados esperados:
* `LockServiceTest`: valida a aquisição e liberação atômica da chave.
* `TicketServiceTest`: testa a reserva, compra com geração de QR Code, validação de check-in na catraca e prevenção contra fraude de dupla entrada (*duplicate check-in*).

Para rodar em modo de desenvolvimento contínuo (Live Reload):
```bash
mvn quarkus:dev
```
Acesse a documentação OpenAPI / Swagger UI em `http://localhost:8080/q/swagger-ui`.

---

## 4. Executando e Testando a Aplicação Front-end Angular 19

Navegue até a pasta do front-end:
```bash
cd ../frontend-angular
npm test
```
Os testes automatizados em `tests/store.test.mjs` validam:
1. As mutações de status do Signal `seats()` (`AVAILABLE` -> `LOCKED` -> `SOLD` -> `CHECKED_IN`).
2. O empacotamento da fila de leitura offline (*Offline Queue*) em um payload estruturado de *Batch Sync*.

Para iniciar a interface web:
```bash
npm start
```
Abra o navegador em `http://localhost:4200`.

---

## 5. Observabilidade e Rastreamento Distribuído (Prometheus, Grafana e Jaeger)

A plataforma exporta métricas em `/q/metrics` e envia traces via gRPC OpenTelemetry para o Jaeger.

* **Acessar Grafana:** abra `http://localhost:3000` (login `admin`/`admin`). O dashboard *Reactive Ticketing - High Concurrency Observability* é carregado automaticamente com métricas de:
  * Proporção de Locks Conflitados (`409 Conflict`) vs Aprovados (`200 OK`)
  * Lag de mensagens no Kafka (`ticket.paid`)
  * Conexões ativas no Event Loop Vert.x
* **Acessar Jaeger Tracing:** abra `http://localhost:16686` para inspecionar os Trace IDs, visualizando a latência individual das operações entre o Angular, Quarkus, Redis e Kafka.

---

## 6. Simulação Prática dos Cenários Críticos

### Cenário A: Reserva Concorrente (Anti-Double Booking)
1. Abra duas abas do navegador em `http://localhost:4200`.
2. Na Aba 1, clique em "Bloquear 10m" no ingresso **VIP-101**.
3. Imediatamente na Aba 2, repare que o botão do **VIP-101** muda instantaneamente para cinzento ("Em Reserva") via WebSocket, sem que você precise recarregar a página.
4. Na Aba 1, navegue para "Checkout & Pagamento" e observe o cronômetro regressivo de 10 minutos.

### Cenário B: Pagamento Assíncrono Desacoplado
1. Preencha os dados no formulário de Checkout e clique em "Pagar & Emitir Bilhete".
2. O Quarkus aprova o pedido, remove o lock do Redis e lança uma mensagem no tópico `ticket.paid` do Kafka.
3. O consumidor em background simula a renderização do PDF e o envio do e-mail sem travar a requisição HTTP.

### Cenário C: Credenciamento na Portaria e Tolerância a Falhas de Rede (Offline)
1. Acesse a aba **"Credenciamento Portaria (Staff PWA)"**.
2. Clique no botão **"Simular Queda de Wi-Fi"**. A interface indicará o modo `MODO OFFLINE (IndexedDB)`.
3. Valide o ingresso **VIP-101** digitando ou clicando no atalho. O acesso será liberado em **< 35ms** e armazenado localmente na fila.
4. Tente escanear o mesmo ingresso novamente: o sistema rejeitará o acesso localmente informando que o ingresso já foi utilizado.
5. Clique em **"Restabelecer Conexão"** e em seguida em **"Sincronizar Fila em Lote (Batch Sync)"**: o lote de acessos será reconciliado de uma só vez com o servidor Quarkus.

---

## 7. Webhook Jibri, Kafka Streaming e VOD Seguro no S3

### A. Simulação do Webhook Jibri (Pós-Gravação)
Quando a gravação de uma sala de conferência/evento Jitsi é concluída pelo Jibri e enviada ao bucket S3, o script emite a requisição:
```bash
curl -X POST http://localhost:8080/api/webhooks/jibri/upload-success \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer default_dev_secret_only" \
  -d '{
    "roomId": "room-1",
    "s3Url": "recordings/event-1/master.mp4",
    "status": "FINISHED"
  }'
```
* **Resposta Esperada:** `HTTP 200 OK` com `{"message": "VOD atualizado e notificado"}`.
* **Segurança:** Se o cabeçalho `Authorization` for omitido ou incorreto, o endpoint responde silenciosamente com `HTTP 401 Unauthorized`.
* **Kafka:** O evento `VodReadyEvent` é emitido de forma não-bloqueante no tópico `events.vod.ready`.

### B. Obtenção de URL Pré-assinada (S3 Pre-signed URL)
O participante autenticado solicita acesso ao VOD:
```bash
curl -X GET http://localhost:8080/api/events/1/vod-access \
  -H "Authorization: Bearer <SEU_JWT_TOKEN_DE_UTILIZADOR>"
```
* **Comportamento:** O Quarkus verifica se o usuário possui ingresso com status `SOLD` ou `CHECKED_IN` para o evento correspondente.
* Em caso positivo, o `S3Presigner` assina criptograficamente a URL em memória com expiração de 3 horas.
* Se o usuário não possuir ingresso válido, recebe `HTTP 403 Forbidden`.

### C. Visualização no Leitor Seguro Angular
Acesse a aba **"🎥 Transmissão VOD Segura (S3)"** na interface web (`http://localhost:4200`):
* O componente solicita a URL segura via `VodService`.
* O leitor de vídeo desativa o download nativo (`controlsList="nodownload"`) e anula o clique direito para evitar salvamento direto do arquivo de mídia.
