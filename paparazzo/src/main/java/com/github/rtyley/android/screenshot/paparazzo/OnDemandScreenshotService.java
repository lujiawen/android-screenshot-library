package com.github.rtyley.android.screenshot.paparazzo;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.lang.Thread.sleep;
import static java.util.Arrays.asList;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.RawImage;
import com.github.rtyley.android.screenshot.paparazzo.processors.ScreenshotProcessor;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OnDemandScreenshotService {
  private final IDevice device;
  private final List<ScreenshotProcessor> processors;
  private final ScreenshotRequestListener logListenerCommandShell;

  public OnDemandScreenshotService(IDevice device, ScreenshotProcessor... processors) {
    this.device = device;
    this.processors = asList(processors);
    this.logListenerCommandShell = new ScreenshotRequestListener();
  }

  /**
   * Start receiving and acting on paparazzo requests from the device.
   */
  public void start() {
    Thread thread = new Thread(new LogCatCommandExecutor());
    thread.setName(getClass().getSimpleName() + " logcat for " +
        device.getSerialNumber());
    thread.start();

    try {
      sleep(500); // ignore old output that logcat feeds us
    } catch (InterruptedException e) {}

    logListenerCommandShell.activate();
  }

  /**
   * Stop receiving and acting on paparazzo requests.
   * This class is not re-usable after finish() is called.
   */
  public void finish() {
    if (logListenerCommandShell != null) {
      logListenerCommandShell.cancelled = true;
    }
    for (ScreenshotProcessor screenshotProcessor : processors) {
      screenshotProcessor.finish();
    }
  }

  private class LogCatCommandExecutor implements Runnable {
    /**
     * Reads only the 'main' log buffer, specifically to exclude the 'system'
     * log - the system log can be extremely slow to cat on some devices, and
     * it doesn't even have the 'screenshot_request' tag we're interested in.
     */
    private final static String LOGCAT_COMMAND =
        "logcat -v raw -b main screenshot_request:D *:S";

    @Override
    public void run() {
      try {
        device.executeShellCommand(LOGCAT_COMMAND, logListenerCommandShell, 0);
      } catch (Exception e) {}
    }
  }

  private class ScreenshotRequestListener extends MultiLineReceiver {
    private boolean activated = false, cancelled = false;

    public ScreenshotRequestListener() {
      setTrimLine(false);
    }

    public void activate() {
      activated = true;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void processNewLines(String[] lines) {
      if (!activated || cancelled) {
        return;
      }

      String mostRecentLogLine = lines[lines.length - 1];

      // whatever the earlier requests corresponded to, that stuff is gone
      takeScreenshotFor(mostRecentLogLine);
    }
  }

  private void takeScreenshotFor(String logLine) {
    RawImage rawImage;
    try {
      rawImage = device.getScreenshot();
    } catch (Exception e) {
      return;
    }

    if (rawImage == null) {
      return;
    }

    Map<String, String> keyValueMap = keyValueMapFor(logLine);
    BufferedImage image = bufferedImageFrom(rawImage);

    for (ScreenshotProcessor screenshotProcessor : processors) {
      screenshotProcessor.process(image, keyValueMap);
    }
  }

  /**
   * Parse the logline into key-value pairs. The logline format is
   * comma-separarated key-value pairs surrounded by curly braces, ie:
   *
   * {foo=bar,name=ARandomName}
   */
  static Map<String, String> keyValueMapFor(String logLine) {
    Map<String, String> keyValueMap = new HashMap<String, String>();
    if (logLine.startsWith("{") && logLine.endsWith("}")) {
      for (String keyValuePair : logLine.substring(1, logLine.length() - 1)
          .split(",")) {
        int separatorIndex = keyValuePair.indexOf("=");
        if (separatorIndex > 0) {
          keyValueMap.put(keyValuePair.substring(0, separatorIndex),
              keyValuePair.substring(separatorIndex + 1));
        }
      }
    }
    return keyValueMap;
  }

  private static BufferedImage bufferedImageFrom(RawImage rawImage) {
    BufferedImage image = new BufferedImage(rawImage.width, rawImage.height,
        TYPE_INT_ARGB);

    int index = 0;
    int bytesPerPixel = rawImage.bpp >> 3;
    for (int y = 0; y < rawImage.height; y++) {
      for (int x = 0; x < rawImage.width; x++) {
        image.setRGB(x, y, rawImage.getARGB(index) | 0xff000000);
        index += bytesPerPixel;
      }
    }
    return image;
  }
}
