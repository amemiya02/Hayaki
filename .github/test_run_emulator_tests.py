#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later

import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import run_emulator_tests


class CoverageCopyTest(unittest.TestCase):
    def test_coverage_is_read_from_the_built_application(self):
        coverage = b"device coverage"
        checksum = hashlib.sha256(coverage).hexdigest().encode()
        application_id = "io.github.amemiya02.hayaki"

        with tempfile.TemporaryDirectory() as directory:
            metadata = Path(directory) / "output-metadata.json"
            metadata.write_text(json.dumps({"applicationId": application_id}), encoding="utf-8")

            with (
                patch.object(run_emulator_tests, "APK_METADATA", metadata),
                patch.object(run_emulator_tests.subprocess, "check_output", side_effect=[checksum, coverage]) as adb,
            ):
                self.assertEqual(run_emulator_tests.read_coverage(), coverage)

            expected = ["adb", "shell", "-T", "run-as", application_id]
            self.assertEqual([call.args[0][:5] for call in adb.call_args_list], [expected, expected])


if __name__ == "__main__":
    unittest.main()
