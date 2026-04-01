# @devdebdep/react-native-sunmi-broadcast-scanner

React Native package for listening to Sunmi scanner broadcasts on Android.

This package keeps Sunmi broadcast-scanner support and adds production-focused protections for high-frequency scan flows, plus optional simulation helpers for teams that need to test without a physical Sunmi device.

## Attribution

This repository is an independently maintained fork of the original
`react-native-sunmi-broadcast-scanner` project by Cristiano Cruz.

- Upstream repository: [linvix-sistemas/react-native-sunmi-broadcast-scanner](https://github.com/linvix-sistemas/react-native-sunmi-broadcast-scanner)
- License: MIT

The original license is preserved in [LICENSE](./LICENSE), and fork-specific attribution is noted in [NOTICE](./NOTICE).

## What This Fork Adds

- Android 13+ / 14+ dynamic receiver registration fix using `Context.RECEIVER_EXPORTED`
- Native one-at-a-time scan gate to reduce React Native bridge overload during rapid scan bursts
- `markScanHandled()` so JS can explicitly release the native gate after processing
- Configurable native fail-safe timeout via `setScanGateTimeout(...)`
- Optional simulation mode for local/device testing without a physical Sunmi scanner
- Configurable simulation base URL via `setSimulationConfig(...)`
- Additional native logs to make setup and runtime issues easier to diagnose

## Device Configuration

For real Sunmi scanning to work correctly, configure the device scanner to:

- use Broadcast mode
- disable keyboard/text input output

If keyboard-style input remains enabled, scans may interact with the currently focused UI element instead of behaving like pure broadcast events.

## Installation

```sh
npm install @devdebdep/react-native-sunmi-broadcast-scanner
```

```sh
yarn add @devdebdep/react-native-sunmi-broadcast-scanner
```

## Core Usage

### `onBarcodeRead`

This listener waits for Sunmi broadcast scan events and calls your callback when a barcode is received.

```ts
import ReactNativeSunmiBroadcastScanner from '@devdebdep/react-native-sunmi-broadcast-scanner';

useEffect(() => {
  const cleanup = ReactNativeSunmiBroadcastScanner.onBarcodeRead(ev => {
    console.log(ev.code);
  });

  return () => cleanup.remove();
}, []);
```

## Production Scan Gate

This fork adds a native scan gate so only one scan is forwarded to JS at a time.

Why:

- some scanner/device flows can emit scans very quickly
- forwarding every scan to JS can overload the RN bridge and freeze the UI
- dropping extra scans in native is safer than receiving all of them in JS and ignoring them there

### `markScanHandled()`

Call this after your JS processing is finished for an accepted scan.

This should normally be placed in a `finally` block so the native gate is always released.

```ts
const cleanup = ReactNativeSunmiBroadcastScanner.onBarcodeRead(ev => {
  const run = async () => {
    try {
      await processTicket(ev.code);
    } finally {
      ReactNativeSunmiBroadcastScanner.markScanHandled();
    }
  };

  void run();
});
```

### `setScanGateTimeout(timeoutMs)`

Configures the native fail-safe timeout that force-releases the scan gate if JS never calls `markScanHandled()`.

```ts
ReactNativeSunmiBroadcastScanner.setScanGateTimeout(15000);
```

Why this exists:

- protects against stuck network requests
- protects against unexpected JS errors
- prevents the scanner from staying blocked until the app restarts

## Simulation APIs

Simulation is disabled by default and is intended only for testing/debug scenarios.

### `setSimulationEnabled(enabled)`

Turns simulation mode on or off.

```ts
ReactNativeSunmiBroadcastScanner.setSimulationEnabled(true);
```

### `setSimulationConfig(baseUrl)`

Sets the base URL used when simulation generates mock ticket values.

Example base URL:

```txt
https://tickets.com/scanned-ticket/
```

```ts
ReactNativeSunmiBroadcastScanner.setSimulationConfig(
  'https://tickets.com/scanned-ticket/'
);
```

### `simulateScans(count)`

Generates mock scans and routes them back through the same native broadcast path used by real scanner events.

```ts
ReactNativeSunmiBroadcastScanner.simulateScans(1000);
```

This is useful when:

- you do not have a physical Sunmi device available
- you want to test the one-at-a-time gate behavior
- you want to stress-test scan handling flows on a normal Android device

## Utilities

```ts
import ReactNativeSunmiBroadcastScanner from '@devdebdep/react-native-sunmi-broadcast-scanner';

const model = await ReactNativeSunmiBroadcastScanner.utils.getModel();
const brand = await ReactNativeSunmiBroadcastScanner.utils.getBrand();
const serialNumber =
  await ReactNativeSunmiBroadcastScanner.utils.getSerialNumber();
const versionCode =
  await ReactNativeSunmiBroadcastScanner.utils.getVersionCode();
const versionName =
  await ReactNativeSunmiBroadcastScanner.utils.getVersionName();
const response =
  await ReactNativeSunmiBroadcastScanner.utils.rebootDevice('reason');
```

## Scanner Event Shape

```ts
type ScannerEvent = {
  code: string;
  bytes?: string;
};
```

## Notes

- This package includes Android-native code and is not intended for Expo Go.
- If you are using Expo, you will need a native build setup compatible with custom native modules.
- For Android 13+ / 14+ / 15+ / 16+ targets, the receiver registration fix included in this fork is important.

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and development workflow.

## License

MIT
