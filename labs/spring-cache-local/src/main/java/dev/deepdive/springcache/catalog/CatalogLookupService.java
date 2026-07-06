package dev.deepdive.springcache.catalog;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CatalogLookupService {

    private final AtomicInteger loadCount = new AtomicInteger();

    @Cacheable(cacheNames = "catalogItems", key = "#itemId")
    public CatalogItem findById(long itemId) {
        loadCount.incrementAndGet();
        return new CatalogItem(itemId, "item-" + itemId);
    }

    public int loadCount() {
        return loadCount.get();
    }
}
