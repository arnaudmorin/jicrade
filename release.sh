#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "usage: $0 [version]   e.g. $0, $0 0.7.0, or $0 0.7.0-rc1 for a pre-release" >&2
    echo "Without a version, bumps the minor for Added/Changed/Removed entries, else the patch." >&2
    exit 1
}

[[ $# -le 1 ]] || usage
version=${1:-}
version=${version#v}

cd "$(git rev-parse --show-toplevel)"

die() { echo "error: $*" >&2; exit 1; }

[[ $(git branch --show-current) == master ]] || die "not on master"
[[ -z $(git status --porcelain) ]] || die "working tree is not clean"
git fetch -q origin master --tags
[[ $(git rev-parse HEAD) == $(git rev-parse origin/master) ]] || die "master differs from origin/master"

unreleased=$(awk '/^## \[Unreleased\]/{f=1; next} /^## \[/{f=0} f && NF' CHANGELOG.md)
previous=$(git tag --list 'v*' --sort=-v:refname | grep -v -e - | head -1)

if [[ -z $version ]]; then
    [[ -n $unreleased ]] || die "CHANGELOG.md has no [Unreleased] entries"
    [[ -n $previous ]] || die "no previous release tag found"
    IFS=. read -r major minor patch <<< "${previous#v}"
    if grep -qE '^### (Added|Changed|Removed|Deprecated)' <<< "$unreleased"; then
        version=$major.$((minor + 1)).0
    else
        version=$major.$minor.$((patch + 1))
    fi
    read -rp "Release $version (previous $previous)? [y/N] " answer
    [[ $answer == [yY] ]] || exit 1
fi

[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$ ]] || usage
tag=v$version
! git rev-parse -q --verify "refs/tags/$tag" >/dev/null || die "tag $tag already exists"

if [[ $version != *-* ]]; then
    [[ -n $unreleased ]] || die "CHANGELOG.md has no [Unreleased] entries"
    [[ -n $previous ]] || die "no previous release tag found"
fi

./gradlew -q :app:testDebugUnitTest :app:lintDebug

if [[ $version != *-* ]]; then
    url=https://github.com/arnaudmorin/jicrade/compare/$previous...$tag
    awk -v v="$version" -v d="$(date +%F)" -v link="[$version]: $url" '
        /^## \[Unreleased\]/ { print "## [" v "] - " d; next }
        /^\[[0-9]/ && !done { print link; done = 1 }
        { print }
    ' CHANGELOG.md > CHANGELOG.md.tmp
    mv CHANGELOG.md.tmp CHANGELOG.md
    git commit -q -s -m "Release $version" CHANGELOG.md
fi

git tag -s "$tag" -m "$tag"
git --no-pager log --oneline -1 --decorate

read -rp "Push master and $tag to origin? [y/N] " answer
if [[ $answer == [yY] ]]; then
    git push origin master "$tag"
else
    echo "Not pushed. To undo: git tag -d $tag && git reset --hard origin/master"
fi
