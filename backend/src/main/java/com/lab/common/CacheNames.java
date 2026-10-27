package com.lab.common;

/**
 * 缓存名称集中管理，避免注解里散落字符串。
 * 本地默认使用 Caffeine（L1），开启 Redis 后切换为 Redis（L2）。
 */
public final class CacheNames {

    public static final String DEVICE_PAGE = "devicePage";
    public static final String CATEGORIES = "categories";

    public static final String STATS_OVERVIEW = "statsOverview";
    public static final String STATS_STATUS = "statsStatus";
    public static final String STATS_TOP = "statsTop";
    public static final String STATS_CATEGORY = "statsCategory";
    public static final String STATS_TREND = "statsTrend";

    /** 预约变化后需要清理的统计缓存 */
    public static final String[] STATS_ALL = {
            STATS_OVERVIEW, STATS_STATUS, STATS_TOP, STATS_CATEGORY, STATS_TREND
    };

    private CacheNames() {
    }
}
