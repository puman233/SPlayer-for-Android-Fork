"""Release-note provenance and fail-closed coverage checks (internal evidence only)."""
import hashlib
import json
import os
import re
import subprocess


def git(*args):
    return subprocess.check_output(["git", *args], encoding="utf-8").strip()


def previous_tag(current, explicit=None):
    if git("rev-parse", "--is-shallow-repository") == "true":
        raise ValueError("Release Notes 需要完整 Git 历史与 Tag；请使用 checkout fetch-depth: 0")
    if explicit:
        git("merge-base", "--is-ancestor", explicit, current)
        return explicit
    repository = os.environ["GH_REPO"]
    pages = json.loads(subprocess.check_output([
        "gh", "api", f"repos/{repository}/releases?per_page=100", "--paginate", "--slurp",
    ], encoding="utf-8"))
    candidates = []
    for release in (release for page in pages for release in page):
        tag = release["tag_name"]
        if release["draft"] or tag == current:
            continue
        try:
            git("merge-base", "--is-ancestor", tag, current)
            distance = int(git("rev-list", "--count", f"{tag}..{current}"))
            if distance:
                candidates.append((distance, tag))
        except subprocess.CalledProcessError:
            continue
    if not candidates:
        raise ValueError("无法确定上一公开 Release（可能是首个 Release）；请确认比较起点，不生成虚假的范围")
    return min(candidates)[1]


def collect_sources(previous, current):
    text = git("show", f"{current}:CHANGELOG.md")
    match = re.search(r"^## " + re.escape(current) + r"\s*\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    if not match:
        raise ValueError("当前 Tag 的 CHANGELOG 缺少版本记录")
    sources = {}
    for entry in re.findall(r"^- (.+)$", match[1], re.M):
        key = "changelog:" + hashlib.sha256(entry.encode()).hexdigest()[:16]
        if key in sources:
            raise ValueError("CHANGELOG 中存在重复条目，需要先确认来源")
        sources[key] = entry
    for line in git("log", "--reverse", "--format=%H %s", f"{previous}..{current}").splitlines():
        sha, subject = line.split(" ", 1)
        sources["commit:" + sha] = subject
    if not sources:
        raise ValueError("Release 范围没有可审计来源")
    return sources


def audit(data, sources):
    changes = data.get("changes", [])
    if not isinstance(changes, list) or not changes:
        raise ValueError("缺少结构化 ReleaseChange")
    ids = set()
    summaries = set()
    for change in changes:
        if (not isinstance(change, dict) or not isinstance(change.get("id"), str)
                or change["id"] in ids or change.get("category") not in
                {"highlights", "features", "fixes", "android", "known_issues", "notes"}
                or not isinstance(change.get("summary"), str) or not change["summary"].strip()
                or change["summary"] in summaries):
            raise ValueError("ReleaseChange 无效或同一变化重复出现")
        ids.add(change["id"])
        summaries.add(change["summary"])
    records = data.get("coverage", [])
    if not isinstance(records, list):
        raise ValueError("Coverage Matrix 必须是列表")
    by_source = {}
    mapped = set()
    matrix = []
    for record in records:
        source = record.get("source")
        classification = record.get("classification")
        targets = record.get("release_ids", [])
        if source not in sources or source in by_source:
            raise ValueError("Coverage 来源过期、重复或不在当前范围")
        if classification == "ambiguous":
            raise ValueError("存在未确认条目：" + sources[source])
        if classification not in {"user-visible", "internal"}:
            raise ValueError("每个来源必须明确分类")
        if not isinstance(targets, list) or any(target not in ids for target in targets):
            raise ValueError("Coverage 映射到不存在的 Release 条目")
        if classification == "user-visible" and not targets:
            raise ValueError("遗漏用户可感知变化：" + sources[source])
        if classification == "internal" and (targets or not record.get("exclusion_reason")):
            raise ValueError("内部条目必须排除并说明原因")
        if classification == "user-visible" and not record.get("evidence"):
            raise ValueError("用户变化缺少代码或事实证据")
        by_source[source] = record
        mapped.update(targets)
        matrix.append({**record, "source_text": sources[source]})
    missing = set(sources) - set(by_source)
    if missing:
        raise ValueError("Coverage 尚未分类：" + "; ".join(sources[key] for key in sorted(missing)))
    if mapped != ids:
        raise ValueError("Release 条目缺少来源依据")
    return matrix
