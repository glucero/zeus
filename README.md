# zeus

Interactive CLI for browsing PlayStation catalog metadata
across PSV, PS3, PSP, PSX, PSM. Reads community-maintained
TSV files.

Written in [Babashka](https://babashka.org/), so no JVM.

## Requirements

- [Babashka](https://github.com/babashka/babashka#installation) >= 1.0
- `pkg2zip` and `psxtract` on `PATH` for PSP/PSX extraction (optional)
- A `config.yaml`. Copy `config.example.yaml`.

## Quick start

```sh
cp config.example.yaml config.yaml
bb run --config config.yaml
```

Or symlink `bin/zeus` onto your `PATH` and use it directly:

```sh
zeus --config config.yaml
```

Everything is selected by default, so search works immediately:

```text
zeus> sync                               # download every catalog
zeus> search <title>                     # search across everything
zeus> info 1                             # details for result #1
zeus> download 1                         # PKG + license for #1
zeus> exit
```

The bare `zeus> ` prompt means nothing is narrowed.

To narrow to one platform or region:

```text
zeus> unselect all
zeus[no-type]> select ps3
zeus[ps3]> unregion all
zeus[ps3:no-region]> region us
zeus[ps3:US]>
```

Selections are persisted to a `session:` block in the YAML so the
next launch resumes where you left off.

## Example download

Once you've narrowed to what you want, `info` shows the catalog row
and `download` pulls the PKG and license file:

```text
zeus> search <some title>
  found 1 result(s):
    1. ps3 | games | EXAMPLE TITLE                       | US   |   4.21 GB
zeus> info 1
  ----------------------------
  EXAMPLE TITLE
  ----------------------------
  Title ID:   NPUB99999
  Content ID: ABCD-EFGH12345_00
  Region:     US
  Platform:   ps3 (ps3_games)
  Size:       4.21 GB
  PKG:        [ok] available
  RAP:        [ok] available
  ----------------------------
zeus> download 1
  progress: 100.0% (4310.5/4310.5 MB)
  [ok] PKG:     EXAMPLE TITLE [ABCD-EFGH12345_00].pkg
  [ok] license: EXAMPLE TITLE [ABCD-EFGH12345_00].rap
```

The PKG and `.rap` land under `downloads/<platform>/<content_id>/`:

```text
downloads/ps3/ABCD-EFGH12345_00/
├── EXAMPLE TITLE [ABCD-EFGH12345_00].pkg
└── EXAMPLE TITLE [ABCD-EFGH12345_00].rap
```

From there it's hardware or emulator: install the PKG on a real
console via its package installer, or open it in an emulator
(*File > Install Package* in RPCS3 / Vita3K). PSP and PSX PKGs
need the `extract` step first to produce a disc image the emulator
can load.

For the architecture story behind this, see [WHY_IT_WORKS.md](WHY_IT_WORKS.md).

## Tasks

- `bb run [--config PATH]` launches the browser
- `bb test` runs the test suite
- `bb complete '<line>'` prints shell completions for a partial input
- `bb firmware <ps3|psv> [dir]` downloads the system PUP from Sony's CDN

## Caching

TSVs cache to `cache_dir` (default `./cache`) and stay fresh for
`cache_expiration_days` days (default 7). After that, the next
`sync` or `search` re-fetches. `refresh on` forces a re-fetch on
every operation.

Downloaded PKGs land in `output_dir/<platform>/<content_id>/`.

To actually run what you download, install on a real console or use
an emulator: [RPCS3](https://rpcs3.net/) (PS3), [Vita3K](https://vita3k.org/) (PSV / PSM),
[PPSSPP](https://www.ppsspp.org/) (PSP), [DuckStation](https://www.duckstation.org/) (PSX, after `extract`).

## Firmware

RPCS3 and Vita3K need the original Sony system PUPs to boot. Both
are still served from Sony's CDN; `bb firmware` fetches them
straight from there:

- `bb firmware ps3` resolves the latest PS3 firmware via Sony's
  update-list endpoint and downloads `PS3UPDAT.PUP` to
  `./firmware/ps3/`. Install in RPCS3 via *File > Install Firmware*.
- `bb firmware psv` downloads the final PSV firmware (3.74) to
  `./firmware/psv/PSVUPDAT.PUP`. Install in Vita3K via
  *File > Install Firmware*.

PPSSPP ships its own PSP firmware, so nothing to fetch. PS1 BIOS
files for DuckStation are not distributed by Sony; they have to
come from a real PS1 / PS2 / PS3 you own.

## License

MIT. See [LICENSE](LICENSE).
