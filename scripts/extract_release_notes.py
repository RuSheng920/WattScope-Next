#!/usr/bin/env python3
"""从 README.md 抽取指定版本的「更新日志」小节，作为 GitHub Release 正文。

用法：python3 scripts/extract_release_notes.py <version> > release_body.md

为什么要脚本而不是在 workflow 里写 heredoc：
1. 能本地直接跑，写错了当场就能发现，不用等 CI；
2. YAML 的 `run: |` 块里嵌 heredoc，缩进和 `${{ }}` 表达式互相纠缠，极易踩坑。
"""
import io
import re
import sys


def extract(readme_path: str, version: str) -> str:
    md = io.open(readme_path, encoding="utf-8").read()
    m = re.search(r"^### v%s\s*$" % re.escape(version), md, re.M)
    if not m:
        return "## WattScope Next v%s\n" % version
    rest = md[m.end():]
    nxt = re.search(r"^#{2,3} ", rest, re.M)
    body = rest[: nxt.start()] if nxt else rest
    return "## WattScope Next v%s\n%s\n" % (version, body.strip())


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: extract_release_notes.py <version>")
    sys.stdout.write(extract("README.md", sys.argv[1]))
