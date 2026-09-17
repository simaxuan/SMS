#!/usr/bin/env bash
# 推送 main 与全部 feature 分支到远程仓库
set -e
REMOTE="${1:-origin}"
BRANCH="${2:-main}"

echo "==> 推送 $BRANCH 到 $REMOTE"
git push -u "$REMOTE" "$BRANCH"

# 推送全部 feature 分支
for b in $(git for-each-ref --format='%(refname:short)' refs/heads/feature/); do
  echo "==> 推送 $b"
  git push -u "$REMOTE" "$b"
done

echo "==> 完成"
