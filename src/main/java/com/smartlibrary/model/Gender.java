package com.smartlibrary.model;

/**
 * Gender choices of the registration form - an enum instead of the raw
 * "Male"/"Female" strings that used to be compared all over the controller.
 *
 * The database still stores the label (no schema change); the enum exists at
 * the place where the comparison happens, so an unknown/empty value simply
 * maps to null instead of silently matching nothing.
 */
public enum Gender {

    MALE("Male"),
    FEMALE("Female");

    private final String label;

    Gender(String label) {
        this.label = label;
    }

    /** The value shown in the UI and stored in the members table. */
    public String label() {
        return label;
    }

    /**
     * Static factory: database value (or anything a user typed) to enum.
     *
     * @return the matching constant, or null when the value is empty/unknown
     */
    public static Gender fromLabel(String label) {
        if (label == null) {
            return null;
        }
        for (Gender gender : values()) {
            if (gender.label.equalsIgnoreCase(label.trim())) {
                return gender;
            }
        }
        return null;
    }
}
