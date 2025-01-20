package org.acme.employeescheduling.solver;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Set;

import jakarta.inject.Inject;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;

import org.acme.employeescheduling.domain.Employee;
import org.acme.employeescheduling.domain.EmployeeSchedule;
import org.acme.employeescheduling.domain.Shift;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class EmployeeSchedulingConstraintProviderTest {
    private static final LocalDate DAY_1 = LocalDate.of(2021, 2, 1);
    private static final LocalDate DAY_2 = LocalDate.of(2021, 2, 2);
    private static final LocalDateTime MORNING_SHIFT_START = DAY_1.atTime(LocalTime.of(10, 0));
    private static final LocalDateTime MORNING_SHIFT_END = DAY_1.atTime(LocalTime.of(18, 0));
    private static final LocalDateTime AFTERNOON_SHIFT_START = DAY_1.atTime(LocalTime.of(14, 0));
    private static final LocalDateTime AFTERNOON_SHIFT_END = DAY_1.atTime(LocalTime.of(22, 0));

    @Inject
    ConstraintVerifier<EmployeeSchedulingConstraintProvider, EmployeeSchedule> constraintVerifier;

    @Test
    void noOverlappingShifts() {
        Employee employee = new Employee("Alice", "Specialist", Collections.emptySet(), Collections.emptySet(), 40);
        constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::noOverlappingShifts)
            .given(
                new Shift("1", MORNING_SHIFT_START, MORNING_SHIFT_END, "Specialist", employee),
                new Shift("2", MORNING_SHIFT_START, MORNING_SHIFT_END, "Specialist", employee))
            .penalizesByMoreThan(0);

        constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::noOverlappingShifts)
            .given(
                new Shift("1", AFTERNOON_SHIFT_START, AFTERNOON_SHIFT_END, "Specialist", employee),
                new Shift("2", AFTERNOON_SHIFT_START, AFTERNOON_SHIFT_END, "Specialist", employee))
            .penalizesByMoreThan(0);

        constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::noOverlappingShifts)
            .given(
                new Shift("1", MORNING_SHIFT_START, MORNING_SHIFT_END, "Specialist", employee),
                new Shift("2", AFTERNOON_SHIFT_START, AFTERNOON_SHIFT_END, "Specialist", employee))
            .penalizes(0);
    }

    @Test
    void unavailableEmployee() {
        Employee employee = new Employee("Alice", "Specialist", Set.of(DAY_1), Set.of(DAY_2), 40);
        constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::unavailableEmployee)
            .given(
                employee,
                new Shift("1", MORNING_SHIFT_START, MORNING_SHIFT_END, "Specialist", employee))
            .penalizesByMoreThan(0);

        constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::unavailableEmployee)
            .given(
                employee,
                new Shift("1", AFTERNOON_SHIFT_START.plusDays(1), AFTERNOON_SHIFT_END.plusDays(1), "Specialist", employee))
            .penalizes(0);
    }

    @Test
    void maxWorkingHoursPerWeekConstraint() {
        Employee employee = new Employee("Alice", "Specialist", Collections.emptySet(), Collections.emptySet(), 40);
        constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::maxWorkingHoursPerWeek)
            .given(
                new Shift("1", MORNING_SHIFT_START, MORNING_SHIFT_END, "Specialist", employee),
                new Shift("1", MORNING_SHIFT_START.plusDays(1), MORNING_SHIFT_END.plusDays(1), "Specialist", employee),
                new Shift("1", MORNING_SHIFT_START.plusDays(2), MORNING_SHIFT_END.plusDays(2), "Specialist", employee),
                new Shift("1", MORNING_SHIFT_START.plusDays(3), MORNING_SHIFT_END.plusDays(3), "Specialist", employee),
                new Shift("1", MORNING_SHIFT_START.plusDays(4), MORNING_SHIFT_END.plusDays(4), "Specialist", employee),
                new Shift("1", MORNING_SHIFT_START.plusDays(5), MORNING_SHIFT_END.plusDays(5), "Specialist", employee))
            .penalizesByMoreThan(0);

        constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::maxWorkingHoursPerWeek)
            .given(
                new Shift("1", MORNING_SHIFT_START, MORNING_SHIFT_END, "Specialist", employee),
                new Shift("1", MORNING_SHIFT_START.plusDays(1), MORNING_SHIFT_END.plusDays(1), "Specialist", employee),
                new Shift("1", MORNING_SHIFT_START.plusDays(2), MORNING_SHIFT_END.plusDays(2), "Specialist", employee),
                new Shift("1", MORNING_SHIFT_START.plusDays(3), MORNING_SHIFT_END.plusDays(3), "Specialist", employee),
                new Shift("1", MORNING_SHIFT_START.plusDays(4), MORNING_SHIFT_END.plusDays(4), "Specialist", employee))
            .penalizes(0);
    }

    @Test
    void roleRequirementConstraint() {
        Employee specialist = new Employee("Alice", "Specialist", Collections.emptySet(), Collections.emptySet(), 40);
        Employee assistant = new Employee("Bob", "Assistant", Collections.emptySet(), Collections.emptySet(), 40);

        constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::roleRequirementConstraint)
            .given(
                new Shift("1", MORNING_SHIFT_START, MORNING_SHIFT_END, "Specialist", assistant),
                new Shift("2", MORNING_SHIFT_START, MORNING_SHIFT_END, "Specialist", specialist))
            .penalizes(0);

        constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::roleRequirementConstraint)
            .given(
                new Shift("1", MORNING_SHIFT_START, MORNING_SHIFT_END, "Specialist", assistant),
                new Shift("2", MORNING_SHIFT_START, MORNING_SHIFT_END, "Specialist", assistant))
            .penalizesByMoreThan(0);
    }

    @Test
    void preferredHolidaysConstraint() {
        Employee specialist = new Employee("Alice", "Specialist", Collections.emptySet(), Set.of(DAY_1), 40);

        Shift holidayShift = new Shift("1", MORNING_SHIFT_START, MORNING_SHIFT_END, "Specialist", specialist);
        Shift regularShift = new Shift("2", MORNING_SHIFT_START.plusDays(1), MORNING_SHIFT_END.plusDays(1), "Specialist", specialist);

        constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::preferredHolidaysConstraint)
            .given(
                specialist,
                holidayShift
            )
            .penalizesByMoreThan(0);

        constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::preferredHolidaysConstraint)
            .given(
                specialist,
                regularShift
            )
            .penalizes(0);
    }

    @Test
    void balanceEmployeeShiftAssignments() {
        Employee specialist1 = new Employee("Alice", "Specialist", Collections.emptySet(), Collections.emptySet(), 40);
        Employee specialist2 = new Employee("Bob", "Specialist", Collections.emptySet(), Collections.emptySet(), 40);
        Employee specialist3 = new Employee("Charlie", "Specialist", Collections.emptySet(), Collections.emptySet(), 40);

        // All employees have 1 shift => 3 employees => each group is penalized by 1 => total penalty = 3.
        Shift shift1 = new Shift("1", MORNING_SHIFT_START, MORNING_SHIFT_END, "Specialist", specialist1);
        Shift shift2 = new Shift("2", MORNING_SHIFT_START.plusDays(1), MORNING_SHIFT_END.plusDays(1), "Specialist", specialist2);
        Shift shift3 = new Shift("3", MORNING_SHIFT_START.plusDays(2), MORNING_SHIFT_END.plusDays(2), "Specialist", specialist3);

        constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::balanceEmployeeShiftAssignments)
            .given(
                specialist1, specialist2, specialist3,
                shift1, shift2, shift3
            )
            .penalizesByMoreThan(0);

        // 2 employees each with 1 shift, 1 employee with 2 shifts => total groups = 3 => penalty = 3.
        Shift extraShift = new Shift("4", MORNING_SHIFT_START.plusDays(3), MORNING_SHIFT_END.plusDays(3), "Specialist", specialist1);

        constraintVerifier.verifyThat(EmployeeSchedulingConstraintProvider::balanceEmployeeShiftAssignments)
            .given(
                specialist1, specialist2, specialist3,
                shift1, shift2, shift3, extraShift
            )
            .penalizesByMoreThan(0);
    }

}
