package com.akamai.miniwsa.domain;

/** Geographic origin of the request, as resolved at the edge. */
public record GeoLocation(
        String country,
        String city
) {
}
