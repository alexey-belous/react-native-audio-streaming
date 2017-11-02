package com.audioStreaming;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import javax.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class ReactNativeAudioStreamingModule extends ReactContextBaseJavaModule
    implements ServiceConnection {

  public static final String SHOULD_SHOW_NOTIFICATION = "showInAndroidNotifications";
  private ReactApplicationContext context;

  private Class<?> clsActivity;
  private static Signal signal;
  private Intent bindIntent;
  private String streamingURL;
  private boolean shouldShowNotification;

  public ReactNativeAudioStreamingModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.context = reactContext;
  }

  public ReactApplicationContext getReactApplicationContextModule() {
    return this.context;
  }

  public Class<?> getClassActivity() {
    if (this.clsActivity == null) {
      this.clsActivity = getCurrentActivity().getClass();
    }
    return this.clsActivity;
  }

  public void stopOncall() {
    this.signal.stop();
  }

  public Signal getSignal() {
    return signal;
  }

  public void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
    this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }

  @Override public String getName() {
    return "ReactNativeAudioStreaming";
  }

  @Override public void initialize() {
    super.initialize();

    try {
      bindIntent = new Intent(this.context, Signal.class);
      this.context.bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
    } catch (Exception e) {
      Log.e("ERROR", e.getMessage());
    }
  }

  @Override public void onServiceConnected(ComponentName className, IBinder service) {
    signal = ((Signal.RadioBinder) service).getService();
    signal.setData(this.context, this);
    WritableMap params = Arguments.createMap();
    sendEvent(this.getReactApplicationContextModule(), "streamingOpen", params);
  }

  @Override public void onServiceDisconnected(ComponentName className) {
    signal = null;
  }

  private String UnwrapUrl(String url, int attempts) throws IOException {
    HttpURLConnection con = (HttpURLConnection)new URL(url).openConnection();
    con.connect();
    int code = con.getResponseCode();
    System.out.println(url);
    System.out.println(attempts);
    if(code == 301 || code == 302){
      String location = con.getHeaderField("Location");
      return UnwrapUrl(location, attempts + 1);
    }
    if(attempts > 10)
    {
      return url;
    }
    return url;
  }

  @ReactMethod public void play(String streamingURL, ReadableMap options) throws IOException {
    this.streamingURL = UnwrapUrl(streamingURL, 0);
    System.out.println("!!!");
    System.out.println(this.streamingURL);
    this.shouldShowNotification =
        options.hasKey(SHOULD_SHOW_NOTIFICATION) && options.getBoolean(SHOULD_SHOW_NOTIFICATION);
    signal.setURLStreaming(this.streamingURL); // URL of MP3 or AAC stream
    playInternal();
  }

  private void playInternal() {
    signal.play();
    if (shouldShowNotification) {
      signal.showNotification();
    }
  }

  @ReactMethod public void stop() {
    signal.stop();
  }

  @ReactMethod public void pause() {
    // Not implemented on aac
    this.stop();
  }

  @ReactMethod public void resume() {
    // Not implemented on aac
    playInternal();
  }

  @ReactMethod public void destroyNotification() {
    signal.exitNotification();
  }

  @ReactMethod public void getStatus(Callback callback) {
    WritableMap state = Arguments.createMap();
    state.putString("status", signal != null && signal.isPlaying ? Mode.PLAYING : Mode.STOPPED);
    callback.invoke(null, state);
  }
}
