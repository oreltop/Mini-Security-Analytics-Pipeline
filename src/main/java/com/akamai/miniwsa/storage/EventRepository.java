package com.akamai.miniwsa.storage;

import com.akamai.miniwsa.domain.SecurityEvent;
import java.util.List;

/**
 * The storage seam. The application depends on this interface, not on any
 * concrete engine, so the backing store (in-memory today; a database or search
 * engine at real scale) can be swapped without touching ingestion or analytics.
 */
public interface EventRepository {

    void save(SecurityEvent event);

    List<SecurityEvent> findAll();
}
