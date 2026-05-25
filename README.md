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

Inside the prompt:

```text
zeus[none]> select ps3                   # turn on PS3 content types
zeus[ps3]> region us                     # toggle US region on
zeus[ps3:US]> sync                       # download catalog TSVs
zeus[ps3:US]> search <title>          # search across cached TSVs
zeus[ps3:US]> info 1                     # details for result #1
zeus[ps3:US]> download 1                 # PKG + license for #1
zeus[ps3:US]> extract 1                  # PSP/PSX: pkg2zip / psxtract
zeus[ps3:US]> help
zeus[ps3:US]> exit
```

Selections are persisted to a `session:` block in the YAML so the
next launch resumes where you left off.

## Tasks

- `bb run [--config PATH]` launches the browser
- `bb test` runs the test suite
- `bb complete '<line>'` prints shell completions for a partial input

## Caching

TSVs cache to `cache_dir` (default `./cache`) and stay fresh for
`cache_expiration_days` days (default 7). After that, the next
`sync` or `search` re-fetches. `refresh on` forces a re-fetch on
every operation.

Downloaded PKGs land in `output_dir/<platform>/<content_id>/`.

To actually run what you download, install on a real console or use
an emulator: [RPCS3](https://rpcs3.net/) (PS3), [Vita3K](https://vita3k.org/) (PSV / PSM),
[PPSSPP](https://www.ppsspp.org/) (PSP), [DuckStation](https://www.duckstation.org/) (PSX, after `extract`).

## License

MIT. See [LICENSE](LICENSE).
