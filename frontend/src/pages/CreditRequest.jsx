import { useState } from "react";

const API = "";

function statusChip(status) {
  const map = {
    AUTO_APPROVED: "chip chip--success",
    LOCAL_APPROVED: "chip chip--success",
    LLM_APPROVED: "chip chip--success",
    REJECTED: "chip chip--error",
    HUMAN_REJECTED: "chip chip--error",
    HUMAN_REVIEW_PENDING: "chip chip--warning",
    PENDING: "chip chip--primary",
  };
  return map[status] || "chip";
}

export default function CreditRequest() {
  const [form, setForm] = useState({
    clientId: "",
    score: 650,
    income: 4000,
    requestedAmount: 5000,
    hasActiveRestrictive: false,
    hasRecentBankruptcy: false,
  });
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const update = (k, v) => setForm((f) => ({ ...f, [k]: v }));

  const submit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const res = await fetch(`${API}/api/credit/decide`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          clientId: form.clientId || "cliente",
          score: Number(form.score),
          income: Number(form.income),
          requestedAmount: Number(form.requestedAmount),
          hasActiveRestrictive: form.hasActiveRestrictive,
          hasRecentBankruptcy: form.hasRecentBankruptcy,
        }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setResult(await res.json());
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const reset = () => {
    setResult(null);
    setError(null);
  };

  return (
    <>
      <header className="page-header">
        <h1 className="page-title">Solicitação de Crédito</h1>
        <p className="page-subtitle">
          Preencha o perfil. O motor decide em camadas: regras → classificador local → LLM, com trava para revisão humana.
        </p>
      </header>

      <div className="grid-2">
        <section className="card">
          <div className="card__body">
            <h3 style={{ fontSize: 20, marginBottom: "var(--sp-4)" }}>Dados do cliente</h3>
            <form onSubmit={submit}>
              <div className="field">
                <label className="field-label">Identificador do cliente</label>
                <input
                  className="input"
                  value={form.clientId}
                  placeholder="ex: cli-001"
                  onChange={(e) => update("clientId", e.target.value)}
                />
              </div>
              <div className="field">
                <label className="field-label">Score (0–1000)</label>
                <input
                  className="input"
                  type="number"
                  value={form.score}
                  onChange={(e) => update("score", e.target.value)}
                />
              </div>
              <div className="field">
                <label className="field-label">Renda (R$)</label>
                <input
                  className="input"
                  type="number"
                  value={form.income}
                  onChange={(e) => update("income", e.target.value)}
                />
              </div>
              <div className="field">
                <label className="field-label">Valor solicitado (R$)</label>
                <input
                  className="input"
                  type="number"
                  value={form.requestedAmount}
                  onChange={(e) => update("requestedAmount", e.target.value)}
                />
              </div>
              <div className="field" style={{ display: "flex", gap: "var(--sp-6)", flexWrap: "wrap" }}>
                <label className="toggle">
                  <input
                    type="checkbox"
                    checked={form.hasActiveRestrictive}
                    onChange={(e) => update("hasActiveRestrictive", e.target.checked)}
                  />
                  Restritivo ativo
                </label>
                <label className="toggle">
                  <input
                    type="checkbox"
                    checked={form.hasRecentBankruptcy}
                    onChange={(e) => update("hasRecentBankruptcy", e.target.checked)}
                  />
                  Falência recente
                </label>
              </div>
              <div style={{ display: "flex", gap: "var(--sp-3)", marginTop: "var(--sp-2)" }}>
                <button className="btn btn--primary btn--lg" type="submit" disabled={loading}>
                  {loading ? <span className="spinner" /> : null}
                  {loading ? "Avaliando…" : "Solicitar crédito"}
                </button>
                <button className="btn btn--secondary btn--lg" type="button" onClick={reset}>
                  Limpar
                </button>
              </div>
            </form>
          </div>
        </section>

        <section className="card" style={{ alignSelf: "start" }}>
          <div className="card__body">
            <h3 style={{ fontSize: 20, marginBottom: "var(--sp-4)" }}>Resultado da decisão</h3>
            {!result && !error && (
              <div className="empty">Preencha e envie uma proposta para ver o resultado.</div>
            )}
            {error && (
              <div className="chip chip--error" style={{ width: "100%", justifyContent: "center" }}>
                Erro: {error}
              </div>
            )}
            {result && (
              <>
                <div style={{ display: "flex", alignItems: "center", gap: "var(--sp-3)", marginBottom: "var(--sp-4)" }}>
                  <span className={statusChip(result.finalStatus)} style={{ fontSize: 13, padding: "6px 14px" }}>
                    {result.finalStatus}
                  </span>
                  <span className="mono">proposta #{result.proposalId}</span>
                </div>
                <p style={{ color: "var(--color-text-secondary)", marginBottom: "var(--sp-4)" }}>
                  {result.message}
                </p>
                <div className="list" style={{ border: "1px solid var(--color-border)", borderRadius: "var(--r-lg)" }}>
                  <div className="list-row">
                    <span style={{ color: "var(--color-text-secondary)", fontSize: 13 }}>Camada 1</span>
                    <span className="mono">{result.layer1Status || "—"} {result.layer1RuleId ? `(${result.layer1RuleId})` : ""}</span>
                  </div>
                  <div className="list-row">
                    <span style={{ color: "var(--color-text-secondary)", fontSize: 13 }}>Camada 2 (Laya)</span>
                    <span className="mono">{result.layer2Complexity || "—"} {result.layer2Confidence != null ? `(${result.layer2Confidence.toFixed(2)})` : ""}</span>
                  </div>
                  <div className="list-row">
                    <span style={{ color: "var(--color-text-secondary)", fontSize: 13 }}>Camada 3 (LLM)</span>
                    <span className="mono">{result.layer3Decision || "—"} {result.layer3Confidence != null ? `(${result.layer3Confidence.toFixed(2)})` : ""}</span>
                  </div>
                </div>
                {result.layer3Decision === "HUMAN_REVIEW" && (
                  <p style={{ marginTop: "var(--sp-4)", fontSize: 13, color: "var(--color-warning)" }}>
                    LLM indisponível ou trava de segurança acionada — proposta enviada à mesa humana.
                  </p>
                )}
              </>
            )}
          </div>
        </section>
      </div>
    </>
  );
}
