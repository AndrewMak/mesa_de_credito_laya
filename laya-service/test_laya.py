import os
os.environ["USE_TF"] = "0"
import laya
agent = laya.load("convaiinnovations/laya", device="cuda")
state = "Credit proposal: score 650, income 4000, no restrictions, moderate history."
questions = {
  "complexity": {
    "type": "choice",
    "instructions": "Assess decision complexity",
    "criteria": {"LOW": "simple, low risk, straightforward",
                 "HIGH": "complex, mixed signals, needs human review"}
  }
}
result = agent.predict(state, questions)
import json
print(json.dumps(result, indent=2, default=str))
