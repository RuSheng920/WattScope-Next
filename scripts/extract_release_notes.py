#!/usr/bin/env python3
"""从 README.md（中文）与 README.en.md（英文）抽取指定版本的更新日志，作为 GitHub Release 正文。

用法：python3 scripts/extract_release_notes.py <version> > release_body.md

为什么用脚本而不是在 workflow 里写 heredoc：
1. 能本地直接跑，写错了当场就能发现，不用等 CI；
2. YAML 的 `run: |` 块里嵌 heredoc，缩进和 `${{ }}` 表达式互相纠缠，极易踩坑。

双语规则：两边各自抓 `### vX.Y.Z` 小节，命中的按「中文 → --- → English」拼接。
某一边缺失（例如早期版本还没补英文）不报错，只输出存在的那部分；
两边都没有则退化为一行标题，保证 Release 仍可创建。

从脚本自身位置定位仓库根，不依赖调用时的 CWD。
"""
import io
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ZH_README = ROOT / "README.md"
EN_README = ROOT / "README.en.md"


def extract(readme_path: pathlib.Path, version: str) -> str | None:
    """取出某个 README 里 `### vX.Y.Z` 小节正文；不存在返回 None。"""
    if not readme_path.exists():
        return None
    md = io.open(readme_path, encoding="utf-8").read()
    m = re.search(r"^### v%s\s*$" % re.escape(version), md, re.M)
    if not m:
        return None
    rest = md[m.end():]
    nxt = re.search(r"^#{2,3} ", rest, re.M)
    body = rest[: nxt.start()] if nxt else rest
    return body.strip()


def build(version: str) -> str:
    zh = extract(ZH_README, version)
    en = extract(EN_README, version)

    blocks = []
    if zh:
        blocks.append("## WattScope Next v%s\n\n%s" % (version, zh))
    if en:
        blocks.append("## WattScope Next v%s (English)\n\n%s" % (version, en))

    if not blocks:
        return "## WattScope Next v%s\n" % version
    return "\n\n---\n\n".join(blocks) + "\n"


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: extract_release_notes.py <version>")
    sys.stdout.write(build(sys.argv[1]))
