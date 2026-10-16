# WebAssembly source and distribution index

This index distinguishes provenance, license texts, source availability, and
source delivery. It does not assert that an existing APK contains newly added
notices, or that an external source link alone fulfills all release obligations.
Checksums and stable source references are in [wasm-sources.json](wasm-sources.json).

## Files actually distributed

| Tracked input | Origin / generation | Version / license | Application use | Distribution |
| --- | --- | --- | --- | --- |
| `public/wasm/ffmpeg.wasm` | Precompiled upstream FFmpeg Audio Player artifact; Emscripten/Docker build | Wrapper source revision `38f1664e...`; FFmpeg n8.0.1; SoundTouch 2.4.0; GPLv3 wrapper, GPLv2-or-later FFmpeg build, LGPL2.1 SoundTouch | `ffmpeg.worker.ts` initializes it at `/wasm/ffmpeg.wasm` when the FFmpeg engine is selected and isolation checks pass | Public web asset; present in all four inspected Beta APKs at `assets/public/wasm/ffmpeg.wasm` |
| `native/ferrous-opencc-wasm/pkg/ferrous_opencc_wasm_bg.wasm` | Precompiled upstream vendored artifact; Rust / wasm-pack / wasm-bindgen | Wrapper 0.1.0; ferrous-opencc 0.3.1, Apache-2.0; permissive dependencies described below | `@opencc` dynamic import in `src/utils/opencc.ts`; lyric conversion initializes it when enabled | Vite emits a hashed asset; present in all four inspected Beta APKs at `assets/public/assets/ferrous_opencc_wasm_bg-Cs2bcDdF.wasm` |
| Base64 in `@tootallnate/quickjs-emscripten/dist/generated/emscripten-module.WASM_RELEASE_SYNC.js` | Precompiled npm dependency 0.23.0, copied/transpiled by the embedded Node packager; the embedded WASM bytes are preserved | QuickJS 2021-03-27; wrapper 0.23.0; MIT | Embedded Node PAC proxy resolution, not the web UI's audio/lyric engine | Present in all four inspected Beta APKs under `assets/www/nodejs-project/vendor/node_modules/`; no separate `.wasm` file |

The existing `dist/capacitor`, Android public assets, and debug/release merged
asset trees contain copies of these two binaries, not additional implementations.
No tracked Git LFS WASM or third standalone WASM was found. The inline QuickJS
binary is an additional implementation and must not be omitted by an extension-only
inventory. Historical
`decode-audio.wasm` blobs were predecessors of the FFmpeg implementation and are
not current distributed inputs. Neither current artifact is treated as unused
solely because Android may select another playback engine.

APK inclusion was checked by reading the four existing `v3.0.16-beta.6` archives,
without rebuilding or modifying them. This proves packaging, not execution.
The code paths above establish conditional web loading; they do not claim every
browser session enables both features. The current draft Release has no uploaded
APK attachments; a future upload of those APKs would distribute their embedded
WASM. GitHub's source archive for a commit containing these files also includes
the tracked binaries, but is not the complete FFmpeg dependency source.

## FFmpeg: source available, release delivery still needs confirmation

### Provenance

[SPlayer PR #828](https://github.com/SPlayer-Dev/SPlayer/pull/828) publishes the
decoder fix and identifies
[apoint123/ffmpeg-audio-player revision 38f1664e](https://github.com/apoint123/ffmpeg-audio-player/tree/38f1664ea3f9cb32007eb90dc13e25be84b34b6b).
SPlayer's importing revision is
`765d11b5ef52d48654e7f310eaa9697da0c569cd`; the current binary has the same Git
blob as that upstream input. Attribution is to the publishing upstream
contributor; the exact build operator/runner is not independently attested.

The wrapper C++ source is **not** in the current SPlayer source tree. The linked
revision supplies `cpp/audio-decode.cpp`, `Dockerfile`,
`scripts/build-ffmpeg.ts`, `scripts/build.ts`, and its dependency/build settings.
It specifies Emscripten 4.0.22, static linking, `--enable-gpl`, FFmpeg n8.0.1,
and SoundTouch 2.4.0. It does not identify downstream source patches to those
libraries. A bit-for-bit rebuild is not claimed.

### Source downloads and build entry points

- [Wrapper and build scripts, exact source archive](https://github.com/apoint123/ffmpeg-audio-player/archive/38f1664ea3f9cb32007eb90dc13e25be84b34b6b.tar.gz).
- [FFmpeg n8.0.1, exact source archive](https://github.com/FFmpeg/FFmpeg/archive/894da5ca7d742e4429ffb2af534fcda0103ef593.tar.gz).
- [SoundTouch 2.4.0, official source archive](https://codeberg.org/soundtouch/soundtouch/archive/2.4.0.tar.gz).
- [Build instructions](https://github.com/apoint123/ffmpeg-audio-player/blob/38f1664ea3f9cb32007eb90dc13e25be84b34b6b/README.md):
  Docker and Bun; `bun install`, then `bun run build:wasm`. The pinned Dockerfile
  and build script are the authoritative compilation options.
- License copies: [wrapper GPLv3](ffmpeg-audio-player-GPLv3.txt),
  [FFmpeg GPLv2](ffmpeg-n8.0.1-GPLv2.txt),
  [FFmpeg license description](ffmpeg-n8.0.1-LICENSE.md),
  [SoundTouch LGPL2.1](soundtouch-2.4.0-COPYING.TXT).

### What remains before distribution

The wrapper's GPLv3 section 6 requires an applicable corresponding-source
delivery method. For network distribution, section 6(d) permits another source
server with equivalent copying facilities and clear directions next to the
object code; the distributor remains responsible for availability. It does not
require a written section 6(b) offer in every case.

FFmpeg's selected GPLv2-or-later build also requires the applicable source
conditions; its GPLv2 section 3 describes source distribution and equivalent
network access. SoundTouch LGPL2.1 section 6 requires the applicable materials
for modification/relinking when distributing the linked executable. Its section
3 provides an option to apply ordinary GPL terms, but this index does not claim
that an undocumented conversion of third-party notices has already happened.

The identified source projects are publicly available. However, the release
still needs a verified complete corresponding-source/relinking set for this
binary, and an explicit source delivery arrangement accompanying its actual
download. This file records precise acquisition routes; it is **not** a
three-year written source offer and does not claim that a draft with no assets
has already delivered source. Do not mark `SOURCE_OFFER_ALREADY_PRESENT` merely
because source is somewhere on GitHub. Do not change or publish a Release to
settle this without authorization.

A review-only source set can be prepared from the three original archives above,
retaining their notices and the wrapper's build scripts. Preparation is separate
from actually providing equivalent source access with a binary download.

## OpenCC: permissive source terms, not a mandatory source offer

### Provenance and source

[SPlayer PR #755](https://github.com/SPlayer-Dev/SPlayer/pull/755) explicitly says
the compiled OpenCC binaries were checked in to avoid downstream build problems.
Its vendoring revision
[142a0c56](https://github.com/SPlayer-Dev/SPlayer/tree/142a0c5690a7c606a021c6ed10b81726758a90f0)
is authored by apoint123 and contains the matching binary, wrapper source,
`Cargo.toml`, `Cargo.lock`, and `scripts/build-native.ts`.

The current repository includes the wrapper source at
`native/ferrous-opencc-wasm/src/lib.rs` and the `wasm-pack build --target web`
entry point in that directory's `package.json`. It does not include the complete
third-party dependency source or the historical root Cargo lockfile.

- [Wrapper, vendored binary and historical lockfile archive](https://github.com/SPlayer-Dev/SPlayer/archive/142a0c5690a7c606a021c6ed10b81726758a90f0.tar.gz).
- [ferrous-opencc 0.3.1 source archive](https://crates.io/api/v1/crates/ferrous-opencc/0.3.1/download);
  its crate VCS metadata points to
  [398daf0e](https://github.com/apoint123/ferrous-opencc/tree/398daf0ec19a5535278d147f46441337efb530a4).
- The historical lockfile and binary markers agree on ferrous-opencc 0.3.1,
  bincode 2.0.1, fst 0.4.7, serde_json 1.0.149, wasm-bindgen 0.2.106,
  zstd-safe 7.2.4, and zstd-sys 2.0.16 with Zstd 1.5.7. Binary producer metadata
  identifies rustc 1.94.0-nightly (`2850ca829`, 2026-01-13).
- The crate's built-in dictionaries are available in its source archive; its
  README identifies BYVoid/OpenCC as their source. An earlier independent
  dictionary revision is not established by this evidence.

### License conditions

ferrous-opencc declares Apache-2.0 in the inspected version's Cargo metadata and
README. That revision has no separate LICENSE/NOTICE file. The supplied
[Apache-2.0 text](Apache-2.0.txt) and attribution index retain that declaration
without inventing a copyright statement. Apache-2.0 section 4 does not impose a
corresponding-source offer; absence of an exact rebuild is not itself a missing
source-offer violation.

The known bincode and fst MIT notices, Zstd native/binding BSD notices, and
xxHash header attribution are retained verbatim in
[OPENCC_DEPENDENCY_NOTICES.txt](OPENCC_DEPENDENCY_NOTICES.txt). The inspected
serde_json, wasm-bindgen, unty and zstd Rust wrappers offer Apache-2.0 as a license
option; the Apache text is already included. This source-offer conclusion is
specific to these permissive terms, not to arbitrary future WASM dependencies.

This establishes source provenance and the absence of a mandatory source offer
under the identified permissive licenses. It does not claim that a historical
82-package lockfile is an 82-library runtime bill of materials: it also includes
host build tools, macros and other targets. A complete link map was not supplied
by upstream. Existing APKs have not been repackaged with these notices.

## QuickJS: inline npm WASM with MIT terms

The embedded Node service's dependency chain includes `pac-proxy-agent` /
`pac-resolver` / `degenerator` and `@tootallnate/quickjs-emscripten` 0.23.0.
`pnpm-lock.yaml` locks that npm version. `scripts/build-android-node.ts` copies
runtime dependencies and transpiles JavaScript; the QuickJS Base64 WASM in the
existing APKs matches the official npm archive byte-for-byte.

- [Official npm archive](https://registry.npmjs.org/@tootallnate/quickjs-emscripten/-/quickjs-emscripten-0.23.0.tgz),
  version 0.23.0, publisher namespace `tootallnate`.
- npm records gitHead `891a55e8df93a667e5980863f2995e8bfef54872`, which could not
  be resolved in the declared public repositories at inspection. This is not
  replaced with an invented source revision.
- Independently, the official unscoped `quickjs-emscripten` 0.23.0 npm package
  contains exactly the same 456,202-byte WASM (checksum in the index). Its
  accessible source revision is
  [75fcb3fc](https://github.com/justjake/quickjs-emscripten/tree/75fcb3fc27d4857207266ef9c621d93eb79b868d),
  which includes the C wrapper, QuickJS source, Makefile, and Emscripten 3.1.35
  settings. `quickjs/VERSION` identifies 2021-03-27.
- The npm wrapper's MIT `LICENSE` is already present alongside its code in all
  four inspected APKs. The underlying QuickJS source's matching
  [MIT copyright/license](quickjs-2021-03-27-LICENSE.txt) is additionally supplied
  here, unmodified from that accessible source revision, preserving Fabrice
  Bellard and Charlie Gordon's notices.
- MIT requires the copyright/permission notice to accompany distributed copies;
  it does not require a corresponding-source offer. The original wrapper's
  public source and the byte-identical upstream binary provide a documented
  source route despite the scoped package's unresolved gitHead. This does not
  assert an independently witnessed npm build or an exact rebuild.
- The ordinary web asset tree does not load this QuickJS dependency. It is
  packaged for the Android Node service, where the PAC proxy path can initialize
  it; packaging does not mean that every playback/request invokes QuickJS.
