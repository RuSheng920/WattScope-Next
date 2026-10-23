#!/usr/bin/env python3
"""生成 beta prerelease 的 Release 正文。

用法：python3 scripts/beta_release_body.py <version> > release_body.md

正文来源优先取 `beta-notes/<version>.md`。该目录只存在于 beta 标签对应的
临时提交里，不进 main —— README 是仓库主页、docs/ 是官网，两边都不该被
测试版日志污染；而 CI 检出的是标签本身，文件随手就能拿到。

取不到日志时退化成通用双语说明，保证 prerelease 一定能建出来。

从脚本自身位置定位仓库根，不依赖调用时的 CWD。
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

FALLBACK = """Beta build for early testing. Enable the Beta channel under
Settings → About to receive this version in-app.

测试版构建，仅供尝鲜；在「设置 → 关于 → 更新通道」选择 Beta 版即可收到。
"""


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: beta_release_body.py <version>")
    note = ROOT / "beta-notes" / ("%s.md" % sys.argv[1])
    if note.is_file():
        sys.stdout.write(note.read_text(encoding="utf-8"))
    else:
        sys.stdout.write(FALLBACK)


if __name__ == "__main__":
    main()
