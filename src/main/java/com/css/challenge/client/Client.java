package com.css.challenge.client;

import static simplehttp.HeaderList.headers;
import static simplehttp.HeaderPair.header;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simplehttp.*;

/** Client is a client for fetching and solving challenge test problems. */
public class Client {
  private static final Logger LOGGER = LoggerFactory.getLogger(Client.class);

  private final String endpoint;
  private final String auth;
  private final HttpClient client;

  public Client(String endpoint, String auth) {
    this.endpoint = endpoint;
    this.auth = auth;
    this.client = HttpClients.anApacheClient();
  }

  /**
   * newProblem fetches a new test problem from the server. The URL also works in a browser for
   * convenience.
   */
  public Problem newProblem(String name, long seed) throws IOException {
    if (seed == 0) {
      seed = new Random().nextLong();
    }

    URL url =
        new URL(
            endpoint + "/interview/challenge/new?auth=" + auth + "&name=" + name + "&seed=" + seed);
    HttpResponse response = client.get(url);
    if (!response.ok()) {
      throw new IOException(url + ": " + response.getStatusMessage());
    }
    String id = response.getHeaders().get("x-test-id").value();

    LOGGER.info("Fetched new test problem, id={}: {}", id, url);
    return new Problem(id, Order.parse(response.getContent().asString()));
  }

  private static class Options {
    public long rate;
    public long min;
    public long max;

    Options(Duration rate, Duration min, Duration max) {
      this.rate = TimeUnit.MILLISECONDS.toMicros(rate.toMillis());
      this.min = TimeUnit.MILLISECONDS.toMicros(min.toMillis());
      this.max = TimeUnit.MILLISECONDS.toMicros(max.toMillis());
    }
  }

  private static class Solution {
    public Options options;
    public List<Action> actions;

    Solution(Options options, List<Action> actions) {
      this.options = options;
      this.actions = actions;
    }

    String encode() throws IOException {
      return new ObjectMapper().writeValueAsString(this);
    }
  }

  /**
   * solveProblem submits a sequence of actions and parameters as a solution to a test problem.
   * Returns test result.
   */
  public String solveProblem(
      String testId, Duration rate, Duration min, Duration max, List<Action> actions)
      throws IOException {
    Solution solution = new Solution(new Options(rate, min, max), actions);

    URL url = new URL(endpoint + "/interview/challenge/solve?auth=" + auth);
    HttpResponse response =
        client.post(
            url,
            new UnencodedStringMessage(
                solution.encode(),
                headers(header("Content-Type", "application/json"), header("x-test-id", testId))));
    if (!response.ok()) {
      throw new IOException(url + ": " + response.getStatusMessage());
    }
    return response.getContent().asString();
  }
}
