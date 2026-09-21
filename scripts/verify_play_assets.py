"""Check the packaged OCR model and ELF load-segment alignment for 64-bit devices."""
import hashlib
import struct
import sys
import zipfile


def check_elf(data):
    assert data[:4] == b'\x7fELF' and data[4] == 2, 'Expected a 64-bit ELF'
    endian = '<' if data[5] == 1 else '>'
    offset = struct.unpack_from(endian + 'Q', data, 32)[0]
    size, count = struct.unpack_from(endian + 'HH', data, 54)
    loads = 0
    for i in range(count):
        pos = offset + i * size
        if struct.unpack_from(endian + 'I', data, pos)[0] == 1:
            loads += 1
            alignment = struct.unpack_from(endian + 'Q', data, pos + 48)[0]
            assert alignment >= 16384, f'ELF load segment alignment is {alignment}, expected at least 16384'
    assert loads, 'No loadable segments'


def verify(path):
    with zipfile.ZipFile(path) as apk:
        model = apk.read('assets/tessdata/ara.traineddata')
        assert hashlib.sha256(model).hexdigest() == 'e3206d3dc87fd50c24a0fb9f01838615911d25168f4e64415244b67d2bb3e729'
        for name in apk.namelist():
            if name.endswith('.so') and ('/arm64-v8a/' in name or '/x86_64/' in name):
                try:
                    check_elf(apk.read(name))
                except AssertionError as error:
                    raise AssertionError(f'{name}: {error}') from error
    print('PASS: Arabic OCR model hash and 16KB ELF load alignment')


if __name__ == '__main__':
    verify(sys.argv[1])
