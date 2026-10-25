"""Check tracked/unignored Git candidates without printing secret values."""
import pathlib
import re
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parents[1]
paths = subprocess.check_output(
    ['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], cwd=root
).decode().split('\0')
forbidden = re.compile(r'(^|/)(\.signing|\.local|\.gradle|\.kotlin|build|releases|captures|node_modules)/|(^|/)\.env(?:\.|$)|(^|/)(local|release)\.properties$|\.(?:jks|keystore|pem|key|p12|pfx|apk|aab|apks|gray|mp4|mp3|wav|zip|log)$', re.I)
patterns = [
    re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----\s*[\r\n]'),
    re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
    re.compile(r'\bgh[pousr]_[A-Za-z0-9]{30,}\b'),
    re.compile(r'\bmfa\.[\w-]{60,}\b|\b[\w-]{23,30}\.[\w-]{6}\.[\w-]{27,}\b'),
]
findings = []
files = sorted(set(filter(None, paths)))
for relative in files:
    path = root / relative
    if not path.is_file():
        continue
    if forbidden.search(relative) and path.name != '.env.example':
        findings.append((relative, 'private or generated artifact'))
    data = path.read_bytes()
    if path.suffix.lower() in {'.png', '.jpg', '.jpeg', '.webp', '.gif', '.ico', '.jar'}:
        continue
    text = data.decode('utf-8', errors='replace')
    if any(pattern.search(text) for pattern in patterns):
        findings.append((relative, 'possible credential material'))
for path, reason in findings:
    print(f'{path}: {reason}', file=sys.stderr)
print(f'{len(files)} Git candidates; {len(findings)} findings.')
sys.exit(bool(findings))
