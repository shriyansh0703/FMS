#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
cat .test-shim.js app.js gen-comms.js > /tmp/fms-gen.js
node /tmp/fms-gen.js
