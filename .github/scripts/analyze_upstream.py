#!/usr/bin/env python3
"""Classify upstream diff files as AUTO / MANUAL / FORK using GitHub Models.

Uses the GITHUB_TOKEN already available in every Actions workflow —
no extra secrets required. Falls back gracefully if the token is missing.
"""
import os
import json
import sys
import urllib.request
import urllib.error

token = os.environ.get("GITHUB_TOKEN", "")
if not token:
    print("(GITHUB_TOKEN not available — analysis skipped)")
    sys.exit(0)

commits = open("/tmp/commits.txt").read().strip()
stat = open("/tmp/stat.txt").read().strip()

prompt = (
    "Eres un asistente analizando cambios de upstream google-ai-edge/gallery "
    "para mergear en un fork.\n\n"
    "El fork añade: HTTP bridge (service/, LlmHttpService.kt), tests en src/test/, "
    "docs/, mobile-actions/, tools/ y .github/workflows/jvm-tests.yml. "
    "Estos archivos son exclusivos del fork y NO deben sobreescribirse.\n\n"
    f"Nuevos commits upstream:\n{commits}\n\n"
    f"Archivos que difieren entre fork HEAD y upstream/main:\n{stat}\n\n"
    "Para cada archivo modificado, clasifica:\n"
    "- AUTO: se puede hacer checkout directo desde upstream (el fork no tiene contenido propio aquí)\n"
    "- MANUAL: requiere merge cuidadoso (tanto upstream como el fork tienen cambios)\n"
    "- FORK: archivo exclusivo del fork, ignorar versión upstream\n\n"
    "Sé conciso. Máximo 400 palabras."
)

payload = json.dumps({
    "model": "gpt-4o-mini",
    "max_tokens": 1024,
    "messages": [{"role": "user", "content": prompt}],
}).encode()

req = urllib.request.Request(
    "https://models.inference.ai.azure.com/chat/completions",
    data=payload,
    headers={
        "Authorization": f"Bearer {token}",
        "content-type": "application/json",
    },
)

try:
    resp = json.loads(urllib.request.urlopen(req).read())
    print(resp["choices"][0]["message"]["content"])
except urllib.error.HTTPError as e:
    print(f"API error {e.code}: {e.read().decode()}", file=sys.stderr)
    print("(Analysis unavailable)")
