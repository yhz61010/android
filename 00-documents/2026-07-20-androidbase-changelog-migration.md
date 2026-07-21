# Changelog & Migration Guide — `androidbase` security review

> Chinese version: [`2026-07-20-androidbase-changelog-migration-zh.md`](./2026-07-20-androidbase-changelog-migration-zh.md)

This release bundles the `fix/androidbase-security-review` branch: a security review and
hardening pass across the cipher, media, and Android-utility layers. It contains **breaking
changes** to public APIs and on-disk/ciphertext formats, so it **requires a major version bump**.

- **Format:** based on [Keep a Changelog](https://keepachangelog.com/).
- **Previous release:** `5.15.8`.
- **Target version:** _major bump — set at release time (e.g. `6.0.0`)._

---

## [Unreleased] — target major version

### ⚠️ Breaking changes

#### `AESUtil` — ciphertext format & API

- **Ciphertext format changed.** Was `AES/CBC` + static zero IV + 4-byte salt + 1000 PBKDF2
  iterations. Now **AES-GCM authenticated encryption**, layout
  `[version:1][salt:16][iv:12][ciphertext+tag]`, with `version‖salt` bound as GCM AAD.
  - `version=0x01`: GCM + PBKDF2-HMAC-SHA256 / 600k (API 26+)
  - `version=0x02`: GCM + PBKDF2-HMAC-SHA1 / 1.4M (API 21–25)
  - On API 21–25, decrypting `version=0x01` uses a standard PBKDF2-HMAC-SHA256 fallback so
    SHA256 ciphertext stays cross-device readable.
- **One-way compatibility.** `decrypt(...)` reads the version byte; legacy ciphertext still
  decodes via the `@Deprecated legacyDecrypt` path (4-byte salt, zero IV, 1000 iterations).
  But **data encrypted by this version cannot be decrypted by older versions of the library.**
  Coordinate upgrades where ciphertext is shared across app versions.
- **`useSHA512` parameter** now affects **only the legacy decrypt path**; for the new GCM format
  the KDF is chosen by the version byte and `useSHA512` is ignored.
- Removed `SystemClock`-seeded `generateKeyBySHA512/SHA1`; replaced by `generateKey(bits = 256)`
  (`KeyGenerator` + system entropy). `@RequiresApi(O)` removed.

#### `RSAUtil` — signing & padding

- **`sign` / `verify` semantics changed.** Previously a "pseudo-signature" via `Cipher` + private
  key, with `verify` returning the decrypted `ByteArray`. Now `sign(...)` produces a real
  `Signature("SHA256withRSA")` (`ByteArray?`), and **`verify(encodedPubKey, data, signature)`
  returns `Boolean`**. The old pseudo-signature methods were **not retained**.
- **Encryption padding** is now `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`, with an explicit
  `OAEPParameterSpec` forcing the MGF1 mask hash to SHA-256 (otherwise it defaults to SHA-1).
  **Old PKCS#1 v1.5 ciphertext is not interoperable with the new OAEP format.**
- `MAX_ENCRYPT_LEN` changed from `KEY_SIZE/8 - 11` to `190` (OAEP-SHA256). Fragmented
  encrypt/decrypt now split **by bytes** (fixes corruption of multi-byte UTF-8 across fragments).
- Removed the unused `MAX_DECRYPT_LEN`.

#### `PBKDF2Util` — default iteration counts

- **Default iterations for the SHA512 / SHA1 overloads increased** (breaking: the same
  passphrase + salt now derives a different key):
  - `generateKeyWithSHA512(...)` defaults to `ITERATIONS_SHA512 = 220_000` (was 1000)
  - `generateKeyWithSHA1(...)` defaults to `ITERATIONS_SHA1 = 1_400_000` (was 1000)
- If external code relied on the old 1000-iteration derivation, pass `iterations = 1000`
  (or `ITERATIONS_LEGACY`) explicitly.

#### Other public APIs

- **`BluetoothUtil.setPairingConfirmation(...)`**: return type `Unit` → `Boolean` (`false` on failure).
- **`WifiUtil.connectWifi(...)`**: default `enc` changed from `WifiEncType.WEP` to `WifiEncType.WPA`.
- **`YuvUtil`**: six unused public functions marked `@Deprecated`
  (`convertYUV420888ToNV21`, `cropYUV420`, `frameMirror`, `generateFromImage`,
  `i420ToRGBABitmap`, `rgbToI420`) — scheduled for removal in the next major version.

### Added

- `AESUtil.decryptStrict(cipherBytes, secKey)`: accepts **only** the new GCM format and throws on
  authentication failure — never falls back to legacy. Use it when AEAD guarantees are required.
- `PBKDF2Util`: `generateKeyWithSHA256(...)` overload family, plus constants
  `ITERATIONS_SHA256 = 600_000`, `ITERATIONS_SHA512`, `ITERATIONS_SHA1`,
  `ITERATIONS_LEGACY = 1000` (for explicit legacy decrypt only).
- `PBKDF2Util`: built-in PBKDF2-HMAC-SHA256 fallback (RFC 8018) for API 21–25 where the JCA
  provider lacks `PBKDF2WithHmacSHA256`. `@RequiresApi(O)` removed — the SHA256 overloads now work
  on minSdk 21. The fallback triggers only on `NoSuchAlgorithmException`; other failures propagate.

### Fixed (non-breaking hardening)

- **Media / codecs:** NALU start-code operator-precedence bug in `H264Util`/`H265Util`;
  `CodecUtil.findStartCode` out-of-bounds fix; `H265Util.getVps` reuses the fixed `findStartCode`;
  `YuvUtil.yuvRotate270` Y-plane bounds fix + `require` boundary validation on full-frame converters.
- **Encoding:** `Base64Ext.fromBase64` decodes as UTF-8 (symmetric with `toBase64`).
- **Networking:** `ConnectionLiveData` reports the real `networkType` when online;
  `NetworkMonitor` uses `quitSafely()` instead of `interrupt()` (no thread leak);
  `BaseActivity` guards async DNS callbacks with `isDestroyed/isFinishing`, drops `currentFocus!!`.
- **Resources / lifecycle:** `LeoTextureView` surface reuse/release; `PcmToWavUtil` closes streams
  with `use{}`; `SoundEffectPlayer` uses concurrent collections + `openFd().use{}`; `DeviceSound`
  reuses a single `MediaActionSound` + `release()` (no native leak).
- **`CrashHandler`:** chains to the previously installed handler via a per-init local `val` (fixes
  a `StackOverflowError` from repeated init) and wraps both the custom and previous handler calls in
  `runCatching` so a misbehaving reporter cannot break the chain.
- **`BluetoothUtil`:** five reflection calls unified under `runCatching` (log + return `false`).
- **`PhoneCallReceiver`:** call-state machine moved to a thread-safe holder (monitor lock).
- **`CameraUtil.performCrop`:** adds `FLAG_GRANT_WRITE_URI_PERMISSION` + broad exception capture.
  Still emits a `file://` output (see "Known follow-ups").
- **`AESUtil.deriveKey`:** dropped the `SDK_INT ≥ O` guard and faithfully reproduces the KDF from
  the version byte, so `version=0x01` (SHA256) ciphertext is SHA256-derived on API 21–25 too
  (previously mis-derived with SHA1 → GCM authentication always failed cross-device).
- **`RSAUtil.encryptStringByFragment`:** empty-string input goes through a single `encrypt` block,
  fixing encrypt/decrypt asymmetry for empty input.

### Security

- AES-GCM AAD binds `version‖salt`; RSA OAEP forces MGF1 SHA-256; PBKDF2 iteration hardening;
  `decryptStrict` for no-fallback AEAD.
- `PBKDF2Util` passphrase is now encoded to UTF-8 via NIO buffers instead of an immutable `String`
  intermediate, so transient key material can be zeroed.

---

## Migration guide

### 1. RSA `verify` — from bytes to boolean

```kotlin
// Before — verify returned the decrypted payload
val payload: ByteArray? = RSAUtil.verify(pubKey, signedData)
if (payload != null) { /* trusted */ }

// After — sign()/verify() are real RSA signatures
val signature: ByteArray? = RSAUtil.sign(priKey, data)
val ok: Boolean = RSAUtil.verify(pubKey, data, signature!!)
if (ok) { /* trusted */ }
```

### 2. AES ciphertext is not backward-compatible

- Data encrypted by this version **cannot** be read by pre-upgrade clients. If ciphertext is shared
  across app/library versions, upgrade producers and consumers together, or re-encrypt at rest.
- Reading old data still works automatically (version byte → legacy path). Use `decryptStrict(...)`
  when you must reject anything that is not the authenticated GCM format.

### 3. RSA ciphertext is not backward-compatible

- New OAEP-SHA256 ciphertext is not interoperable with old PKCS#1 v1.5 ciphertext. Re-encrypt any
  persisted RSA payloads after upgrading.

### 4. PBKDF2 default iterations changed

```kotlin
// To reproduce a key derived by the OLD 1000-iteration default, pass it explicitly:
val key = PBKDF2Util.generateKeyWithSHA1(passphrase, salt, PBKDF2Util.ITERATIONS_LEGACY)
```

### 5. Minor API signature changes

- `BluetoothUtil.setPairingConfirmation(...)` now returns `Boolean` — check the result.
- `WifiUtil.connectWifi(...)` defaults to WPA — pass `enc` explicitly for WEP networks.
- Migrate off the `@Deprecated` `YuvUtil` functions before the next major release.

---

## Known follow-ups (not release-blocking)

- **`CameraUtil.performCrop`** still uses a `file://` crop output (FileProvider not adopted, because
  some crop apps handle `content://` poorly). On API 24+ under StrictMode this can throw
  `FileUriExposedException` (guarded by a broad catch). Verify against common crop apps on a real
  device before deciding whether to switch to FileProvider.

## Security note

- A Personal Access Token exposed in an earlier working session should be treated as leaked and
  **revoked** at <https://github.com/settings/tokens> if still valid.

---

_See also:_
[pre-release checklist](./2026-07-20-androidbase-pre-release-checklist-zh.md) ·
[cipher security review](./2026-07-20-androidbase-cipher-security-review-zh.md)
