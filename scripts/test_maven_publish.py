"""Tests of scripts/maven_publish.py. Run: python3 -m unittest discover -s scripts -p "test_*.py" """
import os
import tempfile
import unittest
import xml.etree.ElementTree as ET

import maven_publish

REMOTE = b"""<?xml version="1.0" encoding="UTF-8"?>
<metadata><groupId>g</groupId><artifactId>a</artifactId><versioning><latest>1.0.0</latest><release>1.0.0</release>
<versions><version>0.9.0</version><version>1.0.0</version></versions><lastUpdated>20260101000000</lastUpdated>
</versioning></metadata>"""
LOCAL = b"""<?xml version="1.0" encoding="UTF-8"?>
<metadata><groupId>g</groupId><artifactId>a</artifactId><versioning><latest>1.1.0</latest><release>1.1.0</release>
<versions><version>1.1.0</version></versions><lastUpdated>20261104000000</lastUpdated></versioning></metadata>"""


class FakeRepository:
    def __init__(self, files=None):
        self.files = dict(files or {})
        self.order = []

    def get(self, path):
        return self.files.get(path)

    def put(self, path, data):
        self.files[path] = data
        self.order.append(path)


class MergeTest(unittest.TestCase):
    def test_no_remote_metadata(self):
        self.assertEqual(maven_publish.merge_metadata(None, LOCAL), LOCAL)

    def test_new_version_is_added(self):
        root = ET.fromstring(maven_publish.merge_metadata(REMOTE, LOCAL))
        self.assertEqual([v.text for v in root.findall("versioning/versions/version")], ["0.9.0", "1.0.0", "1.1.0"])
        self.assertEqual(root.findtext("versioning/latest"), "1.1.0")
        self.assertEqual(root.findtext("versioning/release"), "1.1.0")
        self.assertEqual(root.findtext("versioning/lastUpdated"), "20261104000000")

    def test_known_version_is_not_added_again(self):
        root = ET.fromstring(maven_publish.merge_metadata(LOCAL, LOCAL))
        self.assertEqual([v.text for v in root.findall("versioning/versions/version")], ["1.1.0"])


class PublishTest(unittest.TestCase):
    def test_files_first_then_merged_metadata(self):
        with tempfile.TemporaryDirectory() as folder:
            artifact = os.path.join(folder, "g", "a")
            os.makedirs(os.path.join(artifact, "1.1.0"))
            for name, data in (("1.1.0/a-1.1.0.jar", b"jar"), ("1.1.0/a-1.1.0.jar.sha1", b"x"),
                               ("maven-metadata.xml", LOCAL), ("maven-metadata.xml.sha1", b"old")):
                with open(os.path.join(artifact, name), "wb") as out:
                    out.write(data)
            repository = FakeRepository({"g/a/maven-metadata.xml": REMOTE})
            maven_publish.publish(folder, repository)
        self.assertEqual(repository.order[:3], ["g/a/1.1.0/a-1.1.0.jar", "g/a/1.1.0/a-1.1.0.jar.sha1",
                                                "g/a/maven-metadata.xml"])
        self.assertEqual(len(repository.order), 3 + len(maven_publish.CHECKSUMS))
        merged = repository.files["g/a/maven-metadata.xml"]
        self.assertIn(b"0.9.0", merged)
        self.assertEqual(repository.files["g/a/maven-metadata.xml.sha1"],
                         dict(maven_publish.checksum_files(merged))["sha1"])

    def test_empty_folder(self):
        with tempfile.TemporaryDirectory() as folder, self.assertRaises(SystemExit):
            maven_publish.publish(folder, FakeRepository())


if __name__ == "__main__":
    unittest.main()
