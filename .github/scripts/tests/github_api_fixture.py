import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class GitHubApiFixture:
    def __init__(self, routes):
        self.routes = routes
        self.requests = []
        self.posts = []
        self.patches = []
        self.deletes = []
        self.server = None
        self.thread = None

    def start(self):
        routes = self.routes
        requests = self.requests
        posts = self.posts
        patches = self.patches
        deletes = self.deletes

        class Handler(BaseHTTPRequestHandler):
            def do_DELETE(self):
                deletes.append(self.path)
                route = routes.get(("DELETE", self.path))
                status = route() if callable(route) else route
                self.send_response(status if status is not None else 404)
                self.end_headers()

            def do_POST(self):
                self.write_response("POST")

            def do_PATCH(self):
                self.write_response("PATCH")

            def write_response(self, method):
                payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                journal = posts if method == "POST" else patches
                journal.append((self.path, payload))
                route = routes.get((method, self.path))
                result = route(payload) if callable(route) else route
                status, response = result if isinstance(result, tuple) else (result or 404, None)
                body = json.dumps(response).encode() if response is not None else b""
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def do_GET(self):
                requests.append(self.path)
                request_path = self.path if self.path in routes else self.path.split("?", 1)[0]
                if request_path not in routes:
                    self.send_response(404)
                    self.send_header("Content-Type", "application/json")
                    self.end_headers()
                    self.wfile.write(b'{"message":"Not Found"}')
                    return
                payload = routes[request_path]
                if callable(payload):
                    payload = payload()
                status = 200
                if isinstance(payload, tuple):
                    status, payload = payload
                body = json.dumps(payload).encode()
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, format, *args):
                return

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    @property
    def url(self):
        return f"http://127.0.0.1:{self.server.server_address[1]}"

    def stop(self):
        if self.server is not None:
            self.server.shutdown()
            self.server.server_close()
            self.thread.join(timeout=5)
            self.server = None
