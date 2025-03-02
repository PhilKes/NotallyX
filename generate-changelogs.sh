#!/bin/bash

VERSION_NAME=$1
OUTPUT_FILE=$2
echo "Generating CHANGELOG.md entry since $VERSION_NAME to $OUTPUT_FILE"
github_changelog_generator -u PhilKes -p NotallyX --since-tag $VERSION_NAME --no-pull-requests --include-tags-regex '^v\d+(\.\d+)*$' --include-labels enhancement,bug --exclude-labels duplicate,question,invalid,wontfix,'already done' --enhancement-label "### Added Features" --bugs-label "### Fixed Bugs" --base $OUTPUT_FILE --output $OUTPUT_FILE
