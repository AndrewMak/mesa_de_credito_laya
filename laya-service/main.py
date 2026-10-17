import os
import time
import threading
from contextlib import asynccontextmanager

# Avoid TensorFlow/abseil deadlocks at import time; Laya runs on PyTorch.
os.environ.setdefault("USE_TF", "0")
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")
os.environ.setdefault("HF_HUB_DISABLE_SYMLINKS_WARNING", "1")

import laya  # noqa: E402
from fastapi import FastAPI, HTTPException  # noqa: E402
from pydantic import BaseModel, Field  # noqa: E402

LAYA_DEVICE = os.getenv("LAYA_DEVICE", "cuda")
LAYA_MODEL = os.getenv("LAYA_MODEL", "convaiinnovations/laya")

COMPLEXITY_QUESTION = {
    "risk_level": {
        "type": "score",
        "instructions": "Overall credit risk level of this proposal",
        "criteria": ["very low", "low", "moderate", "high", "very high"],
    },
    "fraud_risk": {
        "type": "noul",
        "instructions": "Are there indicators of fraud or misrepresentation?",
    },
    "income_stability": {
        "type": "choice",
        "instructions": "How stable and verifiable is the client's income?",
        "criteria": {
            "STABLE": "stable, proven, consistent income",
            "IRREGULAR": "irregular or partially verified",
            "UNVERIFIABLE": "unverifiable or inconsistent",
        },
    },
    "documentation": {
        "type": "noul",
        "instructions": "Is the documentation complete and consistent?",
    },
    "complexity": {
        "type": "choice",
        "instructions": (
            "Classify the overall complexity of this credit proposal: "
            "LOW (straightforward) or HIGH (mixed signals, needs deeper review)."
        ),
        "criteria": {
            "LOW": "straightforward, consistent signals, low risk",
            "HIGH": "mixed signals, ambiguous, gray zone",
        },
    },
}

_agent = None
_lock = threading.Lock()


def _load_agent():
    global _agent
    _agent = laya.load(LAYA_MODEL, device=LAYA_DEVICE)
    return _agent


@asynccontextmanager
async def lifespan(app: FastAPI):
    _load_agent()
    yield


app = FastAPI(title="Laya Local Classifier", version="1.0.0", lifespan=lifespan)


class ClassifyRequest(BaseModel):
    text: str = Field(..., description="Descriptive textual payload of the proposal/profile.")


class ClassifyResponse(BaseModel):
    complexity: str
    confidence: float
    probabilities: dict
    answers: dict
    routing: str
    routing_reason: str
    model: str
    device: str
    latency_ms: float


@app.get("/health")
def health():
    return {
        "status": "UP",
        "model": LAYA_MODEL,
        "device": LAYA_DEVICE,
        "loaded": _agent is not None,
    }


@app.post("/classify", response_model=ClassifyResponse)
def classify(payload: ClassifyRequest):
    if _agent is None:
        raise HTTPException(status_code=503, detail="Model not loaded yet")
    state = payload.text.strip()
    if not state:
        raise HTTPException(status_code=400, detail="text must not be empty")

    started = time.perf_counter()
    with _lock:
        result = _agent.predict(state, COMPLEXITY_QUESTION)
    elapsed_ms = (time.perf_counter() - started) * 1000.0

    answers = result.get("answers", {})
    parsed_answers = {}
    overall_confidence = 0.0
    confidence_count = 0

    for key, ans in answers.items():
        entry = {
            "type": ans.get("type"),
            "confidence": float(ans.get("answer_confidence", ans.get("confidence", 0.0))),
            "probabilities": {k: float(v) for k, v in ans.get("probabilities", {}).items()},
        }
        if ans.get("type") == "choice":
            entry["choice"] = str(ans.get("choice", "")).upper()
        elif ans.get("type") == "score":
            entry["score"] = float(ans.get("score", 0.0))
        elif ans.get("type") == "noul":
            entry["noul"] = float(ans.get("noul", 0.0))

        parsed_answers[key] = entry
        overall_confidence += entry["confidence"]
        confidence_count += 1

    avg_confidence = overall_confidence / confidence_count if confidence_count > 0 else 0.0

    # Determine routing based on multiple signals
    complexity = parsed_answers.get("complexity", {}).get("choice", "HIGH")
    risk_score = parsed_answers.get("risk_level", {}).get("score", 3.0)
    fraud_prob = parsed_answers.get("fraud_risk", {}).get("noul", 0.0)
    income = parsed_answers.get("income_stability", {}).get("choice", "IRREGULAR")
    docs_ok = parsed_answers.get("documentation", {}).get("noul", 0.0)

    # Multi-signal routing decision
    if fraud_prob > 0.5 or risk_score >= 4.0:
        routing = "HUMAN"
        routing_reason = "High fraud risk or very high risk score"
    elif complexity == "HIGH" or income == "UNVERIFIABLE":
        routing = "HUMAN"
        routing_reason = "High complexity or unverifiable income"
    elif avg_confidence < 0.60:
        routing = "HUMAN"
        routing_reason = "Low model confidence"
    elif complexity == "LOW" and risk_score <= 2.0 and income == "STABLE":
        routing = "LLM"
        routing_reason = "Low risk, stable income, straightforward"
    else:
        routing = "LLM"
        routing_reason = "Passes risk screening"

    return ClassifyResponse(
        complexity=complexity,
        confidence=round(avg_confidence, 4),
        probabilities={k: v.get("probabilities", {}) for k, v in parsed_answers.items()},
        answers=parsed_answers,
        routing=routing,
        routing_reason=routing_reason,
        model=result.get("model", LAYA_MODEL),
        device=LAYA_DEVICE,
        latency_ms=round(elapsed_ms, 2),
    )
