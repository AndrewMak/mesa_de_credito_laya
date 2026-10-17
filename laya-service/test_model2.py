from transformers import AutoConfig, AutoModelForSequenceClassification
cfg = AutoConfig.from_pretrained("convaiinnovations/laya", trust_remote_code=True)
print("config type", type(cfg))
model = AutoModelForSequenceClassification.from_pretrained("convaiinnovations/laya", trust_remote_code=True, device_map="cuda")
print("labels", model.config.id2label)
