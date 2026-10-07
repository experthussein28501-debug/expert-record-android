import unittest
from verify_release_config import REQUIRED, validate

class ReleaseConfigurationTest(unittest.TestCase):
    def valid(self):
        result = {name: "configured" for name in REQUIRED}
        result.update(FIREBASE_API_KEY="AIza" + "a" * 35, FIREBASE_APP_ID="1:123456789:android:abc123",
            GOOGLE_WEB_CLIENT_ID="123-client.apps.googleusercontent.com")
        for name in ("MONETIZATION_BASE_URL", "PRIVACY_POLICY_URL", "ACCOUNT_DELETION_URL"):
            result[name] = "https://khabir.demo-domain.org/" + name.lower()
        result["ADMOB_APP_ID"] = "ca-app-pub-1234567890123456~1234567890"
        for name in ("ADMOB_BANNER_ID", "ADMOB_INTERSTITIAL_ID", "ADMOB_REWARDED_ID"):
            result[name] = "ca-app-pub-1234567890123456/1234567890"
        return result
    def test_configured_values_pass_format_checks(self):
        self.assertEqual([], validate(self.valid()))
    def test_missing_configuration_is_reported_by_name_only(self):
        self.assertTrue(validate({}))
        self.assertIn("Missing: KHABIR_FIREBASE_PROJECT_ID", validate({}))
    def test_ci_placeholder_and_test_ad_unit_are_rejected(self):
        values = self.valid()
        values.update(PRIVACY_POLICY_URL="https://example.invalid/privacy", UPLOAD_KEY_ALIAS="play-preflight", ADMOB_APP_ID="ca-app-pub-3940256099942544~3347511713")
        self.assertGreaterEqual(len(validate(values)), 3)
    def test_embedded_credentials_are_never_accepted_or_printed(self):
        values = self.valid()
        values["ACCOUNT_DELETION_URL"] = "https://secret:password@khabir.demo-domain.org/delete"
        errors = validate(values)
        self.assertTrue(errors)
        self.assertNotIn("secret", "\n".join(errors))
        self.assertNotIn("password", "\n".join(errors))

if __name__ == "__main__": unittest.main()
