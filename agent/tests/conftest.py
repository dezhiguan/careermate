import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
for key, value in {
    "KEEL_BRIDGE_TOKEN": "unit-bridge", "CAREERMATE_SERVICE_TOKEN": "unit-service",
    "KEEL_ENV": "staging", "KEEL_LLM_BASE_URL": "http://model.invalid", "KEEL_LLM_KEY": "unit-key",
    "KEEL_AUDIT_URL": "http://audit.invalid", "KEEL_AUDIT_TOKEN": "unit-audit",
    "KEEL_AUDIT_SPOOL_PATH": "/tmp/careermate-unit-audit", "CAREERMATE_BACKEND_URL": "http://business.invalid",
    "CAREERMATE_AGENT_ENDPOINT": "http://runtime.invalid",
}.items():
    os.environ[key] = value
