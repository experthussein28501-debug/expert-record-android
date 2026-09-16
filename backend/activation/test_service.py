import concurrent.futures
import tempfile
import unittest
from service import ActivationStore


class ActivationTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.store = ActivationStore(self.directory.name + '/test.db')
        self.code = self.store.issue(1)

    def test_repeat_activation_does_not_consume_seat(self):
        for _ in range(2):
            self.assertEqual(200, self.store.activate(self.code, 'a' * 32, 'ip')[0])
        self.assertEqual(409, self.store.activate(self.code, 'b' * 32, 'ip')[0])

    def test_concurrent_devices_cannot_exceed_seats(self):
        with concurrent.futures.ThreadPoolExecutor() as pool:
            results = list(pool.map(lambda i: self.store.activate(self.code, str(i) * 32, str(i))[0], range(6)))
        self.assertEqual(1, results.count(200))
        self.assertEqual(5, results.count(409))

    def test_disable_and_device_transfer(self):
        self.store.activate(self.code, 'a' * 32, 'ip')
        self.store.release_device(self.code, 'a' * 32)
        self.assertEqual(200, self.store.activate(self.code, 'b' * 32, 'ip')[0])
        self.store.disable(self.code)
        self.assertEqual(403, self.store.activate(self.code, 'c' * 32, 'ip')[0])

    def test_wrong_codes_and_throttling(self):
        for _ in range(10):
            self.assertEqual(403, self.store.activate('wrong', 'a' * 32, 'ip')[0])
        self.assertEqual(429, self.store.activate(self.code, 'a' * 32, 'ip')[0])

    def test_database_does_not_store_plaintext_code(self):
        with self.store.connect() as db:
            self.assertNotEqual(self.code, db.execute('SELECT digest FROM codes').fetchone()[0])


if __name__ == '__main__':
    unittest.main()
