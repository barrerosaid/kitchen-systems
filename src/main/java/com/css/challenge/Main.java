package com.css.challenge;

import com.css.challenge.Adapter.OrderAdapter;
import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Harness.SimpleHarness;
import com.css.challenge.client.Action;
import com.css.challenge.client.Client;
import com.css.challenge.client.Order;
import com.css.challenge.client.Problem;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "challenge", showDefaultValues = true)
public class Main implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  static {
    org.apache.log4j.Logger.getRootLogger().setLevel(Level.OFF);
    System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT: %5$s %n");
  }

  @Option(names = "--endpoint", description = "Problem server endpoint")
  String endpoint = "https://api.cloudkitchens.com";

  @Option(names = "--auth", description = "Authentication token (required)")
  String auth = "";

  @Option(names = "--name", description = "Problem name. Leave blank (optional)")
  String name = "";

  @Option(names = "--seed", description = "Problem seed (random if zero)")
  long seed = 0;

  @Option(names = "--rate", description = "Inverse order rate")
  Duration rate = Duration.ofMillis(500);

  @Option(names = "--min", description = "Minimum pickup time")
  Duration min = Duration.ofSeconds(4);

  @Option(names = "--max", description = "Maximum pickup time")
  Duration max = Duration.ofSeconds(8);

  @Override
  public void run() {
    try {
      Client client = new Client(endpoint, auth);
      Problem problem = client.newProblem(name, seed);

      LOGGER.info("=====");
      LOGGER.info("Problem ID: {}", problem.getTestId());
      LOGGER.info("Orders processing: {}", problem.getOrders().size());
      LOGGER.info("=====");

      Kitchen kitchen = new Kitchen();
      LOGGER.info("Kitchen created");

      List<KitchenOrder> orders = problem.getOrders().stream()
              .map(OrderAdapter::toDomain)
              .toList();

      LOGGER.info("Convert {} scaffold code orders to kitchen orders", orders.size());

      SimpleHarness harness = new SimpleHarness(kitchen, rate, min, max);
      LOGGER.info("Running simulation with rate={}ms and pickup between={}-{}", rate.toMillis(), min.toSeconds(), max.toSeconds());

//      var harnessResults = harness.run();
//      List<Action> actions = harnessResults.getActions();

      // ------ Execution harness logic goes here using rate, min and max ----

      List<Action> actions = new ArrayList<>();
      for (Order order : problem.getOrders()) {
        LOGGER.info("Received: {}", order);

        actions.add(new Action(Instant.now(), order.getId(), Action.PLACE, Action.COOLER));
        Thread.sleep(rate.toMillis());
      }

      // ----------------------------------------------------------------------

      LOGGER.info("Submitting {} actions to server: ", actions.size());
      String result = client.solveProblem(problem.getTestId(), rate, min, max, actions);
      LOGGER.info("Result: {}", result);

    } catch (IOException | InterruptedException e) {
      LOGGER.error("Simulation failed: {}", e.getMessage());
    }
  }

  public static void main(String[] args) {
    new CommandLine(new Main()).execute(args);
  }
}
