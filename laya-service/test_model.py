from transformers import pipeline
clf = pipeline("text-classification", model="convaiinnovations/laya", device=0)
print("LABELS:", clf.model.config.id2label)
print("OUT:", clf("Customer with score 720 and income 8000 requesting 50k"))
