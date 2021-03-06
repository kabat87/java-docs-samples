/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package functions;

// [START functions_pubsub_integration_test]

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.Gson;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.vavr.CheckedRunnable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class ExampleIT {
  // Each function must be assigned a unique port to run on.
  // Otherwise, tests can flake when 2+ functions run simultaneously.
  // This is also specified in the `function-maven-plugin` config in `pom.xml`.
  private static final int PORT = 8083;

  // Root URL pointing to the locally hosted function
  // The Functions Framework Maven plugin lets us run a function locally
  private static final String BASE_URL = "http://localhost:" + PORT;

  private static Process emulatorProcess = null;
  private static HttpClient client = HttpClientBuilder.create().build();
  private static final Gson gson = new Gson();

  @BeforeClass
  public static void setUp() throws IOException {
    // Get the sample's base directory (the one containing a pom.xml file)
    String baseDir = System.getProperty("user.dir");

    // Emulate the function locally by running the Functions Framework Maven plugin
    emulatorProcess = new ProcessBuilder()
        .command("bash", "-c", "mvn function:run")
        .directory(new File(baseDir))
        .start();
  }

  @AfterClass
  public static void tearDown() throws IOException {
    // Display the output of the plugin process
    InputStream stdoutStream = emulatorProcess.getInputStream();
    ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
    stdoutBytes.write(stdoutStream.readNBytes(stdoutStream.available()));
    System.err.println(stdoutBytes.toString(StandardCharsets.UTF_8));

    // Terminate the running Functions Framework Maven plugin process (if it's still running)
    if (emulatorProcess.isAlive()) {
      emulatorProcess.destroy();
    }
  }

  @Test
  public void helloPubSub_shouldRunWithFunctionsFramework() throws Throwable {
    String functionUrl = BASE_URL + "/helloPubsub"; // URL to your locally-running function

    // Initialize constants
    String name = UUID.randomUUID().toString();
    String nameBase64 = Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8));

    String jsonStr = gson.toJson(Map.of("data", Map.of("data", nameBase64)));

    HttpPost postRequest =  new HttpPost(URI.create(functionUrl));
    postRequest.setEntity(new StringEntity(jsonStr));

    // The Functions Framework Maven plugin process takes time to start up
    // Use resilience4j to retry the test HTTP request until the plugin responds
    RetryRegistry registry = RetryRegistry.of(RetryConfig.custom()
        .maxAttempts(12)
        .retryExceptions(HttpHostConnectException.class)
        .retryOnResult(u -> {
          // Retry if the Functions Framework process has no stdout content
          // See `retryOnResultPredicate` here: https://resilience4j.readme.io/docs/retry
          try {
            return emulatorProcess.getErrorStream().available() == 0;
          } catch (IOException e) {
            return true;
          }
        })
        .intervalFunction(IntervalFunction.ofExponentialBackoff(200, 2))
        .build());
    Retry retry = registry.retry("my");

    // Perform the request-retry process
    CheckedRunnable retriableFunc = Retry.decorateCheckedRunnable(
        retry, () -> client.execute(postRequest));
    retriableFunc.run();

    // Get Functions Framework plugin process' stdout
    InputStream stdoutStream = emulatorProcess.getErrorStream();
    ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
    stdoutBytes.write(stdoutStream.readNBytes(stdoutStream.available()));

    // Verify desired name value is present
    assertThat(stdoutBytes.toString(StandardCharsets.UTF_8)).contains(
        String.format("Hello %s!", name));
  }
}
// [END functions_pubsub_integration_test]
