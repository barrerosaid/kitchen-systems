package com.css.challenge;

import com.css.challenge.Adapter.OrderAdapter;
import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Harness.SimpleHarness;
import com.css.challenge.Harness.SimpleHarnessResult;
import com.css.challenge.Storage.CoolerStorage;
import com.css.challenge.Storage.HeaterStorage;
import com.css.challenge.Storage.ShelfStorage;
import com.css.challenge.Strategies.FreshnessDiscardStrategy;
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
  Duration max = Duration.ofSeconds(20);

  @Override
  public void run() {
    try {
      // --- Connect to API ---
      Client client = new Client(endpoint, auth);
      Problem problem = client.newProblem(name, seed);

      LOGGER.info("=====");
      LOGGER.info("Problem ID: {}", problem.getTestId());
      LOGGER.info("Incoming Orders: {}", problem.getOrders().size());
      LOGGER.info("=====");

      // --- Create storage repositories ---
      HeaterStorage heaterStorage = new HeaterStorage();
      CoolerStorage coolerStorage = new CoolerStorage();
      ShelfStorage shelfStorage  = new ShelfStorage();

      // --- Discard strategy ---
      FreshnessDiscardStrategy discardStrategy = new FreshnessDiscardStrategy();

      // --- Kitchen instance ---
      Kitchen kitchen = new Kitchen(heaterStorage, coolerStorage, shelfStorage, discardStrategy);
      LOGGER.info("Kitchen initialized");

      // --- Convert problem orders → domain orders ---
      Instant simulationNow = Instant.now(); // or problem.getStartTime() if available
      List<KitchenOrder> orders = problem.getOrders().stream()
              .map(o -> OrderAdapter.toDomain(o, simulationNow))
              .toList();
      //KitchenOrder order = OrderAdapter.toDomain(clientOrder, simulationClock.now());

      LOGGER.info("Converted {} scaffold orders to domain orders", orders.size());

      // --- Harness ---
      SimpleHarness harness =
              new SimpleHarness(kitchen, rate, min, max);

      LOGGER.info(
              "Starting simulation: rate={}ms, pickup={}-{} sec",
              rate.toMillis(), min.toSeconds(), max.toSeconds()
      );

      // Run the local simulation
      SimpleHarnessResult result = harness.run(orders);

      List<Action> actions = result.getActions();
      LOGGER.info("Simulation produced {} actions", actions.size());

      // --- Submit actions to server ---
      LOGGER.info("Submitting actions...");
      String response = client.solveProblem(
              problem.getTestId(),
              rate,
              min,
              max,
              actions
      );

      LOGGER.info("Server Response: {}", response);

    } catch (IOException e) {
      LOGGER.error("Simulation failed: {}", e.getMessage());
      System.exit(1);
    }
  }


  public static void main(String[] args) {
    new CommandLine(new Main()).execute(args);
  }
}
