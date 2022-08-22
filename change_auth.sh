#!/bin/sh

git filter-branch --env-filter '

export GIT_AUTHOR_NAME="Michael Leo"
export GIT_AUTHOR_EMAIL="yhzemail61010@aliyun.com"
export GIT_COMMITTER_NAME="Michael Leo"
export GIT_COMMITTER_EMAIL="yhzemail61010@aliyun.com"
' -f
