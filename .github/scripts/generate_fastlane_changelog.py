#!/usr/bin/env python3
"""Generate a fastlane changelog from GitHub Release notes using GitHub Models.

Reads release notes from stdin, summarizes to <=500 chars via GPT-4o-mini,
writes plain text to stdout. Falls back to simple strip+truncate if the API
is unavailable.

Uses GITHUB_TOKEN — available in every Actions workflow, no extra secrets.
No special permission scope needed (no 'models: read' — that doesn't exist).
"""
import os
import sys
import json
import re
import urllib.request
import urllib.error

MAX_CHARS = 500


def summarize_with_ai(release_body):
    """Call GPT-4o-mini to summarize release notes. Returns None on failure."""
    token = os.environ.get("GITHUB_TOKEN", "")
    if not token:
        print("GITHUB_TOKEN not set — skipping AI summarization", file=sys.stderr)
        return None

    prompt = (
        "Summarize the following software release notes into a concise plain-text "
        "changelog for an app store listing. Rules:\n"
        "- Maximum 500 characters total (HARD LIMIT — count carefully)\n"
        "- Plain text only — no Markdown, no headers, no bold, no links\n"
        "- Use bullet points with a bullet character prefix\n"
        "- Focus on user-facing changes only\n"
        "- Use action verbs: Added, Fixed, Improved, Removed\n"
        "- Drop internal/developer-only changes\n"
        "- If the notes are very short, don't pad — just clean up formatting\n\n"
        "Release notes:\n" + release_body
    )

    payload = json.dumps({
        "model": "gpt-4o-mini",
        "max_tokens": 300,
        "temperature": 0.3,
        "messages": [{"role": "user", "content": prompt}],
    }).encode()

    req = urllib.request.Request(
        "https://models.inference.ai.azure.com/chat/completions",
        data=payload,
        headers={
            "Authorization": "Bearer " + token,
            "Content-Type": "application/json",
        },
    )

    try:
        resp = json.loads(urllib.request.urlopen(req, timeout=30).read())
        text = resp["choices"][0]["message"]["content"].strip()
        return text if text else None
    except Exception as e:
        print("GitHub Models API error: %s" % e, file=sys.stderr)
        return None


def strip_markdown(text):
    """Remove Markdown formatting, returning plain text."""
    # Remove HTML tags (e.g. <br>, <details>, <summary>)
    text = re.sub(r'<[^>]+>', '', text)
    # Remove code blocks (triple backticks) before inline code
    text = re.sub(r'```[\s\S]*?```', '', text)
    # Remove inline code backticks
    text = re.sub(r'`([^`]+)`', r'\1', text)
    # Remove Markdown headers
    text = re.sub(r'^#{1,6}\s+', '', text, flags=re.MULTILINE)
    # Remove blockquotes
    text = re.sub(r'^>\s?', '', text, flags=re.MULTILINE)
    # Remove horizontal rules (before bullet conversion — *** and --- are not bullets)
    text = re.sub(r'^-{3,}$', '', text, flags=re.MULTILINE)
    text = re.sub(r'^\*{3,}$', '', text, flags=re.MULTILINE)
    # Convert bullets BEFORE bold/italic removal — "* text" at line start is a bullet,
    # not italic. The single-asterisk italic regex would otherwise eat bullet markers.
    text = re.sub(r'^- ', '\u2022 ', text, flags=re.MULTILINE)
    text = re.sub(r'^\* ', '\u2022 ', text, flags=re.MULTILINE)
    text = re.sub(r'^\d+\.\s+', '\u2022 ', text, flags=re.MULTILINE)
    # Remove bold/italic (triple, double, single asterisks — order matters)
    text = re.sub(r'\*{3}([^*]+)\*{3}', r'\1', text)
    text = re.sub(r'\*{2}([^*]+)\*{2}', r'\1', text)
    text = re.sub(r'\*([^*]+)\*', r'\1', text)
    # Remove underscore bold/italic
    text = re.sub(r'__([^_]+)__', r'\1', text)
    text = re.sub(r'_([^_]+)_', r'\1', text)
    # Remove strikethrough
    text = re.sub(r'~~([^~]+)~~', r'\1', text)
    # Remove images: ![alt](url) -> alt (before links, since ![...] contains [...])
    text = re.sub(r'!\[([^\]]*)\]\([^)]+\)', r'\1', text)
    # Remove links: [text](url) -> text
    text = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', text)
    # Collapse multiple blank lines
    text = re.sub(r'\n{3,}', '\n\n', text)
    return text.strip()


def fallback_strip(release_body):
    """Dumb fallback: strip Markdown formatting and truncate to 500 chars."""
    text = strip_markdown(release_body)

    if len(text) <= MAX_CHARS:
        return text

    # Truncate at last complete line before limit
    truncated = text[:MAX_CHARS]
    last_newline = truncated.rfind('\n')
    if last_newline > MAX_CHARS * 0.5:
        truncated = truncated[:last_newline]
    return truncated.strip()


def enforce_limit(text):
    """Hard enforce 500 char limit even if AI exceeded it."""
    if not text or not text.strip():
        return None
    text = text.strip()
    if len(text) <= MAX_CHARS:
        return text
    # Try to cut at last complete bullet or line before limit
    truncated = text[:MAX_CHARS]
    last_bullet = truncated.rfind('\n\u2022')
    last_newline = truncated.rfind('\n')
    cut_point = max(last_bullet, last_newline)
    if cut_point > MAX_CHARS * 0.5:
        return truncated[:cut_point].strip() or None
    # No good line break — cut at last word boundary
    last_space = truncated.rfind(' ')
    if last_space > MAX_CHARS * 0.5:
        return truncated[:last_space].strip() or None
    # No good break at all — hard cut
    return truncated.strip() or None


if __name__ == "__main__":
    release_body = sys.stdin.read().strip()
    if not release_body:
        print("Warning: No release notes provided — skipping changelog generation", file=sys.stderr)
        sys.exit(0)

    # Try AI summarization first
    result = summarize_with_ai(release_body)

    if result:
        # AI may include Markdown despite prompt instructions — sanitize
        result = strip_markdown(result)
        result = enforce_limit(result)

    if result:
        print("AI summary: %d chars" % len(result), file=sys.stderr)
    else:
        # Fallback to dumb strip+truncate
        result = fallback_strip(release_body)
        print("Fallback strip: %d chars" % len(result), file=sys.stderr)

    if not result:
        print("Warning: Could not generate changelog — output is empty", file=sys.stderr)
        sys.exit(0)

    # Final validation
    if len(result) > MAX_CHARS:
        print("BUG: Output is %d chars, exceeds %d" % (len(result), MAX_CHARS), file=sys.stderr)
        sys.exit(1)
    print(result)
