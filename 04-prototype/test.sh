#!/usr/bin/env bash
# Self-test for the FMS prototype.
# Boots app.js headlessly against a minimal DOM shim and checks that the
# money arithmetic matches the PRD's stated rules. Run:  ./test.sh
set -e
cd "$(dirname "$0")"
cat .test-shim.js app.js .test-assert.js > /tmp/fms-selftest.js
node /tmp/fms-selftest.js
