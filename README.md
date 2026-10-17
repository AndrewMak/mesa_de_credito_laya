# Motor de Decisão de Crédito Híbrido

Sistema de decisão de crédito em camadas: regras determinísticas (DynamoDB) → classificador local (Laya/GPU) → LLM (OpenRouter) → mesa humana.

## Arquitetura

```
┌─────────────────────────────────────────────────────┐
│                  Frontend (React)                    │
│         http://localhost:5173                        │
└──────────────────────┬──────────────────────────────┘
                       │ /api (proxy Vite)
┌──────────────────────▼──────────────────────────────┐
│          Orquestrador (Spring Boot)                  │
│         http://localhost:8082                        │
│                                                      │
│  Camada 1: Regras dinâmicas (DynamoDB Local)         │
│  Camada 2: Classificador Laya (GPU local)            │
│  Camada 3: LLM (OpenRouter API)                      │
│  Persistência: PostgreSQL (propostas + auditoria)    │
└──────┬──────────────┬───────────────┬───────────────┘
       │              │               │
  DynamoDB Local   Laya (GPU)     OpenRouter API
  :8000            :8001          (externa)
       │
  PostgreSQL
  :5432
```

## Pré-requisitos

| Ferramenta | Versão | Verificação |
|---|---|---|
| Java JDK | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker | 20.10+ | `docker --version` |
| Docker Compose | 2.0+ | `docker compose version` |
| Python | 3.10+ | `py --version` |
| Node.js | 18+ | `node --version` |
| GPU NVIDIA | CUDA 11+ | `nvidia-smi` |
| Conta OpenRouter | — | https://openrouter.ai (para Camada 3) |

## Execução Local

### 1. Infraestrutura (PostgreSQL + DynamoDB Local)

```bash
docker compose up -d
```

Validação:
```bash
docker ps
# Esperado: jev-postgres (healthy) e jev-dynamodb-local
```

### 2. Serviço Laya (Classificador Local - GPU)

```bash
cd laya-service

# Criar ambiente virtual (primeira vez)
py -m venv venv

# Ativar ambiente
venv\Scripts\activate        # Windows
# source venv/bin/activate   # Linux/macOS

# Instalar dependências
pip install -r requirements.txt

# Subir o serviço
venv\Scripts\uvicorn main:app --host 0.0.0.0 --port 8001
# ou: python -m uvicorn main:app --host 0.0.0.0 --port 8001
```

Validação:
```bash
curl http://localhost:8001/health
# Esperado: {"status":"UP", ...}
```

### 3. Backend (Orquestrador Spring Boot)

```bash
cd credit-engine

# Compilar
mvn compile

# Executar
mvn spring-boot:run
```

Validação:
```bash
curl http://localhost:8082/actuator/health
# Esperado: {"status":"UP"}
```

### 4. Frontend (React + Vite)

```bash
cd frontend

# Instalar dependências (primeira vez)
npm install

# Subir o dev server
npm run dev
```

Acesso: http://localhost:5173

## Configuração do OpenRouter (Camada 3)

### Obter chave
1. Criar conta em https://openrouter.ai
2. Ir em https://openrouter.ai/keys
3. Criar uma nova chave

### Configurar a chave

**Opção A — Variável de ambiente (recomendado):**
```powershell
# Windows PowerShell
$env:OPENROUTER_API_KEY = "sk-or-v1-..."

# Linux/macOS
export OPENROUTER_API_KEY="sk-or-v1-..."
```

**Opção B — application.yml:**
```yaml
app:
  openrouter:
    api-key: sk-or-v1-...
    model: meta-llama/llama-3.2-3b-instruct
```

> **Nota:** Após alterar a chave ou o modelo, é necessário reiniciar o backend (`mvn spring-boot:run`).

## Testando o Fluxo Completo

### Pelo Frontend
1. Acesse http://localhost:5173
2. Na aba **Solicitar**: preencha os dados e envie
3. Na aba **Mesa Humana**: aprove/rejeite propostas pendentes

### Pela API (curl)
```bash
# Solicitar crédito
curl -X POST http://localhost:8082/api/credit/decide \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "cli-001",
    "score": 600,
    "income": 6000,
    "requestedAmount": 5000,
    "hasActiveRestrictive": false,
    "hasRecentBankruptcy": false
  }'

# Listar propostas pendentes
curl http://localhost:8082/api/credit/proposals

# Ver trace completo de uma proposta (todas as camadas + decisões)
curl http://localhost:8082/api/credit/proposals/1/trace

# Decisão humana
curl -X PATCH http://localhost:8082/api/credit/proposals/1 \
  -H "Content-Type: application/json" \
  -d '{"decision": "APPROVED", "note": "Aprovado pela mesa"}'
```

### Trace da Proposta

O endpoint `GET /api/credit/proposals/{id}/trace` retorna o caminho completo da proposta:

```json
{
  "proposalId": 106,
  "clientId": "trace1",
  "status": "LLM_APPROVED",
  "steps": [
    { "step": "LAYER1", "detail": "Regras DynamoDB (SpEL)", "data": { "status": "GRAY_ZONE", "ruleId": null } },
    { "step": "LAYER2", "detail": "Laya (GPU local)", "data": { "complexity": "LOW", "confidence": 0.69, "answers": { "risk_level": {...}, "fraud_risk": {...} }, "routing": "LLM" } },
    { "step": "LAYER3", "detail": "LLM (OpenRouter)", "data": { "decision": "APPROVED", "confidence": 0.9, "opinion": "..." } },
    { "step": "FINAL", "detail": "Decisão final", "data": { "status": "LLM_APPROVED", "message": "..." } }
  ]
}
```

No frontend, clique em **"Ver Trace"** em qualquer proposta da mesa humana para visualizar o modal com timeline completa.

## Decisões Possíveis

| Status | Origem |
|---|---|
| `AUTO_APPROVED` | Camada 1 (regras DynamoDB) |
| `LLM_APPROVED` | Camada 3 (OpenRouter) — após Laya LOW |
| `REJECTED` | Camada 1 ou 3 |
| `HUMAN_REVIEW_PENDING` | Laya HIGH / LLM indisponível / confiança baixa / valor alto |
| `HUMAN_APPROVED` | Mesa humana |
| `HUMAN_REJECTED` | Mesa humana |

## Critérios de Decisão

### Camada 1 — Regras (DynamoDB `CreditRules`)
- **REJECTED**: score < 300, restritivo ativo, falência recente
- **APPROVED**: score > 850, ou (score ≥ 700 E renda ≥ 5000)
- **GRAY_ZONE**: demais casos

### Camada 2 — Laya (Classificador Local Multi-Sinal)

A Laya atua como **filtro de risco** dentro da zona cinzenta. Ela recebe um **texto narrativo** + **JSON estruturado** da proposta e responde **5 perguntas tipadas em um único forward pass** (~33ms na GPU):

| Pergunta | Tipo | Opções | Descrição |
|---|---|---|---|
| `risk_level` | `score` (1–5) | muito baixo → muito alto | Nível geral de risco |
| `fraud_risk` | `noul` (0–1) | sim/não | Indicadores de fraude |
| `income_stability` | `choice` | STABLE / IRREGULAR / UNVERIFIABLE | Estabilidade da renda |
| `documentation` | `noul` (0–1) | sim/não | Documentação completa |
| `complexity` | `choice` | LOW / HIGH | Complexidade da decisão |

**Como a Laya decide o roteamento:**

A decisão é baseada na **combinação de todos os sinais** (não apenas complexidade):

```
Se fraud_prob > 0.50 → HUMAN (risco de fraude)
Se risk_score ≥ 4.0  → HUMAN (risco muito alto)
Se complexity = HIGH → HUMAN (sinais mistos/ambíguos)
Se income = UNVERIFIABLE → HUMAN (renda não comprovada)
Se avg_confidence < 0.60 → HUMAN (baixa confiança do modelo)
Caso contrário → LLM (passou no filtro de risco)
```

**Resposta da API (`POST /classify`):**

```json
{
  "complexity": "LOW",
  "confidence": 0.7326,
  "answers": {
    "risk_level": { "type": "score", "score": 1.94, "confidence": 0.51, "probabilities": { "2": 0.51, ... } },
    "fraud_risk": { "type": "noul", "noul": 0.14, "confidence": 0.86 },
    "income_stability": { "type": "choice", "choice": "STABLE", "confidence": 0.60 },
    "documentation": { "type": "noul", "noul": 0.89, "confidence": 0.89 },
    "complexity": { "type": "choice", "choice": "LOW", "confidence": 0.80 }
  },
  "routing": "LLM",
  "routing_reason": "Low risk, stable income, straightforward",
  "model": "laya-rl-agent",
  "device": "cuda",
  "latency_ms": 67.13
}
```

**Texto narrativo enviado (gerado pelo Java):**

O orquestrador monta um texto descritivo derivado dos dados + JSON estruturado:

```json
{
  "client_id": "cli-001",
  "score": 600,
  "income": 6000,
  "requested_amount": 5000,
  "has_active_restrictive": false,
  "has_recent_bankruptcy": false
}
```

A narrativa textual varia conforme o perfil:
- **Limpo + renda ≥ 4000**: "Perfil simples, limpo, sinais consistentes, baixo risco."
- **Limpo + renda < 4000**: "Perfil com sinais mistos, renda moderada, zona cinzenta."
- **Com flags**: "Perfil complexo, sinais mistos, ambiguo, precisa revisao humana."

### Camada 3 — LLM (OpenRouter)
- Gera parecer técnico + sugestão + confiança
- **Trava**: confiança < 0,60 ou valor ≥ R$ 100.000 → humano

## Parar Tudo

```bash
# Frontend
Ctrl+C no terminal do Vite

# Backend
Ctrl+C no terminal do Maven

# Laya
Ctrl+C no terminal do uvicorn

# Infraestrutura
docker compose down
```

## Estrutura do Projeto

```
jev/
├── docker-compose.yml          # PostgreSQL + DynamoDB Local
├── credit-engine/              # Backend Spring Boot
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/jev/creditengine/
│       │   ├── config/         # DynamoDB, OpenRouter, CORS
│       │   ├── dto/            # Payloads (ClientProfile, etc.)
│       │   ├── laya/           # Cliente HTTP do Laya
│       │   ├── llm/            # Cliente OpenRouter
│       │   ├── orchestration/  # CreditOrchestratorService
│       │   ├── persistence/    # JPA (CreditProposal, AuditLog)
│       │   ├── rules/          # Motor SpEL + Cache + DynamoDB
│       │   └── web/            # Controllers REST
│       └── main/resources/application.yml
├── laya-service/               # Classificador Python/FastAPI
│   ├── main.py                 # 5 perguntas tipadas + roteamento multi-sinal
│   └── requirements.txt
└── frontend/                   # React + Vite
    ├── src/
    │   ├── pages/CreditRequest.jsx
    │   ├── pages/ApprovalDesk.jsx
    │   └── App.jsx
    └── vite.config.js          # Proxy /api → :8082
```
