import { useCallback, useEffect, useState } from "react";

const API = "";

const statuses = [
  { key: "HUMAN_REVIEW_PENDING", label: "Pendentes" },
  { key: "HUMAN_APPROVED", label: "Aprovados (humano)" },
  { key: "HUMAN_REJECTED", label: "Rejeitados (humano)" },
  { key: "ALL", label: "Todas" },
];

function statusChip(status) {
  const map = {
    AUTO_APPROVED: "chip chip--success",
    LOCAL_APPROVED: "chip chip--success",
    LLM_APPROVED: "chip chip--success",
    HUMAN_APPROVED: "chip chip--success",
    REJECTED: "chip chip--error",
    HUMAN_REJECTED: "chip chip--error",
    HUMAN_REVIEW_PENDING: "chip chip--warning",
    PENDING: "chip chip--primary",
  };
  return map[status] || "chip";
}

function statusColor(status) {
  const map = {
    APPROVED: "#10B981",
    REJECTED: "#EF4444",
    GRAY_ZONE: "#F59E0B",
    HUMAN_REVIEW: "#6B6B6B",
    LLM_APPROVED: "#10B981",
    HUMAN_APPROVED: "#10B981",
    HUMAN_REVIEW_PENDING: "#F59E0B",
    HUMAN_REJECTED: "#EF4444",
    PENDING: "#9C9C9C",
    LOW: "#10B981",
    HIGH: "#EF4444",
    STABLE: "#10B981",
    IRREGULAR: "#F59E0B",
    UNVERIFIABLE: "#EF4444",
  };
  return map[status] || "#9C9C9C";
}

function TraceModal({ proposalId, onClose }) {
  const [trace, setTrace] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch(`${API}/api/credit/proposals/${proposalId}/trace`)
      .then((r) => r.json())
      .then((data) => {
        setTrace(data);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, [proposalId]);

  const stepLabels = {
    LAYER1: "Camada 1 • Regras (DynamoDB)",
    LAYER2: "Camada 2 • Classificador (Laya GPU)",
    LAYER3: "Camada 3 • LLM (OpenRouter)",
    FINAL: "Decisão Final",
  };

  if (loading) {
    return (
      <div className="trace-modal-overlay" onClick={onClose}>
        <div className="trace-modal" onClick={(e) => e.stopPropagation()}>
          <div className="spinner-overlay">
            <span className="spinner" style={{ width: 20, height: 20 }} />
            <span style={{ marginLeft: "var(--sp-3)", fontSize: 13, color: "var(--color-text-secondary)" }}>
              Carregando análise do trace…
            </span>
          </div>
        </div>
      </div>
    );
  }

  if (!trace) return null;

  const finalColor = statusColor(trace.status);

  return (
    <div className="trace-modal-overlay" onClick={onClose}>
      <div className="trace-modal" onClick={(e) => e.stopPropagation()}>
        <div className="trace-modal-header">
          <div style={{ display: "flex", alignItems: "center", gap: "var(--sp-3)" }}>
            <span className="trace-modal-dot" style={{ background: finalColor }}></span>
            <h3>Trace da Proposta #{trace.proposalId}</h3>
          </div>
          <button className="btn btn--ghost btn--sm" onClick={onClose}>✕</button>
        </div>

        <div className="trace-summary">
          <div className="trace-summary-status">
            <span className="trace-status-chip" style={{ background: finalColor, color: "#fff" }}>
              {trace.status}
            </span>
            <span className="trace-status-text">{trace.message}</span>
          </div>
          <div className="trace-flow">
            {["LAYER1", "LAYER2", "LAYER3", "FINAL"].map((k, i) => {
              const step = trace.steps.find((s) => s.step === k);
              const done = !!step;
              return (
                <div key={k} style={{ display: "flex", alignItems: "center", gap: "var(--sp-2)" }}>
                  <div
                    className="trace-flow-node"
                    style={{
                      width: 24,
                      height: 24,
                      borderRadius: "9999px",
                      background: done ? finalColor : "#E8E8EC",
                      color: done ? "#fff" : "#9C9C9C",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      fontSize: 10,
                      fontWeight: 600,
                    }}
                  >
                    {i + 1}
                  </div>
                  {i < 3 && (
                    <div
                      className="trace-flow-line"
                      style={{
                        height: 2,
                        flex: 1,
                        background: done ? finalColor : "#E8E8EC",
                      }}
                    />
                  )}
                  <span
                    style={{
                      fontSize: 11,
                      color: done ? "var(--color-text)" : "var(--color-neutral)",
                      fontWeight: done ? 600 : 400,
                    }}
                  >
                    {stepLabels[k]}
                  </span>
                </div>
              );
            })}
          </div>
        </div>

        <div className="trace-steps">
          {trace.steps.map((s, i) => {
            const data = s.data || {};
            const isLayer2 = s.step === "LAYER2";
            const isLayer3 = s.step === "LAYER3";
            const stepStatus =
              s.step === "FINAL"
                ? trace.status
                : s.step === "LAYER1"
                ? data.status || "—"
                : s.step === "LAYER2"
                ? data.complexity || "—"
                : data.decision || "—";

            return (
              <div key={i} className="trace-step-card">
                <div className="trace-step-header">
                  <span className="trace-step-number">{i + 1}</span>
                  <span className="trace-step-title">{stepLabels[s.step]}</span>
                  <span className="trace-step-pill" style={{ background: statusColor(stepStatus) + "1a", color: statusColor(stepStatus) }}>
                    {stepStatus}
                  </span>
                </div>
                <div className="trace-step-detail">{s.detail}</div>

                {isLayer2 && data.answers && (
                  <div className="trace-laya-grid">
                    {Object.entries(data.answers).map(([key, ans]) => {
                      const labelMap = {
                        risk_level: "Risco",
                        fraud_risk: "Fraude",
                        income_stability: "Renda",
                        documentation: "Documentos",
                        complexity: "Complexidade",
                      };
                      const label = labelMap[key] || key;
                      const type = ans.type || "choice";
                      let value = "—";
                      let detail = "";
                      let barValue = 0;

                      if (type === "score") {
                        value = ans.score != null ? ans.score.toFixed(1) : "—";
                        detail = "score 1\u20135";
                        barValue = ((ans.score || 0) / 5) * 100;
                      } else if (type === "choice") {
                        value = ans.choice || "—";
                        detail = Object.entries(ans.probabilities || {}).map(
                          ([k, v]) => k + ": " + (v * 100).toFixed(0) + "%"
                        ).join(" \u2022 ");
                        barValue = (ans.probabilities && ans.probabilities[ans.choice] ? ans.probabilities[ans.choice] : 0) * 100;
                      } else if (type === "noul") {
                        value = ans.noul != null ? (ans.noul * 100).toFixed(1) + "%" : "—";
                        detail = "probabilidade de sim";
                        barValue = (ans.noul || 0) * 100;
                      }

                      return (
                        <div key={key} className="trace-laya-card">
                          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "start", marginBottom: "var(--sp-2)" }}>
                            <span style={{ fontSize: 11, fontWeight: 600, color: "var(--color-text-secondary)", textTransform: "uppercase", letterSpacing: "0.04em" }}>
                              {label}
                            </span>
                            <span style={{ fontSize: 12, fontWeight: 600, color: statusColor(value) }}>{value}</span>
                          </div>
                          <div style={{ fontSize: 11, color: "var(--color-neutral)", marginBottom: "var(--sp-2)" }}>{detail}</div>
                          <div
                            style={{
                              width: "100%",
                              height: 6,
                              background: "#E8E8EC",
                              borderRadius: "3px",
                              overflow: "hidden",
                            }}
                          >
                            <div
                              style={{
                                width: barValue + "%",
                                height: "100%",
                                background: statusColor(value),
                              }}
                            />
                          </div>
                        </div>
                      );
                    })}
                    <div className="trace-laya-routing">
                      <span style={{ fontSize: 11, color: "var(--color-text-secondary)", textTransform: "uppercase", letterSpacing: "0.04em", fontWeight: 600 }}>
                        Roteamento da Camada 2
                      </span>
                      <div style={{ marginTop: "var(--sp-1)", display: "flex", alignItems: "center", gap: "var(--sp-2)" }}>
                        <span style={{ fontSize: 13, fontWeight: 600, color: statusColor(data.routing || "HUMAN") }}>
                          {data.routing || "\u2014"}
                        </span>
                        <span style={{ fontSize: 12, color: "var(--color-text-secondary)" }}>{data.routing_reason || "\u2014"}</span>
                      </div>
                    </div>
                  </div>
                )}

                {isLayer3 && (
                  <div className="trace-laya-grid" style={{ gridTemplateColumns: "1fr" }}>
                    <div className="trace-laya-card">
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "start", marginBottom: "var(--sp-2)" }}>
                        <span style={{ fontSize: 11, fontWeight: 600, color: "var(--color-text-secondary)", textTransform: "uppercase", letterSpacing: "0.04em" }}>
                          Decisão LLM
                        </span>
                        <span style={{ fontSize: 12, fontWeight: 600, color: statusColor(data.decision || "\u2014") }}>{data.decision || "\u2014"}</span>
                      </div>
                      <div style={{ fontSize: 11, color: "var(--color-neutral)", marginBottom: "var(--sp-2)" }}>
                        Confiança: {(data.confidence != null ? data.confidence.toFixed(2) : "\u2014")}
                      </div>
                      <div style={{ fontSize: 11, color: "var(--color-neutral)", marginBottom: "var(--sp-2)" }}>
                        Fallback: {String(data.fallback)}
                      </div>
                      <div style={{ fontSize: 11, color: "var(--color-neutral)", marginBottom: "var(--sp-2)" }}>{data.reason || "\u2014"}</div>
                    </div>
                    {data.opinion && (
                      <div className="trace-laya-card" style={{ gridColumn: "1 / -1", borderLeft: "3px solid " + finalColor }}>
                        <span style={{ fontSize: 11, fontWeight: 600, color: "var(--color-text-secondary)", textTransform: "uppercase", letterSpacing: "0.04em", display: "block", marginBottom: "var(--sp-1)" }}>
                          Parecer t\u00e9cnico
                        </span>
                        <div style={{ fontSize: 13, color: "var(--color-text)", lineHeight: 1.5, whiteSpace: "pre-wrap" }}>{data.opinion}</div>
                      </div>
                    )}
                  </div>
                )}

                {!isLayer2 && !isLayer3 && s.step !== "FINAL" && (
                  <div className="trace-fields">
                    {Object.entries(data).map(([k, v]) => (
                      <div key={k} className="trace-field">
                        <span className="trace-field-key">{k}</span>
                        <span className="trace-field-value">{String(v ?? "\u2014")}</span>
                      </div>
                    ))}
                  </div>
                )}

                {s.step === "FINAL" && (
                  <div className="trace-fields">
                    <div className="trace-field">
                      <span className="trace-field-key">Status final</span>
                      <span className="trace-field-value" style={{ color: finalColor, fontWeight: 600 }}>{data.status}</span>
                    </div>
                    <div className="trace-field">
                      <span className="trace-field-key">Mensagem</span>
                      <span className="trace-field-value" style={{ whiteSpace: "pre-wrap" }}>{data.message}</span>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

export default function ApprovalDesk() {
  const [status, setStatus] = useState("HUMAN_REVIEW_PENDING");
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [notes, setNotes] = useState({});
  const [acting, setActing] = useState(null);
  const [traceId, setTraceId] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const url = status === "ALL"
        ? `${API}/api/credit/proposals`
        : `${API}/api/credit/proposals?status=${status}`;
      const res = await fetch(url);
      if (!res.ok) throw new Error("HTTP " + res.status);
      setItems(await res.json());
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => { load(); }, [load]);

  const decide = async (id, decision) => {
    setActing(id);
    try {
      await fetch(`${API}/api/credit/proposals/${id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ decision, decision: decision, note: notes[id] || "" }),
      });
      await load();
    } finally {
      setActing(null);
    }
  };

  return (
    <>
      <header className="page-header">
        <h1 className="page-title">Mesa de Aprovação Humana</h1>
        <p className="page-subtitle">
          Propostas desviadas para revisão (LLM indisponível, baixa confiança ou valor alto).
        </p>
      </header>

      <div style={{ display: "flex", gap: "var(--sp-2)", marginBottom: "var(--sp-6)", flexWrap: "wrap" }}>
        {statuses.map((s) => (
          <button
            key={s.key}
            className={"btn btn--sm " + (status === s.key ? "btn--primary" : "btn--secondary")}
            onClick={() => setStatus(s.key)}
          >
            {s.label}
          </button>
        ))}
        <button className="btn btn--ghost btn--sm" onClick={load} style={{ marginLeft: "auto" }}>
          Atualizar
        </button>
      </div>

      {loading && <div className="empty">Carregando…</div>}

      {!loading && items.length === 0 && (
        <div className="card"><div className="card__body empty">Nenhuma proposta nesse filtro.</div></div>
      )}

      <div style={{ display: "grid", gap: "var(--sp-4)" }}>
        {items.map((p) => (
          <div className="card" key={p.id}>
            <div className="card__body">
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "var(--sp-4)", flexWrap: "wrap" }}>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: "var(--sp-3)", marginBottom: "var(--sp-2)" }}>
                    <span className={statusChip(p.status)}>{p.status}</span>
                    <span className="mono">#{p.id} \u2022 {p.clientId}</span>
                  </div>
                  <div style={{ display: "flex", gap: "var(--sp-6)", flexWrap: "wrap", fontSize: 13, color: "var(--color-text-secondary)" }}>
                    <span>Score <strong style={{ color: "var(--color-text)" }}>{p.score}</strong></span>
                    <span>Renda <strong style={{ color: "var(--color-text)" }}>R$ {Number(p.income).toLocaleString("pt-BR")}</strong></span>
                    <span>Valor <strong style={{ color: "var(--color-text)" }}>R$ {Number(p.requestedAmount).toLocaleString("pt-BR")}</strong></span>
                  </div>
                </div>
                <div style={{ display: "flex", gap: "var(--sp-2)" }}>
                  <button className="btn btn--secondary btn--sm" onClick={() => setTraceId(p.id)}>
                    Ver Trace
                  </button>
                  <button
                    className="btn btn--sm"
                    style={{ color: "var(--color-success)", borderColor: "var(--color-success)" }}
                    disabled={acting === p.id}
                    onClick={() => decide(p.id, "APPROVED")}
                  >
                    {acting === p.id ? <span className="spinner" style={{ borderTopColor: "var(--color-success)" }} /> : null}
                    Aprovar
                  </button>
                  <button
                    className="btn btn--sm btn--destructive"
                    disabled={acting === p.id}
                    onClick={() => decide(p.id, "REJECTED")}
                  >
                    Rejeitar
                  </button>
                </div>
              </div>

              <div style={{ marginTop: "var(--sp-4)", display: "grid", gap: "var(--sp-2)", fontSize: 13 }}>
                <div className="list-row" style={{ padding: "8px 0", border: "none" }}>
                  <span style={{ color: "var(--color-text-secondary)" }}>L1</span>
                  <span className="mono">{p.layer1Status || "\u2014"} {p.layer1RuleId ? "(" + p.layer1RuleId + ")" : ""}</span>
                </div>
                <div className="list-row" style={{ padding: "8px 0", border: "none" }}>
                  <span style={{ color: "var(--color-text-secondary)" }}>L2 (Laya)</span>
                  <span className="mono">{p.layer2Complexity || "\u2014"} {p.layer2Confidence != null ? "(" + Number(p.layer2Confidence).toFixed(2) + ")" : ""}</span>
                </div>
                <div className="list-row" style={{ padding: "8px 0", border: "none" }}>
                  <span style={{ color: "var(--color-text-secondary)" }}>L3 (LLM)</span>
                  <span className="mono">{p.layer3Decision || "\u2014"} {p.layer3Confidence != null ? "(" + Number(p.layer3Confidence).toFixed(2) + ")" : ""}</span>
                </div>
                {p.finalMessage && (
                  <div style={{ color: "var(--color-text-secondary)", fontStyle: "italic", marginTop: "var(--sp-2)" }}>
                    {p.finalMessage}
                  </div>
                )}
              </div>

              <div className="field" style={{ marginTop: "var(--sp-4)" }}>
                <label className="field-label">Nota (opcional)</label>
                <input
                  className="input"
                  placeholder="Coment\u00e1rio da mesa\u2026"
                  value={notes[p.id] || ""}
                  onChange={(e) => setNotes((n) => ({ ...n, [p.id]: e.target.value }))}
                />
              </div>
            </div>
          </div>
        ))}
      </div>

      {traceId && <TraceModal proposalId={traceId} onClose={() => setTraceId(null)} />}
    </>
  );
}
