# Why it works

These games are still downloadable years after Sony shut the
storefront down. That's down to some architectural choices that
weren't really about preservation. Sony just built a CDN the way
you'd build any CDN, and the way you build a CDN happens to also
be the way you build something that outlives its commercial
purpose.

## The shutdown

Sony ran the PlayStation Store for PS3 from 2006, with PSP and
PSV stores added later. The wind-down went roughly like this:

- **March 2021**: Sony announces the PS3, PSP, and PSV
  storefronts will close that summer.
- **April 2021**: after public pushback, Sony walks back the PS3
  and PSV closures; PSP's stays on the schedule.
- **July 2021**: the PSP storefront closes for new purchases.
- **Late 2021**: web and mobile access to the PS3, PSP, and PSV
  storefronts is removed. The on-console stores survive but get
  progressively worse: search breaks, listings vanish, pages
  hang. The catalog is still there; finding things through
  Sony's own UI mostly isn't.

But the CDN is still up. The files are right where they always
were, on Sony's own servers, addressable by the same IDs they
always had.

## The store and the CDN

When you "bought a game from the Store" the console was doing
two separate things. One was talking to a storefront, a web
service that listed games, prices, screenshots, and (after
purchase) the URL of the installer. The other was downloading
that installer from a CDN — a different set of servers entirely,
whose only job was to serve files to whoever asked for them by
URL.

Nothing unusual about this split. Almost every online store
works this way; the storefront and the CDN scale and fail
independently of each other.

Sony shut down the storefront. The CDN didn't go anywhere.

## The Content ID

Files on the CDN are identified by their Content ID, a string
like `ABCD12345` or `WX0000-YZAB01234_00`. Each Content ID is
unique across Sony's whole catalog, and once one is assigned it
never gets reused. The URL on the CDN is just a function of the
ID, deterministically:

```
Content ID:  ABCD-EFGH12345_00
URL:         http://zeus.dl.playstation.net/cdn/ABCD/EFGH12345_00/<package>.pkg
```

First two segments become the path, the filename comes from the
catalog row. That's it. No account, no permission, no
storefront. Once you have the row, you have the URL.

None of this was about preservation. It's just how you build a
CDN that doesn't fall over: immutable IDs, stateless delivery.
The side effect is that any file ever published is still
trivially fetchable if someone remembers the ID.

## The lost index

The thing that actually died was the index. The storefront knew
"title X is `ABCD-EFGH12345_00`, costs $20, here's a screenshot."
Without it, the IDs still work but you have no way to find out
which ID means which.

Years before the shutdown, hobbyists were already scraping the
storefront API and assembling the missing index as a set of TSV
files. A row from `ps3_games.tsv` looks roughly like this
(truncated for width):

```
Title ID   Region  Name           PKG direct link                          Content ID            File Size    RAP
NPUB99999  US      Example title  http://zeus.dl.../EFGH12345_00/...pkg    ABCD-EFGH12345_00     1234567890   a4b1c2...
```

One file per content type — `ps3_games.tsv`, `psv_dlcs.tsv`, and
so on — maintained as community projects and updated whenever
new content is discovered. zeus just consumes these.

## Licenses

PKGs from the CDN are encrypted. Sony's DRM model wasn't "block
the download" — it was "anyone can have the encrypted blob, only
the right license unlocks it."

PS3 uses something called RAP: a 16-byte file tied to the
account that bought the game. The console runs the RAP through a
fixed dance of AES rounds and lookup tables to produce a 16-byte
`klicensee`, and that `klicensee` is the key that actually
decrypts the PKG's file table. PSV uses zRIF, which is just a
base64+zlib-packaged RIF carrying the `klicensee` directly along
with metadata about what content it unlocks. Either way you end
up at a `klicensee`, and either way the PKG is opaque bytes
without one.

The community catalogs carry RAP and zRIF data harvested from
various sources (purchased accounts, leaks, dumps). The decoded
form is what an emulator or real console expects to find sitting
next to the PKG.

## So the tool is small

Most of the actual work happens elsewhere. Sony's CDN serves the
bytes. The community TSVs carry the IDs and licenses. Your
console or emulator handles installation. zeus is basically a
TSV parser plus some URL math, and there's not really much to
it. The infrastructure underneath is doing the heavy lifting.
