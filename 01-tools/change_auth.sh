#!/bin/sh

# 批量修改 Git 历史中所有提交的作者和提交者信息。
# GIT_AUTHOR_NAME：原始作者的姓名
# GIT_AUTHOR_EMAIL：原始作者的邮箱
# 这两个变量决定谁创建了这个提交的内容
# GIT_COMMITTER_NAME：实际执行提交操作的人
# GIT_COMMITTER_EMAIL：提交者的邮箱
# 这两个变量决定谁提交了这个更改到仓库
git filter-branch --env-filter '
export GIT_AUTHOR_NAME="Michael Leo"
export GIT_AUTHOR_EMAIL="yhzemail61010@aliyun.com"
export GIT_COMMITTER_NAME="Michael Leo"
export GIT_COMMITTER_EMAIL="yhzemail61010@aliyun.com"
' -f
# -f / --force：强制重写历史，即使已经有备份引用存在
