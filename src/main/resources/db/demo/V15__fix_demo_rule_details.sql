-- V15 (demo only): Normalize rule_details keys and format to match what the live
-- risk engine stores. V13 seeded flat numbers with "velocityAnomaly"/"behavioralAnomaly"
-- keys; the engine produces nested {score, factors} objects under "velocity"/"behavioral".

UPDATE risk_alert SET rule_details = '{"finalScore":0.91,"escalationTier1":false,"escalationTier2":true,"amountAnomaly":{"score":0.52,"factors":{}},"velocity":{"score":0.61,"factors":{}},"behavioral":{"score":0.48,"factors":{}},"graphPattern":{"score":0.95,"factors":{"fanInCount":3,"fanOutCount":4,"roundTripWindowMinutes":87}}}'
WHERE alert_number = 'ALERT-DEMO-0001';

UPDATE risk_alert SET rule_details = '{"finalScore":0.78,"escalationTier1":true,"escalationTier2":false,"amountAnomaly":{"score":0.88,"factors":{"transactionAmount":12500.00,"baselineMean":200.00,"zScore":4.22}},"velocity":{"score":0.12,"factors":{}},"behavioral":{"score":0.20,"factors":{}},"graphPattern":{"score":0.05,"factors":{}}}'
WHERE alert_number = 'ALERT-DEMO-0002';

UPDATE risk_alert SET rule_details = '{"finalScore":0.72,"escalationTier1":true,"escalationTier2":false,"amountAnomaly":{"score":0.10,"factors":{}},"velocity":{"score":0.85,"factors":{"txnCount1h":8,"dailyBaseline":0.5,"velocityRatio":8.0}},"behavioral":{"score":0.30,"factors":{}},"graphPattern":{"score":0.05,"factors":{}}}'
WHERE alert_number = 'ALERT-DEMO-0003';

UPDATE risk_alert SET rule_details = '{"finalScore":0.54,"escalationTier1":false,"escalationTier2":false,"amountAnomaly":{"score":0.10,"factors":{}},"velocity":{"score":0.20,"factors":{}},"behavioral":{"score":0.15,"factors":{}},"graphPattern":{"score":0.65,"factors":{"distinctRecipients":6,"windowHours":4}}}'
WHERE alert_number = 'ALERT-DEMO-0004';

UPDATE risk_alert SET rule_details = '{"finalScore":0.48,"escalationTier1":false,"escalationTier2":false,"amountAnomaly":{"score":0.05,"factors":{}},"velocity":{"score":0.08,"factors":{}},"behavioral":{"score":0.62,"factors":{"transactionHour":3,"typicalHourRange":[9,18]}},"graphPattern":{"score":0.02,"factors":{}}}'
WHERE alert_number = 'ALERT-DEMO-0005';

UPDATE risk_alert SET rule_details = '{"finalScore":0.31,"escalationTier1":false,"escalationTier2":false,"amountAnomaly":{"score":0.38,"factors":{"transactionAmount":155.00,"baselineMean":110.00,"zScore":1.80}},"velocity":{"score":0.05,"factors":{}},"behavioral":{"score":0.10,"factors":{}},"graphPattern":{"score":0.02,"factors":{}}}'
WHERE alert_number = 'ALERT-DEMO-0006';
