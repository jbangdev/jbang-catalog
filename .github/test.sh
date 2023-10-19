#!/bin/bash

has_error=0

while read -r file; do 
    if ! jbang build "$file"; then
        echo "Error building $file"
        has_error=1
    fi
done < <(jq -r '.aliases[]["script-ref"] | select(contains(".java"))' jbang-catalog.json)

echo "has_error: $has_error"
exit $has_error