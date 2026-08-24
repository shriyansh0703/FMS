#!/usr/bin/env bash
# Self-test for the money-movement dashboard.
# Requires dashboard.js into node and asserts the identities that must hold
# whatever the data says.  Run:  ./dashboard-test.sh
set -e
cd "$(dirname "$0")"
node .dash-assert.js
