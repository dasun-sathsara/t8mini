# Tronsmart T8 Mini: protocol notes

## Transport

BLE, service `011ff5da-0000-1000-8000-00805f9b34fb`

| role | UUID | value handle |
| --- | --- | --- |
| write without response | `00007777-0000-1000-8000-00805f9b34fb` | 0x0009 |
| notify | `00008888-0000-1000-8000-00805f9b34fb` | 0x000b |

Voice/audio traffic is ordinary A2DP on the classic link. There is also a JieLi
(杰理) OTA service `e49a25f8-...` and the JieLi SPP records `0cf12d31-...` in SDP.
Both are unrelated to control.

## Frame format

Every frame is

```
08 EE 00 00 00 01 <cmd> <len lo> <len hi> <payload...> <sum>
```

- `08 EE` request magic (`09 FF` in replies), `00 00` constant, `00` request
  (`01` reply), `01` constant.
- `len` is the total frame length including header and checksum, little endian.
- `sum` is the low byte of the sum of every preceding byte.

Example, LED on: `08 ee 00 00 00 01 98 0b 00 01 9b`.

## Commands

| feature | cmd | payload | notes |
| --- | --- | --- | --- |
| EQ preset | `0x82` | `[preset,0,0,0,0,0]` | 0 Default, 1 SoundPulse, 2 Deep Bass, 3 Classical, 4 Rock |
| EQ custom | `0x82` | `[0xfe,b1,b2,b3,b4,b5]` | band levels, 0 to 255, higher is louder |
| LED on/off | `0x98` | `[0/1]` | |
| LED mode | `0x8a` | `[1/2/3]` | 1 Fashion Party, 2 Deep Breath, 3 Carousel, only acts while LED is on |
| Stereo pair | `0x8b` | `[0/1]` | reply `0x8c` `[0/1]` |
| Input | `0x90` | `[0/1/2]` | 0 Bluetooth observed; 1 AUX, 2 TF card guessed, the vendor app blocks switching while connected |
| LED status poll | `0x92` | `[]` | sent after enabling the light |
| Voice prompt | `0x9b` | `[0/1]` | |
| Auto power-off | `0x96` | `[min lo, min hi]` | uint16 LE minutes, 0 = off; acked by `0x97` |
| Status queries | `0x02`, `0x08`, `0x09`, `0x8f`, `0x97` | `[]` | sent on connect, see replies below |

Most set commands are acknowledged with a reply whose command byte is the request
plus one and whose payload echoes the request, for example

```
-> 08 ee 00 00 00 01 98 0b 00 01 9b
<- 09 ff 00 00 01 01 99 0b 00 01 af
```

No bonding is needed. A plain unbonded BLE central (macOS CoreBluetooth) writes
these frames and gets the acknowledgements. Android's Web Bluetooth path still
raises a pairing prompt because the speaker sends an SMP security request, and on
the Android 16 beta build used here the prompt's Pair button stays disabled.

Replies seen on connect:

```
0x02 -> [06]
0x8f -> [04 01 00]
0x97 -> [00 00 00 00 00]
0x08 -> [00]
0x09 -> [10]
```

The battery percentage in the vendor app does not come from these; it most likely
comes from the classic HFP battery indicator, so it is not reachable over BLE.

## Notifications the speaker pushes

Everything below was observed on the real speaker while driving it from the
Android app, with the raw frames pinned in `RepliesTest`.

| cmd | payload | meaning |
| --- | --- | --- |
| `0x04` | `[preset,0,0,0,0,0]` | EQ preset applied, echoes the preset index |
| `0x04` | `[0xfe,b1..b5]` | custom curve applied, echoes the five bands |
| `0x92` | `[mode]` | LED *animation* only; it still answers `02` while the light is off |
| `0x97` | `[set lo,set hi,rem lo,rem hi,sec]` | timer state, pushed every few seconds while armed |
| `0x99` | `[on]` | LED on/off, both as an acknowledgement and as a status query |
| `0x9c` | `[on]` | voice prompt state |
| `0x8c` | `[on]` | stereo mode |

`0x92` looks like a state report but is not one: after switching the light off the
speaker still replies `0x92 [02]`, so a UI that derives on/off from it flicks the
light back on every poll. Ask `0x99` with an empty payload for the real state;
the speaker answers `0x99 [0]` or `0x99 [1]`.

The timer notification is the useful one: with 30 minutes armed the speaker sent
`97 0f 00 1e 00 1d 00 38 23`, i.e. set = 30 min, remaining = 29 min, 56 s. It
keeps counting down on its own, so the app mirrors `remaining` instead of running
a local clock.

The EQ is the odd one out: it answers `0x04` rather than the usual command + 1,
so a naive acknowledgement check would treat it as unrelated traffic.

## Connecting from Android

The address that works over BLE is **not** always the bonded one:

- Bonded entry (from the classic pairing): a classic Bluetooth address, transport `DUAL`,
  but the bond carries no LE key (`LE:N/A`) and a direct LE connect to it never
  completes: the controller gives up after 30 s with status 147.
- The speaker actually advertised as a different address, a random static address
  that is not in the bond table.

Consequences:

1. Scan first and connect to the advertising device, not to the bonded address.
2. The speaker serves one BLE central. While another host (for example a Mac)
   held the classic link, it did not advertise at all: a 12 s scan found nothing,
   and the advertisement appeared within 2 s of disconnecting that host.
3. Bonding is not needed. Writes are accepted on an unbonded LE link, so the app
   never calls `createBond`.

## How this was obtained

1. `jadx` on `com.geekbuy.tronsmart` showed the Kotlin SDK is a dumb pipe:
   `ClassicCommand(data: ByteArray, characteristic: String)` arrives over Pigeon,
   so the frames are built in Dart.
2. The bugreport HCI log (`btsnooz_hci.log`) is truncated to ~15 bytes per packet,
   enough to see `08 ee 00` but not the payload.
3. `blutter` on `libapp.so` named the classes
   (`bt_protocol/t8_mini.dart`, `sendCmd({commandId, payload})`) but not the bodies.
4. Final step: repackaged the APK with `apktool`, added a two-line
   `Log.d("T8SPY_TX", ...)` to `ClassicCommand.getData()` (and the RX twin),
   re-signed with `uber-apk-signer`, installed, and drove the UI while reading
   `logcat`. That produced every frame above verbatim.

## Repo layout

- `web/index.html`: Web Bluetooth test app (works over `http://localhost`).
- `NOTES.md`: this file.
- `apk/`: original vendor APKs pulled from the phone.
- `patched/`: rebuilt, logging build of the same app.
- `jadx_out/`, `blutter_out/`: decompiled Kotlin and Dart.
- `btsnoop.py`, `rawacl.py`, `l2cap.py`, `send.py`: capture and replay helpers.
