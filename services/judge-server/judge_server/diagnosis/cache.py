"""(code_hash, error_signature) → diagnosis 的 LRU 缓存。"""

from __future__ import annotations

import hashlib
import threading
import time
from collections import OrderedDict
from typing import Any, Dict, Optional


class DiagnosisCache:
    """线程安全的 TTL + LRU 缓存。"""

    def __init__(self, max_size: int = 1024, ttl_seconds: float = 600.0):
        self._max_size = max_size
        self._ttl = ttl_seconds
        self._store: OrderedDict[str, tuple[float, Dict[str, Any]]] = OrderedDict()
        self._lock = threading.Lock()
        self._hits = 0
        self._misses = 0

    def get(self, key: str) -> Optional[Dict[str, Any]]:
        with self._lock:
            entry = self._store.get(key)
            if entry is None:
                self._misses += 1
                return None
            ts, value = entry
            if time.monotonic() - ts > self._ttl:
                del self._store[key]
                self._misses += 1
                return None
            self._store.move_to_end(key)
            self._hits += 1
            return value

    def put(self, key: str, value: Dict[str, Any]) -> None:
        with self._lock:
            if key in self._store:
                self._store.move_to_end(key)
                self._store[key] = (time.monotonic(), value)
            else:
                if len(self._store) >= self._max_size:
                    self._store.popitem(last=False)
                self._store[key] = (time.monotonic(), value)

    def stats(self) -> Dict[str, int]:
        return {"hits": self._hits, "misses": self._misses, "size": len(self._store)}

    @staticmethod
    def make_key(code_hash: str, error_signature: str) -> str:
        raw = f"{code_hash}:{error_signature}"
        return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:32]

    @staticmethod
    def hash_code(src: str) -> str:
        return hashlib.sha256(src.encode("utf-8")).hexdigest()[:16]
