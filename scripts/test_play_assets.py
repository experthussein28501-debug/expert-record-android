import struct
import unittest
from verify_play_assets import check_elf

class NativeAlignmentTest(unittest.TestCase):
    def fixture(self, alignment):
        data = bytearray(120)
        data[:6] = b'\x7fELF\x02\x01'
        struct.pack_into('<Q', data, 32, 64)
        struct.pack_into('<HH', data, 54, 56, 1)
        struct.pack_into('<I', data, 64, 1)
        struct.pack_into('<Q', data, 112, alignment)
        return data
    def test_16k_elf_passes(self):
        check_elf(self.fixture(16384))
    def test_4k_elf_rejected(self):
        with self.assertRaises(AssertionError):
            check_elf(self.fixture(4096))
