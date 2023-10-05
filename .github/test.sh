#!/bin/bash

has_error=0

jq -r '.aliases[]["script-ref"] | select(contains(".java"))' jbang-catalog.json | while read -r file; do 
    jbang build "$file" || { echo "Error building $file"; has_error=1; }
done

echo "has_error: $has_error"
exit $has_error
