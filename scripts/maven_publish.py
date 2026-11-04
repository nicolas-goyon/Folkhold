"""Uploads the files of a local Maven repository to a remote Maven repository, as the Gradle task publish does.

The workflow Build writes the Maven files of the mods to build/maven (Gradle task
publishAllPublicationsToStagingRepository). The workflow Release uploads these files to GitHub Packages with this
script, so the Maven jars are the jars of the GitHub Release.

The script uploads each file of a version with an HTTP PUT. Then, for each artifact, it reads the remote file
maven-metadata.xml, adds the new versions, and uploads it with its checksums.

Usage: python3 scripts/maven_publish.py <folder> <URL of the repository>
The environment variables MAVEN_USER and MAVEN_PASSWORD give the credentials (HTTP basic authentication).
"""
import argparse
import base64
import hashlib
import os
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

METADATA = "maven-metadata.xml"
CHECKSUMS = ("md5", "sha1", "sha256", "sha512")
# The time limit of each HTTP request, in seconds.
TIMEOUT = 120


def namespace(element):
    """Returns the XML namespace of `element` as "{URI}", or "" when it has no namespace."""
    return element.tag[:element.tag.index("}") + 1] if element.tag.startswith("{") else ""


def merge_metadata(remote, local):
    """Returns the metadata of `remote` (bytes, or None when the remote file does not exist) with the versions,
    latest, release and lastUpdated of `local` (bytes). Each file can have an XML namespace. The result keeps the
    namespace of `remote`."""
    if remote is None:
        return local
    root = ET.fromstring(remote)
    local_root = ET.fromstring(local)
    ns, local_ns = namespace(root), namespace(local_root)
    if ns:
        ET.register_namespace("", ns[1:-1])

    def child(parent, name):
        element = parent.find(ns + name)
        if element is None:
            element = ET.SubElement(parent, ns + name)
        return element

    new = local_root.find(local_ns + "versioning")
    if new is None:
        return ET.tostring(root, encoding="UTF-8", xml_declaration=True)
    versioning = child(root, "versioning")
    versions = child(versioning, "versions")
    known = {v.text for v in versions.findall(ns + "version")}
    for version in new.findall(f"{local_ns}versions/{local_ns}version"):
        if version.text not in known:
            ET.SubElement(versions, ns + "version").text = version.text
    for name in ("latest", "release", "lastUpdated"):
        value = new.findtext(local_ns + name)
        if value is not None:
            child(versioning, name).text = value
    return ET.tostring(root, encoding="UTF-8", xml_declaration=True)


def checksum_files(data):
    """Returns (extension, content) for each checksum of `data`."""
    return [(name, hashlib.new(name, data).hexdigest().encode()) for name in CHECKSUMS]


class Repository:
    def __init__(self, url, user, password):
        self.url = url.rstrip("/")
        token = base64.b64encode(f"{user}:{password}".encode()).decode()
        self.headers = {"Authorization": f"Basic {token}"}

    def get(self, path):
        request = urllib.request.Request(f"{self.url}/{path}", headers=self.headers)
        try:
            with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
                return response.read()
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
            raise

    def put(self, path, data):
        headers = {**self.headers, "Content-Type": "application/octet-stream"}
        request = urllib.request.Request(f"{self.url}/{path}", data=data, method="PUT", headers=headers)
        with urllib.request.urlopen(request, timeout=TIMEOUT):
            pass
        print(f"Uploaded {path}")


def publish(folder, repository):
    """Uploads the files of `folder`: first the files of the versions, then the metadata of the artifacts."""
    files, metadata = [], []
    for directory, _, names in os.walk(folder):
        for name in names:
            path = os.path.relpath(os.path.join(directory, name), folder).replace(os.sep, "/")
            if name == METADATA:
                metadata.append(path)
            elif not name.startswith(METADATA + "."):
                files.append(path)
    if not files:
        raise SystemExit(f"No file to publish in {folder}.")
    for path in sorted(files):
        with open(os.path.join(folder, path), "rb") as source:
            repository.put(path, source.read())
    for path in sorted(metadata):
        with open(os.path.join(folder, path), "rb") as source:
            data = merge_metadata(repository.get(path), source.read())
        repository.put(path, data)
        for extension, content in checksum_files(data):
            repository.put(f"{path}.{extension}", content)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("folder", help="the local Maven repository")
    parser.add_argument("url", help="the URL of the remote Maven repository")
    args = parser.parse_args(argv)
    publish(args.folder, Repository(args.url, os.environ.get("MAVEN_USER", ""), os.environ.get("MAVEN_PASSWORD", "")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
