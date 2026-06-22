package io.github.faizul.user.model;

import io.github.faizul.storage.file.model.File;

public enum SubscriptionPlanType {
    FREEMIUM("Freemium", PlanLimits.builder()
        .storageQuota(1073741824L)          // Kapasitas Penyimpanan: 1 GB
        .maxCloudAccounts(1)                // Maksimal Akun Cloud: 1
        .aiDailyLimit(5)                    // Batas AI per Hari: 5 request
        .migrationDailyLimit(3)             // Batas Migrasi per Hari: 3 kali
        .migrationMaxFileSize(268435456L)   // Maks. Ukuran File Migrasi: 256 MB
        .publicShareLimit(30)               // Batas Link Share Publik Aktif: 30 link
        .privateShareLimit(30)              // Batas Share Privat Aktif: 30 email
        .maxWorkspaces(2)                   // Batas Maksimal Workspace: 2
        .maxInputTokensPerRequest(20000)    // Maksimal Token Input per Request: 20.000 (~80rb Karakter)
        .build()),
    
    PREMIUM_INDIVIDUAL("Premium Individual", PlanLimits.builder()
        .storageQuota(16106127360L)         // Kapasitas Penyimpanan: 15 GB
        .maxCloudAccounts(10)               // Maksimal Akun Cloud: 10
        .aiDailyLimit(50)                   // Batas AI per Hari: 50 request
        .migrationDailyLimit(-1)            // Batas Migrasi per Hari: Tanpa Batas (-1)
        .migrationMaxFileSize(-1L)          // Maks. Ukuran File Migrasi: Tanpa Batas (-1)
        .publicShareLimit(-1)               // Batas Link Share Publik Aktif: Tanpa Batas (-1)
        .privateShareLimit(-1)              // Batas Share Privat Aktif: Tanpa Batas (-1)
        .maxWorkspaces(-1)                  // Batas Maksimal Workspace: Tanpa Batas (-1)
        .maxInputTokensPerRequest(150000)   // Maksimal Token Input per Request: 150.000 (~600rb Karakter)
        .build()),
    
    PREMIUM_ACADEMIC("Premium Academic", PlanLimits.builder()
        .storageQuota(10737418240L)         // Kapasitas Penyimpanan: 10 GB
        .maxCloudAccounts(5)                // Maksimal Akun Cloud: 5
        .aiDailyLimit(30)                   // Batas AI per Hari: 30 request
        .migrationDailyLimit(30)            // Batas Migrasi per Hari: 30 kali
        .migrationMaxFileSize(10737418240L) // Maks. Ukuran File Migrasi: 10 GB
        .publicShareLimit(100)              // Batas Link Share Publik Aktif: 100 link
        .privateShareLimit(100)             // Batas Share Privat Aktif: 100 email
        .maxWorkspaces(15)                  // Batas Maksimal Workspace: 15
        .maxInputTokensPerRequest(75000)    // Maksimal Token Input per Request: 75.000 (~300rb Karakter)
        .build());

    private final String displayName;
    private final PlanLimits limits;

    SubscriptionPlanType(String displayName, PlanLimits limits) {
        this.displayName = displayName;
        this.limits = limits;
    }

    public String getDisplayName() { return displayName; }
    public PlanLimits getLimits() { return limits; }

    public static SubscriptionPlanType getPlan(String tier) {
        try {
            return valueOf(tier.toUpperCase());
        } catch (Exception e) {
            return FREEMIUM;
        }
    }
}
