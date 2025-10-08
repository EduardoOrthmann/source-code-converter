#!/bin/bash
# Exit immediately if a command exits with a non-zero status.
set -e

echo "--- Building Maven projects in dependency order ---"

# This script finds all pom.xml files and attempts to install them.
# The '-N' flag tells Maven to not recurse into submodules, treating each pom.xml as an independent project.
# The '|| true' ensures that the script continues even if some builds fail initially.
# We run it in a loop to resolve dependencies iteratively.
for i in 1 2 3; do
  echo "--- Build Pass $i ---"
  find /app/project/IBL -name "pom.xml" -exec mvn clean install -o -f {} -N --fail-at-end \; || true
done

# Final build to ensure everything is up to date
echo "--- Final Build Pass ---"
find /app/project/IBL -name "pom.xml" -exec mvn clean install -o -f {} -N \;

echo "--- All modules built successfully ---"