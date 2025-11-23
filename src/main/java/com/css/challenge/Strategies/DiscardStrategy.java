package com.css.challenge.Strategies;

import com.css.challenge.Business.KitchenOrder;
import com.css.challenge.Storage.StorageRepository;

import javax.swing.text.html.Option;
import java.util.Optional;

/**
 * Select a way to discard orders
 */
public interface DiscardStrategy {

    public Optional<KitchenOrder> selectDiscardCandidate(StorageRepository shelf);

    public String getName();
}
