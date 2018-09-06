#!/bin/bash
find . -name '*.kt' | xargs grep -cve '^\s*$'
echo "Total: "
find . -name '*.kt' | xargs grep -ve '^\s*$' | wc -l

