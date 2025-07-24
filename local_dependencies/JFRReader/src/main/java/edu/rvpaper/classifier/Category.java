package edu.rvpaper.classifier;

public enum Category {
    STANDARD_LIB,
    ASPECTJ,
    JAVAMOP,
    RVMONITOR,
    PROJECT,
    THIRD_PARTY_LIB,
    NATIVE,
    ASM,
    OTHER,
    VIOLATION,
    ASPECTJ_INSTRUMENTATION,
    ASPECTJ_RUNTIME,
    ASPECTJ_OTHER,
    MONITORING,
    SPECIFICATION,
    MOP_AND_AJC,
    RVMONITOR_LOCK, // rvmonitor -> LOCK
    MONITORING_LOCK, // monitoring code -> LOCK
    LOCK,
    TEST, // test -> mop
    TEST_METHOD, // test method -> mop
    TEST_FRAMEWORK, // test framework -> mop
    TEST_FIXTURE, // test fixture -> mop
    PROJECT_AND_FIXTURE, // fixture -> CUT -> mop
    THIRD_PARTY_LIB_AND_FIXTURE, // fixture -> LIB -> mop
    TEST_AND_FIXTURE, // fixture -> test code -> mop
    TEST_METHOD_AND_FIXTURE, // fixture -> test method -> mop
    TEST_FRAMEWORK_AND_FIXTURE, // fixture -> test framework -> mop
    AJC_PROJECT, // project -> ajc
    AJC_THIRD_PARTY_LIB, // third party lib -> ajc
    AJC_TEST, // test -> ajc
    AJC_TEST_METHOD, // test method -> ajc
    AJC_TEST_FRAMEWORK, // test framework -> ajc
    AJC_TEST_FIXTURE, // test fixture -> ajc
    AJC_PROJECT_AND_FIXTURE, // fixture -> CUT -> ajc
    AJC_THIRD_PARTY_LIB_AND_FIXTURE, // fixture -> LIB -> ajc
    AJC_TEST_AND_FIXTURE, // fixture -> test code -> ajc
    AJC_TEST_METHOD_AND_FIXTURE, // fixture -> test method -> ajc
    AJC_TEST_FRAMEWORK_AND_FIXTURE, // fixture -> test framework -> ajc
    AJC_OTHER, // sun.launcher.LauncherHelper -> ajc (ajc without knowing caller)
    AJC_UNKNOWN, // unknown -> ajc (ajc without knowing caller)
    MOP_BASE_ASPECT,
    UNKNOWN;

    public static Category[] getStandard() {
        return new Category[]{Category.STANDARD_LIB, Category.ASPECTJ, Category.JAVAMOP, Category.RVMONITOR,
                Category.PROJECT, Category.THIRD_PARTY_LIB, Category.NATIVE, Category.ASPECTJ, Category.OTHER,
                Category.UNKNOWN};
    }

    public static Category[] getViolation() {
        return new Category[]{Category.STANDARD_LIB, Category.ASPECTJ, Category.JAVAMOP, Category.RVMONITOR,
                Category.PROJECT, Category.THIRD_PARTY_LIB, Category.NATIVE, Category.ASPECTJ, Category.OTHER,
                Category.VIOLATION, Category.UNKNOWN};
    }

    public static Category[] getRV() {
        return new Category[]{Category.STANDARD_LIB, Category.JAVAMOP, Category.RVMONITOR, Category.PROJECT,
                Category.THIRD_PARTY_LIB, Category.NATIVE, Category.ASPECTJ, Category.OTHER, Category.VIOLATION,
                Category.ASPECTJ_INSTRUMENTATION, Category.ASPECTJ_RUNTIME, Category.ASPECTJ_OTHER, Category.MONITORING,
                Category.SPECIFICATION, Category.MOP_AND_AJC, Category.RVMONITOR_LOCK, Category.MONITORING_LOCK,
                Category.LOCK, Category.MOP_BASE_ASPECT, Category.UNKNOWN};
    }

    public static Category[] getOverall() {
        return new Category[]{Category.PROJECT, Category.THIRD_PARTY_LIB, Category.TEST, Category.TEST_METHOD,
                Category.TEST_FRAMEWORK, Category.TEST_FIXTURE, Category.PROJECT_AND_FIXTURE,
                Category.THIRD_PARTY_LIB_AND_FIXTURE, Category.TEST_AND_FIXTURE, Category.TEST_METHOD_AND_FIXTURE,
                Category.TEST_FRAMEWORK_AND_FIXTURE,

                Category.AJC_PROJECT, Category.AJC_THIRD_PARTY_LIB, Category.AJC_TEST, Category.AJC_TEST_METHOD,
                Category.AJC_TEST_FRAMEWORK, Category.AJC_TEST_FIXTURE, Category.AJC_PROJECT_AND_FIXTURE,
                Category.AJC_THIRD_PARTY_LIB_AND_FIXTURE, Category.AJC_TEST_AND_FIXTURE, Category.AJC_TEST_METHOD_AND_FIXTURE,
                Category.AJC_TEST_FRAMEWORK_AND_FIXTURE, Category.AJC_OTHER, Category.AJC_UNKNOWN,

                Category.OTHER, Category.UNKNOWN};
    }
}
