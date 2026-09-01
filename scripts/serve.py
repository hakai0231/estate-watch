# -*- coding: utf-8 -*-
"""로컬 미리보기 서버. UTF-8 charset 을 명시해서 내려준다."""
import functools, http.server, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "dist"


class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map,
                      ".html": "text/html; charset=utf-8"}

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


if __name__ == "__main__":
    handler = functools.partial(Handler, directory=str(ROOT))
    with http.server.ThreadingHTTPServer(("127.0.0.1", 8777), handler) as httpd:
        print("http://localhost:8777", flush=True)
        httpd.serve_forever()
