from transformers import AutoConfig
cfg = AutoConfig.from_pretrained("convaiinnovations/laya", trust_remote_code=True)
print("model_type", cfg.model_type)
print("id2label", getattr(cfg, "id2label", None))
