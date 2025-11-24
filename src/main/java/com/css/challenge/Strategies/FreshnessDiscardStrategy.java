package com.css.challenge.Strategies;

import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Storage.StorageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class FreshnessDiscardStrategy implements DiscardStrategy
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FreshnessDiscardStrategy.class);
    private static final String strategyName = "Freshness";

    // We are checking the selected storage on freshness
    public Optional<KitchenOrder> selectDiscardCandidate(StorageRepository shelf){
        Instant now = Instant.now();
        List<KitchenOrder> allOrders = shelf.getAllOrders();

        // compare which is the freshest one from all the kitchen order
        return allOrders.stream().min((o1, o2) ->{
            double ratio1 = o1.getFreshnessRatio(now);
            double ratio2 = o2.getFreshnessRatio(now);

            int freshnessRatioCompare = Double.compare(ratio1, ratio2);
            if(freshnessRatioCompare != 0){
                return freshnessRatioCompare;
            }

            // if the same freshness ratio (both going to expire at the same rate) so compare price
            return o1.getPrice().compareTo(o2.getPrice());
        });
    }

    @Override
    public String getName(){
        return strategyName;
    }
}
