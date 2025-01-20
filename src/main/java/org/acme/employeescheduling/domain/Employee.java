package org.acme.employeescheduling.domain;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@Setter @Getter
@AllArgsConstructor @NoArgsConstructor
@ToString @EqualsAndHashCode
public class Employee {
    @PlanningId
    private String name;
    private String role;
    private Set<LocalDate> unavailableDates = new HashSet<>();
    private Set<LocalDate> preferredHolidays = new HashSet<>();
    private int maxWorkingHoursPerWeek;
}
