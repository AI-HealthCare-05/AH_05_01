"""Hash-pinned release unit; verify every asset before any joblib deserialization."""
import hashlib
import json
import os
from pathlib import Path
from threading import RLock
from uuid import uuid4
from .. import tuntun_inference as legacy
from .. import d0_inference as d0
from . import SCHEMA_VERSION
from .adapters import LegacyModelAdapter
from .registry import REGISTRY, validate_policy
from .reference import validate_reference
from .service import PeerService

LEGACY_DIGEST = "049c01bb78c768d1e5b91b8bfcc157a84a2f09752cad166867c0c9883f5630c5"

def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))

def write(path, value):
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True, allow_nan=False)+"\n", encoding="utf-8")


def validate_bundle(root, trusted_digest):
    root = Path(root).resolve()
    if digest(root/"manifest.json") != trusted_digest:
        raise ValueError("UNTRUSTED_MANIFEST")
    manifest = read(root/"manifest.json")
    if manifest["schemaVersion"] != SCHEMA_VERSION or manifest["environment"] != d0.environment():
        raise ValueError("UNSUPPORTED_SCHEMA_OR_ENVIRONMENT")
    files = manifest["files"]
    required = {"reference.json", "policy.json", "response.schema.json", "legacy/manifest.json"}
    if not required <= set(files):
        raise ValueError("MISSING_RELEASE_ASSETS")
    for name, expected in files.items():
        path = (root/name).resolve()
        if not path.is_relative_to(root) or not path.is_file() or digest(path) != expected:
            raise ValueError("RELEASE_INTEGRITY_FAILURE")
    # Executing source must be exactly the source approved with this release.
    for path in Path(__file__).parent.glob("*.py"):
        if files.get("runtime/"+path.name) != digest(path):
            raise ValueError("PEER_RUNTIME_MISMATCH")
    binding = manifest["modelBinding"]
    if binding != {"modelVersion": legacy.VERSION, "legacyManifestSha256": digest(root/"legacy/manifest.json")}:
        raise ValueError("MODEL_BINDING_MISMATCH")
    legacy.verify_bundle(root/"legacy", binding["legacyManifestSha256"])
    # Nested D0 hash and environment are checked before the legacy constructor loads waist.
    d0.verify_package(root/"legacy/d0", legacy.D0_DIGEST)
    policy, reference = read(root/"policy.json"), read(root/"reference.json")
    validate_policy(policy, REGISTRY)
    validate_reference(reference, policy, binding)
    if reference["provenance"]["predictionSource"] == "synthetic":
        raise ValueError("SYNTHETIC_REFERENCE_CANNOT_BIND_REAL_MODEL")
    if manifest["referenceVersion"] != reference["referenceVersion"] or manifest["formulaVersion"] != policy["formulaVersion"]:
        raise ValueError("RELEASE_POLICY_VERSION_MISMATCH")
    from .schema import RESPONSE_SCHEMA
    if read(root/"response.schema.json") != RESPONSE_SCHEMA:
        raise ValueError("RELEASE_DTO_MISMATCH")
    return manifest, policy, reference


def load_bundle(root, trusted_digest):
    root = Path(root)
    manifest, policy, reference = validate_bundle(root, trusted_digest)
    predictor = legacy.TuntunPredictor(root/"legacy", manifest["modelBinding"]["legacyManifestSha256"])
    return PeerService(LegacyModelAdapter(predictor), reference, policy, manifest["modelBinding"])


class ReleaseManager:
    """Single-process local activation; immutable release dirs required.

    Validation/loading precedes an atomic pointer replacement and in-memory swap.
    Multi-worker service coordination is a deployment concern, not implied here.
    """
    def __init__(self, pointer, loader=load_bundle):
        self.pointer, self.loader = Path(pointer), loader
        self.lock = RLock()
        self.current = self.previous = self.service = None
        if self.pointer.exists():
            state = read(self.pointer)
            self.current, self.previous = state["current"], state["previous"]
            self.service = self.loader(**self.current)

    def activate(self, root, trusted_digest):
        target = {"root": str(Path(root).resolve()), "trusted_digest": trusted_digest}
        candidate = self.loader(**target)
        with self.lock:
            self.pointer.parent.mkdir(parents=True, exist_ok=True)
            tmp = self.pointer.with_name(self.pointer.name+"."+uuid4().hex+".tmp")
            try:
                with tmp.open("w", encoding="utf-8") as stream:
                    json.dump({"current": target, "previous": self.current}, stream)
                    stream.flush()
                    os.fsync(stream.fileno())
                os.replace(tmp, self.pointer)
            finally:
                if tmp.exists():
                    tmp.unlink()
            self.previous, self.current, self.service = self.current, target, candidate

    def rollback(self):
        with self.lock:
            if self.previous is None:
                raise ValueError("NO_PREVIOUS_RELEASE")
            self.activate(**self.previous)

    def score(self, request):
        with self.lock:
            if self.service is None:
                raise ValueError("NO_ACTIVE_RELEASE")
            service = self.service
        return service.score(request)

