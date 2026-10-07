"""Validate real production inputs without printing credentials or their values."""
import os
import re
from urllib.parse import urlsplit

REQUIRED = (
    "FIREBASE_API_KEY", "FIREBASE_APP_ID", "FIREBASE_PROJECT_ID", "GOOGLE_WEB_CLIENT_ID",
    "MONETIZATION_BASE_URL", "PRIVACY_POLICY_URL", "ACCOUNT_DELETION_URL",
    "UPLOAD_STORE_FILE", "UPLOAD_STORE_PASSWORD", "UPLOAD_KEY_ALIAS", "UPLOAD_KEY_PASSWORD",
    "ADMOB_APP_ID", "ADMOB_BANNER_ID", "ADMOB_INTERSTITIAL_ID", "ADMOB_REWARDED_ID",
)

def validate(values):
    errors = []
    for name in REQUIRED:
        value = values.get(name, "").strip()
        if not value:
            errors.append(f"Missing: KHABIR_{name}")
        elif any(token in value.lower() for token in ("play-preflight", "example.invalid", "replace_me", "placeholder")):
            errors.append(f"Test value is not allowed: KHABIR_{name}")
    if values.get("FIREBASE_API_KEY") and not re.fullmatch(r"AIza[\w-]{35}", values["FIREBASE_API_KEY"]):
        errors.append("Invalid Firebase API key format")
    if values.get("FIREBASE_APP_ID") and not re.fullmatch(r"1:\d+:android:[a-zA-Z0-9]+", values["FIREBASE_APP_ID"]):
        errors.append("Invalid Firebase Android App ID format")
    if values.get("GOOGLE_WEB_CLIENT_ID") and not values["GOOGLE_WEB_CLIENT_ID"].endswith(".apps.googleusercontent.com"):
        errors.append("Invalid Google OAuth client ID format")
    for name in ("MONETIZATION_BASE_URL", "PRIVACY_POLICY_URL", "ACCOUNT_DELETION_URL", "GEMINI_VISION_ENDPOINT"):
        value = values.get(name, "")
        if not value:
            continue
        url = urlsplit(value)
        host = url.hostname or ""
        if url.scheme != "https" or not host or url.username or url.password or host.endswith((".invalid", ".localhost", ".test")) or host in ("localhost", "example.com", "example.org", "example.net"):
            errors.append(f"Real HTTPS URL required: KHABIR_{name}")
    for name in ("ADMOB_APP_ID", "ADMOB_BANNER_ID", "ADMOB_INTERSTITIAL_ID", "ADMOB_REWARDED_ID"):
        value = values.get(name, "")
        if value and (not re.fullmatch(r"ca-app-pub-\d{16}[~/]\d{10}", value) or "3940256099942544" in value):
            errors.append(f"Production AdMob ID required: KHABIR_{name}")
    return errors

if __name__ == "__main__":
    values = {k.removeprefix("ORG_GRADLE_PROJECT_KHABIR_"): v for k, v in os.environ.items() if k.startswith("ORG_GRADLE_PROJECT_KHABIR_")}
    errors = validate(values)
    if errors:
        raise SystemExit("\n".join(errors))
    print("PASS: production settings present and contain no CI placeholders; live OAuth validation remains required")
