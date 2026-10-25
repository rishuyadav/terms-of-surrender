#!/usr/bin/env python3
"""Exercise the isolated verification stack over real HTTP, including API restart.

Use only synthetic documents. Requires compose.verification.yml, project
tos-verification, and a keyword-preview deployment on ports 13001 / 18081.
"""

import argparse
import http.client
import http.cookiejar
import json
import subprocess
import time
import urllib.error
import urllib.request
import urllib.parse
import uuid


class VerificationClient:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")
        self.cookies = http.cookiejar.CookieJar()
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))

    def request(self, method, path, body=None, content_type=None, trusted=True):
        headers = {"Origin": self.base_url} if trusted else {}
        if content_type:
            headers["Content-Type"] = content_type
        request = urllib.request.Request(self.base_url + path, data=body, headers=headers, method=method)
        try:
            with self.http.open(request, timeout=15) as response:
                return response.status, response.read(), response.headers
        except urllib.error.HTTPError as error:
            return error.code, error.read(), error.headers

    def submit(self, text):
        boundary = "verification-" + uuid.uuid4().hex
        body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"text\"\r\n\r\n{text}"
                f"\r\n--{boundary}--\r\n").encode()
        status, data, _ = self.request("POST", "/api/analyses", body, "multipart/form-data; boundary=" + boundary)
        return status, json.loads(data)


def await_report(client, report_id):
    for _ in range(80):
        status, body, _ = client.request("GET", "/api/analyses/" + report_id)
        assert status == 200, f"Report read returned {status}"
        report = json.loads(body)
        if report["status"] == "COMPLETED":
            return report
        assert report["status"] != "FAILED", "Analysis failed"
        time.sleep(0.25)
    raise AssertionError("Report did not complete within 20 seconds")


def await_success(client, path):
    for _ in range(100):
        try:
            status, body, headers = client.request("GET", path)
            if status == 200:
                return body, headers
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(0.25)
    raise AssertionError("Verification service did not become ready")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--engine", choices=("podman", "docker"), default="podman")
    args = parser.parse_args()
    client = VerificationClient("http://localhost:13001")
    ownerless = VerificationClient("http://localhost:13001")
    body, _ = await_success(client, "/api/session")
    assert json.loads(body)["mode"] == "RULE_BASED_PREVIEW", "Expected isolated keyword-preview stack"
    source = "This verification agreement renews automatically every year. Give written notice thirty days before renewal. A cancellation fee of fifty dollars applies."
    created = []
    try:
        status, submitted = client.submit(source)
        assert status == 202, f"Submission returned {status}"
        report_id = submitted["id"]
        created.append(report_id)
        report = await_report(client, report_id)
        for finding in report["report"]["findings"]:
            evidence = finding["evidence"]
            # Fixtures are ASCII, so Python and UTF-16 indexing coincide here.
            assert report["document"]["text"][evidence["start"]:evidence["end"]] == evidence["quote"]
        assert report["report"]["legalContextAvailable"] is False
        print("PASS: real HTTP submission, worker completion, and exact evidence")

        assert ownerless.request("GET", "/api/analyses/" + report_id)[0] == 404
        assert client.request("DELETE", "/api/analyses/" + report_id, trusted=False)[0] == 403
        status, _, headers = client.request("GET", "/api/analyses/" + report_id)
        assert status == 200 and "no-store" in headers["Cache-Control"]
        print("PASS: session isolation, CSRF defense, and private response headers")

        # podman-compose does not support `compose restart <service>` — use the engine directly.
        container = subprocess.check_output([args.engine, "ps", "-q",
                                             "--filter", "label=com.docker.compose.project=tos-verification",
                                             "--filter", "label=com.docker.compose.service=backend"], text=True).strip()
        assert container and "\n" not in container, "Expected exactly one verification backend"
        subprocess.run([args.engine, "restart", container], check=True, capture_output=True)
        await_success(client, "/api/analyses/" + report_id)
        assert await_report(client, report_id)["document"]["text"] == source
        print("PASS: PostgreSQL report and ownership survive API container restart")

        status, result = client.submit("a" * 80000)
        assert status == 202, f"Exact character limit rejected: {status}"
        created.append(result["id"])
        assert await_report(client, result["id"])["document"]["text"] == "a" * 80000
        assert client.submit("a" * 80001)[0] == 400
        print("PASS: exact text boundary accepted; oversized text rejected")

        boundary = "verification-file-limit"
        body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"large.pdf\"\r\n"
                "Content-Type: application/pdf\r\n\r\n").encode() + b"%PDF-" + b"x" * (11 * 1024 * 1024)
        body += f"\r\n--{boundary}--\r\n".encode()
        # A proxy may legitimately send 413 before the client finishes writing.
        # urllib discards that valid response when sendall raises BrokenPipeError.
        # Read the response explicitly; an actual connection reset without 413
        # still fails verification rather than being treated as a success.
        address = urllib.parse.urlsplit(client.base_url)
        connection = http.client.HTTPConnection(address.hostname, address.port, timeout=15)
        try:
            try:
                connection.request("POST", "/api/analyses", body, {
                    "Origin": client.base_url,
                    "Content-Type": "multipart/form-data; boundary=" + boundary,
                    "Cookie": "; ".join(f"{cookie.name}={cookie.value}" for cookie in client.cookies),
                })
            except (BrokenPipeError, ConnectionResetError):
                pass
            response = connection.getresponse()
            assert response.status == 413
            assert json.loads(response.read())["code"] == "UPLOAD_TOO_LARGE"
        finally:
            connection.close()
        print("PASS: embedded HTTP server rejects an oversized multipart upload with 413")

        assert client.request("DELETE", "/api/analyses/" + report_id)[0] == 204
        assert client.request("GET", "/api/analyses/" + report_id)[0] == 404
        print("PASS: deletion remains effective after restart")
    finally:
        for report_id in created:
            client.request("DELETE", "/api/analyses/" + report_id)


if __name__ == "__main__":
    main()
