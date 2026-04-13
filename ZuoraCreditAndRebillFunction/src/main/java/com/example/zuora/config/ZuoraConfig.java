package com.example.zuora.config;

import com.zuora.ZuoraClient;

public class ZuoraConfig {

    private final String clientId;
    private final String clientSecret;
    private final ZuoraClient.ZuoraEnv zuoraEnv;

    public ZuoraConfig() {
        this.clientId = requireEnv("ZUORA_CLIENT_ID");
        this.clientSecret = requireEnv("ZUORA_CLIENT_SECRET");
        this.zuoraEnv = parseZuoraEnv(requireEnv("ZUORA_ENV"));
    }

    private static ZuoraClient.ZuoraEnv parseZuoraEnv(String value) {
        try {
            return ZuoraClient.ZuoraEnv.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Invalid ZUORA_ENV value: '" + value + "'. Valid values: SBX, PROD, SBX_NA, SBX_EU, PROD_NA, PROD_EU, PROD_AP", e);
        }
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable not set: " + name);
        }
        return value;
    }

    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public ZuoraClient.ZuoraEnv getZuoraEnv() { return zuoraEnv; }
}
