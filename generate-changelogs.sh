#!/bin/bash

VERSION_NAME="v$(grep '^app.lastVersionName=' gradle.properties | sed 's/app.lastVersionName=//')"
echo "Generating CHANGELOG.md entry since $VERSION_NAME"

github_changelog_generator -u PhilKes -p NotallyX --since-tag $VERSION_NAME --no-unreleased --no-pull-requests --include-tags-regex '^v\d+(\.\d+)*$' --include-labels enhancement,bug --enhancement-label "### Added Features" --bugs-label "### Fixed Bugs" --base CHANGELOG.md
