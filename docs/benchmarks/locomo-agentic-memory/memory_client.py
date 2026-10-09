"""Thin REST wrapper around OpenSearch Agentic Memory APIs."""
import requests
import urllib3

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


class MemoryClient:
    """Client for OpenSearch Agentic Memory container operations."""

    def __init__(self, url: str, user: str, password: str, container_id: str, verify_ssl: bool = False):
        self.base = url.rstrip("/")
        self.auth = (user, password)
        self.container_id = container_id
        self.verify = verify_ssl
        self._session = requests.Session()
        self._session.auth = self.auth
        self._session.verify = self.verify

    def _memories_url(self, suffix=""):
        return f"{self.base}/_plugins/_ml/memory_containers/{self.container_id}/memories{suffix}"

    def add_memory(self, messages: list, namespace: dict, tags: dict = None, infer: bool = True) -> dict:
        """Store a conversation turn. With infer=True, LLM extracts long-term facts."""
        body = {"messages": messages, "namespace": namespace, "infer": infer, "payload_type": "conversational"}
        if tags:
            body["tags"] = tags
        r = self._session.post(self._memories_url(), json=body)
        r.raise_for_status()
        return r.json()

    def semantic_search(self, query: str, k: int = 5, namespace: dict = None) -> list:
        """Semantic (neural) search over long-term memories."""
        body = {"query": query, "k": k}
        if namespace:
            body["namespace"] = namespace
        r = self._session.post(self._memories_url("/long-term/_semantic_search"), json=body)
        r.raise_for_status()
        return r.json().get("hits", {}).get("hits", [])

    def hybrid_search(self, query: str, k: int = 5, namespace: dict = None) -> list:
        """Hybrid (keyword + semantic) search over long-term memories."""
        body = {"query": query, "k": k}
        if namespace:
            body["namespace"] = namespace
        r = self._session.post(self._memories_url("/long-term/_hybrid_search"), json=body)
        r.raise_for_status()
        return r.json().get("hits", {}).get("hits", [])
