package com.deskdibs.user;

/**
 * Authorization role. Persisted as a string so the database stays readable and a
 * reordered enum can never silently change everybody's permissions.
 */
public enum UserRole {
    EMPLOYEE,
    MANAGER,
    ADMIN
}
