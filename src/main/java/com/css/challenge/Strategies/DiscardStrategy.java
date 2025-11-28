package com.css.challenge.Strategies;

import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Storage.StorageRepository;

import javax.swing.text.html.Option;
import java.time.Instant;
import java.util.Optional;

/**
 * Select a way to discard orders
 */
public interface DiscardStrategy {

    Optional<KitchenOrder> selectDiscardCandidate(StorageRepository storage, Instant now);

    public String getName();
}
