"""Forward normalized JSONL messages from an authorized platform adapter or ASR.

AIVORA_ACCESS_TOKEN is read from the environment, never from CLI arguments.
Each input line: {"kind":"CHAT|SUBTITLE","author":"name","content":"text","externalId":"optional"}
"""
import argparse
import json
import os
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:8080')
    parser.add_argument('--room', type=int, required=True)
    args = parser.parse_args()
    url = urlparse(args.base_url)
    if url.username or url.password or url.query or url.fragment or not url.hostname:
        parser.error('Invalid base URL')
    if url.scheme != 'https' and not (url.scheme == 'http' and url.hostname in ('localhost', '127.0.0.1', '::1')):
        parser.error('Use HTTPS for remote connections')
    token = os.environ.get('AIVORA_ACCESS_TOKEN')
    if not token or args.room < 1:
        parser.error('Set AIVORA_ACCESS_TOKEN and a positive room ID')
    # urllib follows redirects by default: refuse redirects to avoid leaking the bearer token.
    from urllib.request import HTTPRedirectHandler, build_opener
    class NoRedirect(HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None
    opener = build_opener(NoRedirect())
    for line_number, line in enumerate(sys.stdin, 1):
        if not line.strip():
            continue
        try:
            event = json.loads(line)
            request = Request(args.base_url.rstrip('/') + f'/api/live/rooms/{args.room}/events',
                              data=json.dumps(event).encode(),
                              headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token})
            with opener.open(request, timeout=15) as response:
                result = json.load(response)
            print(json.dumps({'line': line_number, 'result': result}), flush=True)
        except (ValueError, HTTPError, URLError, TimeoutError):
            print(f'Line {line_number}: forwarding failed; check input, token and room status', file=sys.stderr)
            return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
