"""Operate CareerMate through the existing Keel console APIs."""
import argparse
import hashlib
import json
import os
from pathlib import Path

import httpx
import yaml

ROOT = Path(__file__).resolve().parent
NAME = yaml.safe_load((ROOT / "agent.yaml").read_text())["metadata"]["name"]


def prompt_payload(git_sha):
    if len(git_sha) != 40 or any(c not in "0123456789abcdef" for c in git_sha):
        raise ValueError("git_sha must be a complete commit SHA")
    manifest = yaml.safe_load((ROOT / "agent.yaml").read_text())
    files = []
    for item in manifest["spec"]["prompts"]["items"]:
        content = (ROOT / "prompts" / (item["name"] + ".md")).read_text()
        files.append({"name": item["name"], "type": "text", "prompt": content,
                      "sha256": "sha256:" + hashlib.sha256(content.encode()).hexdigest()})
    return {"gitSha": git_sha, "files": files}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    register = commands.add_parser("register")
    register.add_argument("--endpoint", required=True)
    sync = commands.add_parser("sync-prompts")
    sync.add_argument("--git-sha", required=True)
    commands.add_parser("self-check")
    commands.add_parser("evaluate")
    inspect = commands.add_parser("inspect")
    inspect.add_argument("--run-id", required=True)
    publish = commands.add_parser("publish")
    publish.add_argument("--run-id", required=True)
    publish.add_argument("--env", choices=["staging", "prod"], required=True)
    args = parser.parse_args()
    base = os.environ["KEEL_CONSOLE_URL"].rstrip("/")
    headers = {"X-Keel-Console": "1"}
    cookies = {}
    if os.environ.get("KEEL_CONSOLE_TOKEN"):
        headers["Authorization"] = "Bearer " + os.environ["KEEL_CONSOLE_TOKEN"]
    elif os.environ.get("KEEL_CONSOLE_COOKIE_FILE"):
        cookies = json.loads(Path(os.environ["KEEL_CONSOLE_COOKIE_FILE"]).read_text())
    else:
        raise RuntimeError("An authenticated console token or cookie file is required")
    prefix = "/api/v1/agents/" + NAME
    with httpx.Client(base_url=base, headers=headers, cookies=cookies, timeout=360) as client:
        def call(method, path, body=None):
            response = client.request(method, path, json=body)
            response.raise_for_status()
            return response.json()

        if args.command == "register":
            manifest = yaml.safe_load((ROOT / "agent.yaml").read_text())
            manifest["spec"]["runtime"]["endpoint"] = args.endpoint
            manifest["env"] = "staging"
            result = call("POST", "/api/v1/agents", manifest)
        elif args.command == "sync-prompts":
            payload = prompt_payload(args.git_sha)
            result = call("POST", prefix + "/prompts/sync", payload)
            activated = []
            for item in payload["files"]:
                detail = call("GET", prefix + "/prompts/" + item["name"])["data"]
                matching = [version for version in detail.get("versions", [])
                            if version.get("sha256") == item["sha256"]]
                if not matching:
                    raise RuntimeError("Synced prompt content has no verified platform version: " + item["name"])
                version = max(int(row["version"]) for row in matching)
                call("POST", prefix + "/prompts/" + item["name"] + "/promote", {"env": "staging", "version": version})
                activated.append({"name": item["name"], "version": version})
            result["activatedStaging"] = activated
        elif args.command == "self-check":
            result = call("POST", prefix + "/self-check")
        elif args.command == "evaluate":
            result = call("POST", "/api/v1/eval/" + NAME + "/runs")
        elif args.command == "inspect":
            result = call("GET", "/api/v1/eval/runs/" + args.run_id)
        else:
            # The platform verifies complete coverage, prompt versions and the evaluated image.
            result = call("POST", "/api/v1/eval/" + NAME + "/runs/" + args.run_id + "/publish", {"env": args.env})
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
