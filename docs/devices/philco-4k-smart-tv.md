# Philco 4K SMART TV: hardware, firmware and session notes

Collected on 2026-10-20 in one session against a Brazilian unit, using upstream
`687b5ee` through the fork `eddysangalli/cleanyourtv`. Network ADB on Wi-Fi. The debloat list for
this set is `examples/philco-4k-smart-tv/debloat.txt` (provisional, same-day, soak pending).

## Identify the firmware, not just the retail brand

| Field | Observed value |
|---|---|
| Retail branding | Philco (Brazil); sold as "4K SMART TV" |
| `ro.product.model` | `4K SMART TV` (generic; no size or year in the string) |
| `ro.product.manufacturer` | `KTC` (the OEM behind this Philco) |
| `ro.product.device` | `zhongshan` |
| Android / SDK | `11` / `30` |
| `ro.build.display.id` | `RTM5.220609.082` |
| Build date | `Fri Aug 19 00:12:46 UTC 2022` |
| Security patch | `2022-09-01` (no updates since; firmware frozen in 2022) |
| `ro.hardware` / `ro.board.platform` | `m7632` / `m7632` (MStar/MediaTek TV SoC) |
| `ro.adb.secure` / `service.adb.tcp.port` | `1` / `5555` |

Philco is not the builder here: `ro.product.manufacturer` says KTC, and the platform string `m7632`
is the MStar/MediaTek family used across KTC-built Google TV sets. Match on `ro.product.device`
(`zhongshan`) and the build id, not on the store brand.

## Hardware observed through the shell

| Component | Observed value |
|---|---|
| CPU | 4 cores, ARM Cortex-A55 (`CPU part 0xd05`), ~1.1-1.2 GHz, BogoMIPS 2288.82 |
| ABI list | `armeabi-v7a,armeabi` — the OS userland is **32-bit only**, though the cores are ARMv8 |
| GPU | `ARM Mali-G52` (Bifrost), `OpenGL ES 3.2 v1.r25p1` |
| RAM | `MemTotal 1830732 kB` (~1.75 GB; the rest is GPU/reserved) + `SwapTotal 511996 kB` (zram) |
| `ro.config.low_ram` | `false`; dalvik `heapgrowthlimit=192m`, `heapsize=512m` |
| Display (UI) | `wm size` = 1920x1080, density 320 (video decodes up to 4K; the UI is FHD) |
| Storage | `/data` = 4 GB total, 49% used at test time |
| Free RAM at session | `MemAvailable` ~760 MB with the stock launcher running |

A 2 GB-class set with half the storage of a phone: every MB reclaimed from launcher/ad services is
visible to the user.

## Debugging and connection

Developer options opened with the usual 7 taps on the build number; USB debugging enabled; port
`5555` answered over TCP on the same Wi-Fi and `tools/tv.py --probe` connected without a pairing
step beyond the on-TV authorization prompt. Two saved WPA2 networks were present; their creator was
not checked (setupwraith is kept enabled below, so it does not matter on this unit). The SSIDs are
deliberately not recorded here.

Screenshots work on this MediaTek: `screencap -p` succeeds and `tools/tv.py --pull` retrieves the
file. The `open mma dev failed` line printed by the screencap path is noise; the PNG is valid.

## Home handover: setupwraith is not a claimant here

`cmd package query-activities --brief -c android.intent.category.HOME -a android.intent.action.MAIN`
reported exactly two candidates:

- `com.google.android.tvlauncher/.MainActivity`: priority **2** (the stock Home, scripts' default).
- `com.android.tv.settings/.system.FallbackHome`: priority **-1000**.

`com.google.android.tungsten.setupwraith` is installed (its `wwtv` and Google setup companions are
present) but its `RecoveryActivity` does **not** claim HOME on this firmware, unlike the Xiaomi,
TCL and Changhong lists where it must be disabled for a custom launcher. Consequence: setupwraith is
deliberately **left enabled** — no Home-takeover fight, no risk of losing the saved Wi-Fi password
(see the `xiaomi-mi-box-s-2nd-gen` note where Wi-Fi dropped right after disabling it), and casting
credentials stay where they are. Re-check with the query above on another unit before assuming the
same.

## What was disabled, and what stayed

The list `examples/philco-4k-smart-tv/debloat.txt` (10 packages, `pm disable-user`, reversible)
covers Google recommendation rows, KTC Play Auto Install, feedback/RLZ attribution, calendar sync
adapter, ambient screensaver, Play Games, Google TV/Play Movies, print spooler and Prime Video
(off by choice). Netflix, YouTube, YouTube Music, Google Assistant (`katniss` + `tv.assistant`),
Chromecast (`mediashell`), the phone remote and Dolby audio were kept. The recovery record written
by `tools/disable.sh` is the only copy of the pre-change state; keep it.

Kept despite bloat-shaped names: `fusion.android.tv.demo` (on the MTC-built Hitachi the same
package serves the remote's Input button and Bluetooth pairing — untested here, disable only if a
retail demo loop shows up), `com.mediatek.leanbacklauncher.partnercustomizer` (the `tvlauncher.widget`
provider `com.mediatek.tv.service` wants after reboot), all `com.mediatek.*` (tuners, inputs, Live
TV `com.mediatek.wwtv.tvcenter`) and all `com.ktc.*` / factory menus.

## Validation scope

- Probe, package inventory, screenshot round-trip and the debloat were run 2026-10-20.
- Reboot survival, standby/wake, HDMI inputs, tuner/Live TV, casting, voice and the remote's
  shortcut keys were **not** re-tested after the debloat at the time of writing; the several-day
  soak required by `CONTRIBUTING.md` is still pending.
- Nothing was uninstalled or cleared; only `pm disable-user` was used.
- Network debugging is temporary access: close it when finished and verify port 5555 is shut.
