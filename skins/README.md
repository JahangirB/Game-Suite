# Ship Battle skins

The skins players can download in Ship Battle (the **Skins** button at the bottom of the game).
The app lists `index.json`, shows each skin's picture (`<id>.png`) and downloads `<id>.json`.
Nothing about a skin is built into the app, so a new one needs no app update: once its files are
on the repository's default branch, phones list it under **Available**.

Builds made by this repository's GitHub Actions read the skins from this folder of the same
repository (`https://raw.githubusercontent.com/<owner>/<repo>/HEAD/skins`), so a fork lists its
own skins. Local builds have no store.

## Adding or changing a skin

1. Draw it in `scripts/skins/skins.js` with the kit in `scripts/skins/kit.js`: a sea, a ship for
   each kind, a hit and a miss, on a 2D canvas. Give it an `id` (lowercase letters, digits and
   dashes) and a `version`. When you change a skin that's already published, raise its version
   so phones that have it are offered the update.
2. Run `node scripts/make-skins.js --previews` (the pictures need Playwright with Chromium).
   It writes `<id>.json`, `<id>.png` and `index.json` here.
3. Commit the files. The app's tests check every listed skin reads.

`scripts/make-skins.js` describes the file format.
