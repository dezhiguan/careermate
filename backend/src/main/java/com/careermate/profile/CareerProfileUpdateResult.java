package com.careermate.profile;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class CareerProfileUpdateResult {

    private final boolean updated;
    private final List<String> updatedFields;
    private final String targetRole;

    private CareerProfileUpdateResult(boolean updated, List<String> updatedFields, String targetRole) {
        this.updated = updated;
        this.updatedFields = updatedFields == null ? Collections.emptyList() : updatedFields;
        this.targetRole = targetRole;
    }

    public static CareerProfileUpdateResult notUpdated() {
        return new CareerProfileUpdateResult(false, Collections.emptyList(), null);
    }

    public static CareerProfileUpdateResult updated(List<String> updatedFields, String targetRole) {
        return new CareerProfileUpdateResult(true, updatedFields, targetRole);
    }
}
