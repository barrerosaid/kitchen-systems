# README

Author: `Said Barrero`

## How to run

The `Dockerfile` defines a self-contained Java/Gradle reference environment.
Build and run the program using [Docker](https://docs.docker.com/get-started/get-docker/):
```
$ docker build -t challenge .
$ docker run --rm -it challenge --auth=<token>
```
Feel free to modify the `Dockerfile` as you see fit.

If java `21` or later is installed locally, run the program directly for convenience:
```
$ ./gradlew run --args="--auth=<token>"
```

## Discard criteria

The discard criteria is written in the class: FreshnessDiscardStrategy

The FreshnessDiscardStrategy is used to select which order to discard when a storage location (like the shelf) is full. The strategy ensures that orders with the lowest remaining freshness are discarded first to minimize waste and maximize overall order quality.

How It Works
Fetch All Orders: The strategy retrieves all orders currently in the given storage location.
Evaluate Freshness: Each order’s freshness is calculated as a ratio between its age and its total freshness duration:

- A freshness ratio of 1.0 means the order is completely fresh.
- A freshness ratio of 0.0 means the order has expired.

The criteria will select the least fresh order which is  the order with the lowest freshness ratio is chosen as the discard candidate.

The selected order is then removed from the storage to free up space for new orders.

Key Points
Operates on a per-storage basis: shelf, cooler, or heater.
Always prioritizes discarding the least fresh order first.
Returns Optional.empty() if the storage has no orders.
The strategy will select the single least fresh order at any given time.

Rationale:
By discarding the least fresh items first, fresher orders are preserved, reducing the risk of discarding orders that could still be served. This format allows for customers are more likely to receive fresher items, improving overall satisfaction.
This strategy is straightforward to implement and ensures consistent behavior under the different storage options and also gives a pattern to create new strategies for discarding.

# Architecture Overview
└───src
    └───main
        └───java
            └───com
                └───css
                    └───challenge
                        ├───Adapter
                        ├───Business
                        ├───client
                        ├───Harness
                        ├───Storage
                        └───Strategies

# Design Overview
This kitchen system is designed with modularity and maintainability in mind. The Strategy pattern is employed for the discard logic, enabling different algorithms for selecting which orders to discard without modifying the core Kitchen implementation. 

Each storage type (heater, cooler, and shelf storage) is encapsulated behind a repository-style abstraction, providing a unified interface for adding, removing, and finding/querying orders. 

Thread safety is ensured using a Read-Write Lock, allowing concurrent placements and pickups while maintaining consistent state.

One of the primary challenges in this implementation was coordinating timing across multiple intervals. Kitchen orders have varying freshness durations, placement rates, and pickup delays, which can interact in complex ways. 

My challenge was ensuring that all timestamps remain monotonic, preventing premature pickups or late discards, and maintaining alignment with the discard strategy required careful design and iterative tuning of the simulation logic.

# Sample Output
This simulation output can be seen in sample_output.txt for 4-30ms for 500ms