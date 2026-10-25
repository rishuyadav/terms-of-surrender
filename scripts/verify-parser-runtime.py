#!/usr/bin/env python3
"""Verify packaged file workers and resource settings on the disposable Compose stack.

Run after other browser checks finish: temporary-directory cleanup is asserted while idle.
Requires the same exported environment and ports as verify-containers.py.
"""

import argparse
import io
import json
import subprocess
import zipfile
from xml.sax.saxutils import escape

from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path


def load_client():
    spec = spec_from_file_location("container_verifier", Path(__file__).with_name("verify-containers.py"))
    module = module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def pdf_fixture(text):
    escaped = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    content = f"BT /F1 10 Tf 20 700 Td ({escaped}) Tj ET"
    objects = [
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 1000 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        f"<< /Length {len(content)} >>\nstream\n{content}\nendstream",
    ]
    result = "%PDF-1.4\n"
    offsets = []
    for index, obj in enumerate(objects, 1):
        offsets.append(len(result))
        result += f"{index} 0 obj\n{obj}\nendobj\n"
    xref = len(result)
    result += "xref\n0 6\n0000000000 65535 f \n"
    result += "".join(f"{offset:010} 00000 n \n" for offset in offsets)
    result += f"trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n"
    return result.encode("ascii")


def docx_fixture(text, tracked=False):
    output = io.BytesIO()
    run = f"<w:r><w:t>{escape(text)}</w:t></w:r>"
    if tracked:
        run = f'<w:ins w:id="1">{run}</w:ins>'
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("[Content_Types].xml", '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>')
        archive.writestr("_rels/.rels", '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="r1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>')
        archive.writestr("word/document.xml", '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p>' + run + '</w:p></w:body></w:document>')
    return output.getvalue()


def submit_file(client, extension, content):
    boundary = "parser-runtime-verification"
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="synthetic.{extension}"\r\n'
            'Content-Type: application/octet-stream\r\n\r\n').encode() + content
    body += f"\r\n--{boundary}--\r\n".encode()
    status, response, _ = client.request("POST", "/api/analyses", body, "multipart/form-data; boundary=" + boundary)
    return status, json.loads(response)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--engine", choices=("podman", "docker"), default="podman")
    args = parser.parse_args()
    # Both Compose providers label containers; podman-compose does not support `ps backend`.
    container = subprocess.check_output([args.engine, "ps", "-q",
                                         "--filter", "label=com.docker.compose.project=tos-verification",
                                         "--filter", "label=com.docker.compose.service=backend"], text=True).strip()
    assert container and "\n" not in container, "Expected exactly one verification backend"
    info = json.loads(subprocess.check_output([args.engine, "inspect", container], text=True))[0]
    limits = info["HostConfig"]
    assert limits["ReadonlyRootfs"] is True
    assert limits["Memory"] == 1536 * 1024 * 1024
    assert limits["NanoCpus"] == 2_000_000_000
    assert limits["PidsLimit"] == 192
    print("PASS: read-only root and aggregate memory/CPU/PID limits are applied")

    verifier = load_client()
    client = verifier.VerificationClient("http://localhost:13001")
    verifier.await_success(client, "/api/session")
    text = "This agreement renews automatically each year unless written notice is received thirty days before renewal."
    created = []
    try:
        for extension, content in (("pdf", pdf_fixture(text)), ("docx", docx_fixture(text))):
            status, submitted = submit_file(client, extension, content)
            assert status == 202, f"Packaged {extension} extraction returned {status}: {submitted.get('code')}"
            created.append(submitted["id"])
            report = verifier.await_report(client, submitted["id"])
            assert report["document"]["sourceType"] == extension.upper()
            assert report["document"]["text"] == text
            assert report["report"]["findings"], "Expected an evidence-backed renewal finding"
            print(f"PASS: packaged {extension.upper()} worker extracts exact text and completes analysis")
        status, rejected = submit_file(client, "docx", docx_fixture(text, tracked=True))
        assert status == 400 and rejected["code"] == "DOCX_TRACKED_CHANGES"
        print("PASS: worker rejection propagates as a safe input error without fallback")
    finally:
        for report_id in created:
            client.request("DELETE", "/api/analyses/" + report_id)
    leftovers = subprocess.check_output([args.engine, "exec", container, "find", "/tmp", "-maxdepth", "1", "-name", "tos-parser-*"], text=True)
    assert not leftovers.strip(), "Parser temporary directories remain after requests completed"
    print("PASS: idle worker temporary directories are cleaned up")


if __name__ == "__main__":
    main()
