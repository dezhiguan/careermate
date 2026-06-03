package com.careermate.task;

import java.util.Set;

public final class CareerTaskConstants {

    public static final String STATUS_TODO = "TODO";
    public static final String STATUS_DONE = "DONE";

    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_MEDIUM = "MEDIUM";
    public static final String PRIORITY_LOW = "LOW";

    public static final String CATEGORY_RESUME = "RESUME";
    public static final String CATEGORY_JOB_MATCH = "JOB_MATCH";
    public static final String CATEGORY_INTERVIEW = "INTERVIEW";
    public static final String CATEGORY_PROFILE = "PROFILE";
    public static final String CATEGORY_GENERAL = "GENERAL";

    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_AGENT = "agent";

    public static final int AGENT_TODO_LIMIT = 5;

    public static final Set<String> CATEGORIES = Set.of(
            CATEGORY_RESUME,
            CATEGORY_JOB_MATCH,
            CATEGORY_INTERVIEW,
            CATEGORY_PROFILE,
            CATEGORY_GENERAL
    );

    public static final Set<String> PRIORITIES = Set.of(PRIORITY_HIGH, PRIORITY_MEDIUM, PRIORITY_LOW);

    public static final Set<String> STATUSES = Set.of(STATUS_TODO, STATUS_DONE);

    private CareerTaskConstants() {
    }
}
