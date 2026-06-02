package com.careermate.security;

public final class CurrentUserContext {

    private static final ThreadLocal<CurrentUser> CONTEXT = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(CurrentUser user) {
        CONTEXT.set(user);
    }

    public static CurrentUser get() {
        return CONTEXT.get();
    }

    public static Long getUserId() {
        CurrentUser user = CONTEXT.get();
        return user == null ? null : user.getUserId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
