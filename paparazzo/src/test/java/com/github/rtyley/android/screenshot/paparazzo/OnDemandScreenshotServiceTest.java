package com.github.rtyley.android.screenshot.paparazzo;

import com.madgag.hamcrest.IsMap;
import org.junit.Test;

import static com.github.rtyley.android.screenshot.paparazzo.OnDemandScreenshotService.keyValueMapFor;
import static org.hamcrest.MatcherAssert.assertThat;

public class OnDemandScreenshotServiceTest {
  @Test
  public void shouldParseLogLineWithKeyValue() {
    assertThat(keyValueMapFor("{name=ConfigureMorseActivity-SOS}"),
        IsMap.containingOnly("name", "ConfigureMorseActivity-SOS"));
  }

  @Test
  public void shouldParseLogLineWithTwoKeyValues() {
    assertThat(keyValueMapFor("{foo=bar,alpha=beta}"),
        IsMap.containingOnly("foo", "bar").and("alpha", "beta"));
  }
}
