package com.example.taskapp.geo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class CountryLookupService {
    private final RestClient restClient;
    private final String providerUrl;
    private final String localCountry;

    public CountryLookupService(
            RestClient.Builder restClientBuilder,
            @Value("${app.geo.provider-url}") String providerUrl,
            @Value("${app.geo.local-country}") String localCountry) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.providerUrl = providerUrl.replaceAll("/+$", "");
        this.localCountry = localCountry;
    }

    public String countryFor(String ipAddress) {
        if (isPrivateAddress(ipAddress)) {
            if (localCountry == null || localCountry.isBlank()) {
                throw new GeoLocationException("Country lookup is not configured for private addresses");
            }
            return localCountry.toUpperCase(Locale.ROOT);
        }

        try {
            String country = restClient.get()
                    .uri(providerUrl + "/" + ipAddress + "/country/")
                    .retrieve()
                    .body(String.class);
            if (country == null || !country.trim().matches("[A-Za-z]{2}")) {
                throw new GeoLocationException("Country lookup returned an invalid response");
            }
            return country.trim().toUpperCase(Locale.ROOT);
        } catch (RestClientException exception) {
            throw new GeoLocationException("Country lookup is temporarily unavailable");
        }
    }

    private boolean isPrivateAddress(String address) {
        try {
            InetAddress parsed = InetAddress.getByName(address);
            return parsed.isAnyLocalAddress() || parsed.isLoopbackAddress()
                    || parsed.isLinkLocalAddress() || parsed.isSiteLocalAddress();
        } catch (UnknownHostException exception) {
            throw new GeoLocationException("Unable to determine the request location");
        }
    }
}
