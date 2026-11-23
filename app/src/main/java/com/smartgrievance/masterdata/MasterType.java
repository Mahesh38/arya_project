package com.smartgrievance.masterdata;

import com.smartgrievance.common.exception.Subject;

enum MasterType {
    DEPARTMENT("department", "departments", Subject.Type.DEPARTMENTS),
    CATEGORY("category", "categories", Subject.Type.CATEGORIES),
    AREA("area", "areas", Subject.Type.AREAS);

    final String table;
    final String plural;
    final Subject subject;

    MasterType(String table, String plural, Subject.Type subject) {
        this.table = table;
        this.plural = plural;
        this.subject = new Subject(subject);
    }

    static MasterType forPath(String plural) {
        return switch (plural) {
            case "departments" -> DEPARTMENT;
            case "categories" -> CATEGORY;
            case "areas" -> AREA;
            default -> throw new IllegalArgumentException("Unknown master-data type");
        };
    }

    String url() { return "/admin/" + plural; }
    String message(String action) { return "flash." + table + "." + action; }
}
