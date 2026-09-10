# D0 local backend integration candidate

Live release remains BLOCKED. Includes diabetes and hypertension only.
Use the exact Python and dependency versions in metadata.json.
Verify the trusted manifest digest supplied separately before loading joblib files.
Run: python d0_inference.py --bundle . --manifest-sha256 <trusted digest>
Python: D0Predictor(Path(bundle), trusted_digest).score(canonical_features, pregnancy_status='nonpregnant')
The runtime returns only two scores and availability/version metadata.
Canonical six-feature input semantics are in input_schema.json. No sensor conversion or API/DB mapping is included.
5 means 5+ strength days; age >=80 is top-coded to 80. Missing exercise is unavailable, not zero.
Backend must attach its own input_snapshot_id and apply approved cohort/safety wording and eligibility.
Cross-fitted predictions train Platt; calibrated values on that same sample are NOT independent validation.
Original selection provenance limits remain; provenance contains current sources, not a reconstruction of the past environment.
