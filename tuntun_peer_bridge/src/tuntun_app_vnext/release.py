"""Trust the outer release and frozen core before loading any model object."""
from pathlib import Path
from ..tuntun_peer.bundle import digest, read, load_bundle, validate_bundle
from . import SCHEMA_VERSION
from .service import AppService

CORE_PIN = "815239b302961e6c9f61e4169daccf68159aea68d70bdf7b13016ddc2b8735c4"


def validate_release(root, trusted_digest):
    root = Path(root).resolve()
    if digest(root/"manifest.json") != trusted_digest:
        raise ValueError("UNTRUSTED_RELEASE_MANIFEST")
    manifest = read(root/"manifest.json")
    if manifest["schemaVersion"] != SCHEMA_VERSION or manifest["coreManifestSha256"] != CORE_PIN:
        raise ValueError("UNSUPPORTED_RELEASE_SCHEMA_OR_CORE")
    required = {"core_bundle/manifest.json", "request.schema.json", "response.schema.json"}
    if not required <= set(manifest["files"]):
        raise ValueError("REQUIRED_RELEASE_FILE_MISSING")
    for name, expected in manifest["files"].items():
        target = (root/name).resolve()
        if not target.is_relative_to(root) or not target.is_file() or digest(target) != expected:
            raise ValueError("RELEASE_FILE_INTEGRITY_FAILURE")
    for path in Path(__file__).parent.glob("*.py"):
        if manifest["files"].get("src/tuntun_app_vnext/"+path.name) != digest(path):
            raise ValueError("APP_RUNTIME_MISMATCH")
    from .schema import RESPONSE_SCHEMA, REQUEST_SCHEMA
    if read(root/"response.schema.json") != RESPONSE_SCHEMA or read(root/"request.schema.json") != REQUEST_SCHEMA:
        raise ValueError("RELEASE_SCHEMA_MISMATCH")
    validate_bundle(root/"core_bundle", CORE_PIN)
    return manifest


def load_release(root, trusted_digest):
    validate_release(root, trusted_digest)
    return AppService(load_bundle(Path(root)/"core_bundle", CORE_PIN))
