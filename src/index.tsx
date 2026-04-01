/* eslint-disable no-new */
import { NativeModules, DeviceEventEmitter } from 'react-native';

import type { ScannerEvent } from './types';

const LINKING_ERROR =
  `The package '@devdebdep/react-native-sunmi-broadcast-scanner' doesn't seem to be linked. Make sure: \n\n` +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

if (!NativeModules.RNSunmiBroadcastScanner) {
  new Proxy(
    {},
    {
      get() {
        throw new Error(LINKING_ERROR);
      },
    }
  );
}

const { RNSunmiBroadcastScanner } = NativeModules;

const onBarcodeRead = (callback: (ev: ScannerEvent) => void) => {
  return DeviceEventEmitter.addListener('BROADCAST_SCANNER_READ', callback);
};

const markScanHandled = (): void => {
  RNSunmiBroadcastScanner.markScanHandled();
};

const setScanGateTimeout = (timeoutMs: number): void => {
  RNSunmiBroadcastScanner.setScanGateTimeout(timeoutMs);
};

const setSimulationEnabled = (enabled: boolean): void => {
  RNSunmiBroadcastScanner.setSimulationEnabled(enabled);
};

const setSimulationConfig = (baseUrl: string): void => {
  RNSunmiBroadcastScanner.setSimulationConfig(baseUrl);
};

const simulateScans = (count: number): void => {
  RNSunmiBroadcastScanner.simulateScans(count);
};

const getBrand = async (): Promise<string | null> => {
  return await RNSunmiBroadcastScanner.utilsGetBrand();
};

const getSerialNumber = async (): Promise<string | null> => {
  return await RNSunmiBroadcastScanner.utilsGetSerialNumber();
};

const getModel = async (): Promise<string | null> => {
  return await RNSunmiBroadcastScanner.utilsGetModel();
};

const getVersionCode = async (): Promise<string | null> => {
  return await RNSunmiBroadcastScanner.utilsGetVersionCode();
};

const getVersionName = async (): Promise<string | null> => {
  return await RNSunmiBroadcastScanner.utilsGetVersionName();
};

const rebootDevice = async (reason: string): Promise<boolean> => {
  return await RNSunmiBroadcastScanner.utilsRebootDevice(reason);
};

const ReactNativeSunmiBroadcastScanner = {
  onBarcodeRead,
  markScanHandled,
  setScanGateTimeout,
  setSimulationEnabled,
  setSimulationConfig,
  simulateScans,

  utils: {
    getModel,
    getBrand,
    getSerialNumber,
    getVersionCode,
    getVersionName,
    rebootDevice,
  },
};

export default ReactNativeSunmiBroadcastScanner;
