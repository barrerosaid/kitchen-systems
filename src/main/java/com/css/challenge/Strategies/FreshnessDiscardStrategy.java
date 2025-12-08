package com.css.challenge.Strategies;

import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Storage.StorageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * FreshnessDiscardStrategy discards the order with the
 * lowest freshness ratio first. This strategy ensures that fresher orders are
 * preserved, reducing waste and maintaining order quality.
 */
public class FreshnessDiscardStrategy implements DiscardStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(FreshnessDiscardStrategy.class);

    /**
    * Selects an order to discard from the given storage repository based on the
    * lowest freshness ratio at the given timestamp.
    */
    @Override
    public Optional<KitchenOrder> selectDiscardCandidate(StorageRepository storage, Instant now) {
        List<KitchenOrder> orders = storage.getAllOrders();
        if (orders.isEmpty()) {
            LOGGER.debug("FreshnessStrategy: No orders in storage {}", storage.getName());
            return Optional.empty();
        }

        KitchenOrder leastFresh = null;
        double minFreshness = Double.MAX_VALUE;

        for (KitchenOrder order : orders) {
            double freshness = order.getFreshnessRatio(now);
            LOGGER.debug("FreshnessDiscardStrategy: Evaluating order {} in {} with freshness {:.4f}",
                    order.getId(), storage.getName(), freshness);
            if (freshness < minFreshness) {
                minFreshness = freshness;
                leastFresh = order;
            }
        }

        if (leastFresh != null) {
            LOGGER.info("FreshnessDiscardStrategy: Selected order {} for discard from {} (freshness {:.4f})",
                    leastFresh.getId(), storage.getName(), minFreshness);
            return Optional.of(leastFresh);
        } else {
            LOGGER.debug("FreshnessDiscardStrategy: No candidate to discard in {}", storage.getName());
            return Optional.empty();
        }
    }

    @Override
    public String getName() {
        return "Freshness";
    }
}
