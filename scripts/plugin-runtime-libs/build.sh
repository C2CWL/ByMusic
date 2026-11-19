#!/bin/sh
# Rebuild the bundled plugin libraries. Run manually: npm ci && npm run build
# Each output defines `var __bm_lib = <module.exports>`; prelude.js wraps and evaluates it
# when a plugin calls require(<name>). cheerio uses its htmlparser2-only "slim" entry.
set -e
OUT=../../app/src/main/assets/plugin-runtime/lib
mkdir -p "$OUT" .entries
for spec in "crypto-js:crypto-js" "dayjs:dayjs" "he:he" "big-integer:big-integer" "qs:qs" "cheerio:cheerio/lib/slim"; do
  name=${spec%%:*}
  mod=${spec#*:}
  echo "module.exports = require('$mod');" > ".entries/$name.js"
  npx esbuild ".entries/$name.js" --bundle --minify --format=iife --global-name=__bm_lib \
    --platform=browser --target=es2020 --legal-comments=eof --outfile="$OUT/$name.js"
done
rm -rf .entries
