# Third-party distribution notices

These notices accompany the third-party files identified below. Their licenses
remain separate from SPlayer's root license. Original notices in source files are
retained. This directory is in Vite's public asset tree so the existing web asset
packaging can carry the notices into subsequent Android builds.

This is a source and license index, not a declaration that every existing binary
has been rebuilt or that every redistribution condition has been verified.
Previously built APKs do not acquire these files automatically.

## Node.js, V8 and nodejs-mobile-cordova

- Files: `android/app/libs/cdvnodejsmobile/include/node/` and
  `android/app/libs/cdvnodejsmobile/libnode/include/node/`.
- Checked header versions: Node.js 12.19.0 and V8 7.8.279.23. The Android runtime
  preparation uses `nodejs-mobile-cordova` 0.4.3, which supplies matching headers.
- [Node.js 12.19.0 license collection](https://github.com/nodejs/node/blob/v12.19.0/LICENSE):
  [local copy](node-v12.19.0-LICENSE.txt), including Node.js and bundled dependency
  copyright/license notices. This does not assert that mobile runtime binaries are
  byte-identical to the desktop Node.js release.
- [V8 license at that Node.js revision](https://github.com/nodejs/node/blob/v12.19.0/deps/v8/LICENSE):
  [local copy](v8-7.8.279.23-LICENSE.txt), the BSD-style license referenced by the
  checked-in V8 headers.
- [nodejs-mobile-cordova 0.4.3 MIT license](https://github.com/JaneaSystems/nodejs-mobile-cordova/blob/v0.4.3/LICENSE):
  [local copy](nodejs-mobile-cordova-0.4.3-LICENSE.txt).

## FFmpeg audio player WebAssembly

- Files: `public/wasm/ffmpeg.wasm` and the associated JavaScript wrapper used by
  `src/core/audio-player/ffmpeg-engine/ffmpeg.worker.ts`.
- The WASM Git blob is `3907946cdec5a0b581abbc36c75328cb6b2907c6`, matching
  SPlayer upstream. [SPlayer PR #828](https://github.com/SPlayer-Dev/SPlayer/pull/828)
  identifies the decoder fix and links its
  [source/build revision](https://github.com/apoint123/ffmpeg-audio-player/tree/38f1664ea3f9cb32007eb90dc13e25be84b34b6b).
- That revision declares GPLv3 for the wrapper:
  [license source](https://github.com/apoint123/ffmpeg-audio-player/blob/38f1664ea3f9cb32007eb90dc13e25be84b34b6b/LICENSE),
  [local copy](ffmpeg-audio-player-GPLv3.txt).
- Its Dockerfile and build scripts specify Emscripten 4.0.22, FFmpeg n8.0.1 with
  `--enable-gpl`, and SoundTouch 2.4.0, statically linked into the WASM.
  FFmpeg's [GPLv2 text](ffmpeg-n8.0.1-GPLv2.txt) and
  [license classification](ffmpeg-n8.0.1-LICENSE.md) are copied from
  [the n8.0.1 source](https://github.com/FFmpeg/FFmpeg/tree/n8.0.1).
  SoundTouch's LGPL 2.1 [COPYING.TXT](soundtouch-2.4.0-COPYING.TXT) comes from its
  [official 2.4.0 source archive](https://codeberg.org/soundtouch/soundtouch/archive/2.4.0.tar.gz).
- These links identify upstream source and build instructions. A bit-for-bit
  rebuild of this binary and the complete corresponding-source delivery for a
  release have not been verified. Including license texts alone does not settle
  source-code or relinking obligations.
- See [WASM_SOURCES.md](WASM_SOURCES.md) for the pinned source downloads, actual
  distribution paths, applicable source-access conditions, and remaining release
  delivery checks. Binary identities are recorded in [wasm-sources.json](wasm-sources.json).

## ferrous-opencc

- File: `native/ferrous-opencc-wasm/pkg/ferrous_opencc_wasm_bg.wasm`, used through
  the `@opencc` alias by `src/utils/opencc.ts`.
- The local wrapper declares dependency `ferrous-opencc` 0.3.1. That crate's
  published VCS metadata identifies source revision
  `398daf0ec19a5535278d147f46441337efb530a4`.
- The revision's [Cargo.toml](https://github.com/apoint123/ferrous-opencc/blob/398daf0ec19a5535278d147f46441337efb530a4/Cargo.toml)
  credits apoint123 and declares Apache-2.0. Its
  [README](https://github.com/apoint123/ferrous-opencc/blob/398daf0ec19a5535278d147f46441337efb530a4/readme.md)
  also declares Apache-2.0 and identifies [BYVoid/OpenCC](https://github.com/BYVoid/OpenCC)
  as the dictionary/configuration source.
- No LICENSE or NOTICE file was present in that crate/source revision's tree.
  [Apache-2.0.txt](Apache-2.0.txt) is the unmodified
  [Apache Software Foundation license text](https://www.apache.org/licenses/LICENSE-2.0.txt);
  no third-party copyright statement has been invented.
- The vendoring revision, its historical Cargo lockfile, and dependency version
  markers inside the binary are now identified in [WASM_SOURCES.md](WASM_SOURCES.md).
  That evidence is stronger than a Cargo version constraint; it does not claim a
  reproducible rebuild or a complete build attestation.
- Apache-2.0 does not impose corresponding-source delivery for this component.
  Its identified MIT/BSD dependency notices are supplied separately in
  [OPENCC_DEPENDENCY_NOTICES.txt](OPENCC_DEPENDENCY_NOTICES.txt). Availability of
  source, inclusion of notices, and compliance of a future APK are separate checks.

## QuickJS in the embedded Node service

The Node service also redistributes an inline Base64 WASM through
`@tootallnate/quickjs-emscripten` 0.23.0, not a standalone `.wasm` file. The npm
wrapper's MIT license is already copied with that package. The underlying
QuickJS 2021-03-27 [MIT notice](quickjs-2021-03-27-LICENSE.txt) is supplied here
from the source revision associated with a byte-identical upstream npm binary.
See [WASM_SOURCES.md](WASM_SOURCES.md) for the scoped-package gitHead limitation,
the accessible source revision, and actual APK distribution evidence.

## Poppins logo font

- File: `public/fonts/logo.woff2`; used by `public/fonts/font.css` for the SPlayer
  wordmark in navigation.
- Inherited from `SPlayer-Dev/SPlayer/public/fonts/logo.woff2`, Git blob
  `10568d930c3ded001f0e17ae15d3e2e7aa664c77`, first traceable at
  [2b6d68ec](https://github.com/SPlayer-Dev/SPlayer/commit/2b6d68ecbd65cf06d28324863febb513d3d887bd).
- The font identifies itself as Poppins Medium 4.004 and retains:
  `Copyright 2020 The Poppins Project Authors (https://github.com/itfoundry/Poppins)`.
- All seven mapped glyph outlines and horizontal metrics match Poppins Medium
  4.004 in the Google Fonts
  [4.004 import revision](https://github.com/google/fonts/tree/7dc16b7de42db624b902f2292b68ed9e489e5053/ofl/poppins).
  The matching copyright and SIL Open Font License 1.1 are supplied unmodified in
  [poppins-4.004-OFL.txt](poppins-4.004-OFL.txt), from that revision's `OFL.txt`.
- The subset generator/configuration was not found; the binary is unchanged.

## Notification icon font — authorization pending

Status: **SOURCE_CONFIRMED_LICENSE_UNCONFIRMED**.

- File: `android/app/src/main/assets/iconfont_notification.ttf`, 2,964 bytes;
  Git blob `ce36d59d06853c0e340c42b43fa804efcd1d2cfd`.
- Added by the Android upstream port in
  [7b255eac](https://github.com/SPlayer-Dev/SPlayer-for-Android/commit/7b255eac568e19ef0b37ab918d2610377e4b64e1).
  It remains distributed by Android upstream; no subsequent path change was
  found in its public history at inspection.
- Metadata: `Created by iconfont`, `iconfont`, `Version 1.0`,
  `Generated by svg2ttf from Fontello project.`, `http://fontello.com`.
- Glyph mapping: `e600` lyrics, `e601` favorite filled, `e60a` favorite outline,
  `e63c` previous, `e63d` play, `e63e` next, `e65f` pause. These are used by
  `PlaybackManager.java` for notification media controls.
- The introducing commit/tree, subsequent path history, upstream README/license,
  and the port author's public project trees did not establish an original
  iconfont/Fontello configuration, SVG source set, glyph rights holder, or a
  font-specific redistribution license. Tool metadata and matching Unicode
  values do not establish icon ownership or authorization.
- Further geometry comparison identifies three source candidates independently
  of Unicode: lyrics matches iconfont icon 2929181 (uploader `Noah-Simon`), and
  previous/next match icons 8341784/8341786 (`一只老羊来了`, collection 15977,
  `老羊图标坊`). Official SVG outlines agree with the font to within 0.6 font
  units at 1024 units/em under sampled curve comparison, consistent with font
  coordinate rounding. Public project configurations also link these icon IDs;
  this is source evidence, not an authorization grant.
- The official iconfont records expose no applicable license for these records;
  the other four glyphs and the exact original seven-glyph export project remain
  unconfirmed. The public records can be inspected at the iconfont
  [lyrics detail](https://www.iconfont.cn/api/icon/iconInfo.json?id=2929181),
  [previous detail](https://www.iconfont.cn/api/icon/iconInfo.json?id=8341784), and
  [next detail](https://www.iconfont.cn/api/icon/iconInfo.json?id=8341786) endpoints.
- **P1 — THIRD_PARTY_FONT_LICENSE_UNCONFIRMED:** before release, obtain the original
  icon project/source and its applicable license or an explicit redistribution
  authorization covering these glyphs from the rights holder. The root project
  license and the generator's license are not substituted for that evidence.
  The font has not been removed, replaced, or relicensed.
