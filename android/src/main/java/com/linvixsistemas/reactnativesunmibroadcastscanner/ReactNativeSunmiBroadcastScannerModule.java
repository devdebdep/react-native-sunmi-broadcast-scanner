package com.linvixsistemas.reactnativesunmibroadcastscanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.sunmi.scanner.IScanInterface;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@ReactModule(name = ReactNativeSunmiBroadcastScannerModule.NAME)
public class ReactNativeSunmiBroadcastScannerModule extends ReactContextBaseJavaModule implements PermissionListener {
  public static final String NAME = "RNSunmiBroadcastScanner";
  private final ReactApplicationContext reactContext;
  private static final String ACTION_DATA_CODE_RECEIVED = "com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED";
  private static final String DATA = "data";
  private static final String SOURCE = "source_byte";
  private static final String JS_EVENT_NAME = "BROADCAST_SCANNER_READ";
  private static final String TICKET_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  private static final long DEFAULT_SCAN_GATE_TIMEOUT_MS = 15000L;

  private static final int REQUEST_SERIAL_NUMBER_PERMISSION_CODE = 1;

  private static final Map<String, Promise> promiseMap = Collections.synchronizedMap(new HashMap<>());
  private static final String PROMISE_SERIAL_NUMBER = "SERAIL_NUMBER";

  private static IScanInterface scanInterface;
  // This gate exists because rapid scanner broadcasts can overwhelm the RN bridge and freeze
  // the UI. We only allow one scan through to JS at a time and reopen the gate after JS
  // finishes the corresponding network/request work.
  private static boolean isScanProcessing = false;
  // Simulation is opt-in so the published package keeps real-device behavior by default.
  private static boolean isSimulationEnabled = false;
  // The base URL is configurable from JS so debug/test scenarios do not require native edits.
  private static String mockTicketBaseUrl = "https://tickets.com/scanned-ticket/";
  // The watchdog reopens the gate if JS never acknowledges completion, which protects against
  // stuck requests or unexpected JS failures leaving scanning permanently blocked.
  private static long scanGateTimeoutMs = DEFAULT_SCAN_GATE_TIMEOUT_MS;
  // Simulation posts onto the main looper because broadcasts/receiver delivery are Android
  // framework work and we want our mock path to resemble the real runtime path.
  private final Handler simulationHandler = new Handler(Looper.getMainLooper());
  // This timeout runnable is the fail-safe for the native gate. If JS misses the normal release
  // path for any reason, native will eventually reopen scanning without requiring an app restart.
  private final Runnable scanGateTimeoutRunnable = new Runnable() {
    @Override
    public void run() {
      if (!isScanProcessing) {
        return;
      }

      isScanProcessing = false;
      Log.w(NAME, "Native scan gate force released after timeout");
    }
  };
  private final Random random = new Random();

  public ReactNativeSunmiBroadcastScannerModule(ReactApplicationContext context) {
    super(context);
    reactContext = context;

    try {
      // Registration failures used to be swallowed here, which made Android 14+ receiver issues
      // very hard to diagnose. Keep these logs so setup problems are visible in logcat.
      Log.d(NAME, "Initializing Sunmi broadcast scanner module");
      RegisterReceiver();
      BindScannerService();
    } catch (Exception e) {
      Log.e(NAME, "Failed to initialize Sunmi broadcast scanner module", e);
    }
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  @ReactMethod
  public void utilsGetSerialNumber(final Promise promise) {
    if (!checkPhoneStatePermission()) {
      // put on promises
      promiseMap.put(PROMISE_SERIAL_NUMBER, promise);

      // request permission
      requestPermission();
    } else {
      promise.resolve(this.getSN());
    }
  }

  @ReactMethod
  public void utilsGetBrand(final Promise promise) {
    promise.resolve(ReactNativeSunmiBroadcastScannerModule.getSystemProperty("ro.product.brand"));
  }

  @ReactMethod
  public void utilsGetModel(final Promise promise) {
    promise.resolve(ReactNativeSunmiBroadcastScannerModule.getSystemProperty("ro.product.model"));
  }

  @ReactMethod
  public void utilsGetVersionName(final Promise promise) {
    promise.resolve(ReactNativeSunmiBroadcastScannerModule.getSystemProperty("ro.version.sunmi_versionname"));
  }

  @ReactMethod
  public void utilsGetVersionCode(final Promise promise) {
    promise.resolve(ReactNativeSunmiBroadcastScannerModule.getSystemProperty("ro.version.sunmi_versioncode"));
  }

  @ReactMethod
  public void utilsRebootDevice(String reason, final Promise promise) {
    PowerManager powerManager = (PowerManager) getReactApplicationContext().getSystemService(Context.POWER_SERVICE);

    // força o reinício
    powerManager.reboot(reason);

    // resolve a promessa
    promise.resolve(true);
  }

  @ReactMethod
  public void markScanHandled() {
    // JS calls this after a scan request finishes so native can accept the next broadcast.
    cancelScanGateWatchdog();
    isScanProcessing = false;
    Log.d(NAME, "Native scan gate released");
  }

  @ReactMethod
  public void setScanGateTimeout(double timeoutMs) {
    long normalizedTimeoutMs = (long) timeoutMs;

    // Timeout is configurable from JS because different products may want different tradeoffs
    // between recovery speed and allowing slower network/request flows to finish normally.
    if (normalizedTimeoutMs <= 0) {
      Log.d(NAME, "Ignoring invalid scan gate timeout: " + normalizedTimeoutMs);
      return;
    }

    scanGateTimeoutMs = normalizedTimeoutMs;
    Log.d(NAME, "Scan gate timeout set to " + scanGateTimeoutMs + "ms");
  }

  @ReactMethod
  public void setSimulationEnabled(boolean enabled) {
    // Debug builds can turn simulation on, while production can leave it disabled and use only
    // real Sunmi scanner broadcasts.
    isSimulationEnabled = enabled;
    Log.d(NAME, "Simulation " + (enabled ? "enabled" : "disabled"));
  }

  @ReactMethod
  public void setSimulationConfig(String baseUrl) {
    // We normalize the base URL once in native so every generated ticket follows the same shape.
    if (baseUrl == null || baseUrl.trim().isEmpty()) {
      Log.d(NAME, "Ignoring empty simulation base URL");
      return;
    }

    mockTicketBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    Log.d(NAME, "Simulation base URL set to " + mockTicketBaseUrl);
  }

  @ReactMethod
  public void simulateScans(int count) {
    if (!isSimulationEnabled) {
      Log.d(NAME, "Simulation request ignored because simulation is disabled");
      return;
    }

    // Simulated scans are intentionally routed back through the same broadcast receiver path used
    // by real hardware scans. That keeps mock testing honest and exercises the same gate/logging.
    Log.d(NAME, "Starting native scan simulation: " + count);

    for (int index = 0; index < count; index++) {
      final String mockTicketUrl = mockTicketBaseUrl + generateTicketCode(10);

      simulationHandler.post(() -> {
        Intent intent = new Intent(ACTION_DATA_CODE_RECEIVED);
        intent.putExtra(DATA, mockTicketUrl);
        intent.putExtra(SOURCE, mockTicketUrl.getBytes());
        reactContext.sendBroadcast(intent);
      });
    }
  }

  @SuppressLint("HardwareIds")
  protected String getSN() {
    String serial = null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      serial = ReactNativeSunmiBroadcastScannerModule.getSystemProperty("ro.sunmi.serial");
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      serial = Build.getSerial();
    } else {
      serial = ReactNativeSunmiBroadcastScannerModule.getSystemProperty("ro.serialno");
    }

    return serial;
  }

  @SuppressLint("PrivateApi")
  public static String getSystemProperty(String key) {
    try {
      Class<?> c = Class.forName("android.os.SystemProperties");
      Method get = c.getMethod("get", String.class);
      return (String) get.invoke(c, key);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public Boolean checkPhoneStatePermission() {
    return ContextCompat.checkSelfPermission(reactContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
  }

  @ReactMethod
  @SuppressLint("MissingPermission")
  public void requestPermission() {
    Log.i(NAME, "Requesting permission to access bluetooth device");

    // check if app has permission to interact with bluetooth device
    boolean hasPermission = checkPhoneStatePermission();

    // if has permission, stop script
    if (hasPermission) {
      return;
    }

    // get de current activity
    PermissionAwareActivity activity = (PermissionAwareActivity) reactContext.getCurrentActivity();

    if (activity == null) {
      Log.e(NAME, "Activity not found");
      return;
    }

    // request the permission
    activity.requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_SERIAL_NUMBER_PERMISSION_CODE, this);
  }


  private final BroadcastReceiver receiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      try {
        if (isScanProcessing) {
          // Dropping here is deliberate: once a scan is already being processed, forwarding more
          // events to JS just creates bridge pressure without any product value.
          return;
        }

        String code = intent.getStringExtra(DATA);
        byte[] arr = intent.getByteArrayExtra(SOURCE);

        // parameters that will be sent to react native
        WritableMap params = new WritableNativeMap();

        if (code != null && !code.isEmpty()) {
          // Close the gate before emitting to JS so back-to-back broadcasts from the scanner do
          // not queue up while the app is still handling the current ticket.
          isScanProcessing = true;
          startScanGateWatchdog();
          Log.d(NAME, "Forwarding scan to JS");
          Log.d(NAME, code);

          // monta o params
          params.putString("code", code);
          params.putString("bytes", arr != null ? new String(arr) : null);

          // send event to react native
          reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(JS_EVENT_NAME, params);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  };

  private static final ServiceConnection conn = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
      Log.d(NAME, "Broadcast scanner service connected");
      scanInterface = IScanInterface.Stub.asInterface(iBinder);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      Log.d(NAME, "Broadcast scanner service disconnected");
      scanInterface = null;
    }
  };

  public void BindScannerService() {
    Intent intent = new Intent();
    intent.setPackage("com.sunmi.scanner");
    intent.setAction("com.sunmi.scanner.IScanInterface");

    reactContext.bindService(intent, conn, Service.BIND_AUTO_CREATE);
  }

  private void RegisterReceiver() {
    Log.d(NAME, "Registering broadcast receiver");
    IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_DATA_CODE_RECEIVED);

    // Android 13+ requires dynamic receivers to declare their export behavior. Without this,
    // newer targets can fail registration and the scanner silently stops delivering events.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      reactContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
      Log.d(NAME, "Broadcast receiver registered with RECEIVER_EXPORTED");
    } else {
      reactContext.registerReceiver(receiver, filter);
      Log.d(NAME, "Broadcast receiver registered");
    }
  }

  private String generateTicketCode(int length) {
    StringBuilder builder = new StringBuilder(length);

    // We keep the generator simple and deterministic in shape so simulated values match the real
    // ticket format expectations used by downstream parsing and API calls.
    for (int index = 0; index < length; index++) {
      int randomIndex = random.nextInt(TICKET_CODE_ALPHABET.length());
      builder.append(TICKET_CODE_ALPHABET.charAt(randomIndex));
    }

    return builder.toString();
  }

  private void startScanGateWatchdog() {
    // Always replace any previous watchdog so only the active scan owns the timeout window.
    simulationHandler.removeCallbacks(scanGateTimeoutRunnable);
    simulationHandler.postDelayed(scanGateTimeoutRunnable, scanGateTimeoutMs);
    Log.d(NAME, "Native scan gate watchdog started for " + scanGateTimeoutMs + "ms");
  }

  private void cancelScanGateWatchdog() {
    // Cancelling on successful JS completion prevents the fail-safe from reopening the gate after
    // the scan has already been handled in the intended way.
    simulationHandler.removeCallbacks(scanGateTimeoutRunnable);
    Log.d(NAME, "Native scan gate watchdog cancelled");
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    Log.i(NAME, "Permission request result :" + requestCode + Arrays.toString(permissions) + Arrays.toString(grantResults));

    if (requestCode == REQUEST_SERIAL_NUMBER_PERMISSION_CODE) {
      Promise promise = promiseMap.remove(PROMISE_SERIAL_NUMBER);

      boolean hasPermission = checkPhoneStatePermission();

      if (hasPermission) {
        promise.resolve(this.getSN());
      } else {
        promise.resolve(null);
      }
    }
    return true;
  }
}
