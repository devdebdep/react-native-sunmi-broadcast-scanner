import * as React from 'react';

import { StyleSheet, View, Text } from 'react-native';

import ReactNativeSunmiBroadcastScanner from '@devdebdep/react-native-sunmi-broadcast-scanner';

export default function App() {
  const [result, setResult] = React.useState<string>('');

  const loadDeviceData = async () => {
    try {
      /**
       * Get model of device
       */
      const model = await ReactNativeSunmiBroadcastScanner.utils.getModel();

      /**
       * Get brand of device
       */
      const brand = await ReactNativeSunmiBroadcastScanner.utils.getBrand();

      /**
       * Get serial number of device
       */
      const serialNumber =
        await ReactNativeSunmiBroadcastScanner.utils.getSerialNumber();

      /**
       * Get version code of device
       */
      const versionCode =
        await ReactNativeSunmiBroadcastScanner.utils.getVersionCode();

      /**
       * Get version name of device
       */
      const versionName =
        await ReactNativeSunmiBroadcastScanner.utils.getVersionName();

      console.log('model', model);
      console.log('brand', brand);
      console.log('serialNumber', serialNumber);
      console.log('versionCode', versionCode);
      console.log('versionName', versionName);
    } catch (error: Error | any) {
      console.error(error.message);
    }
  };

  React.useEffect(() => {
    loadDeviceData();

    /**
     * On devices that have a barcode reader or support via external USB scanners.
     * For code reading to work, you need to configure the device to broadcast data and disable TextInput for text output.
     *
     * L2s/L2ks also works here with barcode scanning.
     *
     * It is important to call cleanup to remove the listener from the function when you want to stop receiving the read barcode.
     */
    const cleanup = ReactNativeSunmiBroadcastScanner.onBarcodeRead((ev) => {
      console.log(ev);
      setResult(ev.code);
    });

    return () => cleanup.remove();
  }, []);

  return (
    <View style={styles.container}>
      {result === '' && <Text>Read barcode to show here</Text>}
      {result !== '' && <Text>Barcode: {result}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
  },
});
