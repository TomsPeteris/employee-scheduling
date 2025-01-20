package org.acme.employeescheduling.solver;

import static ai.timefold.solver.core.api.score.stream.Joiners.equal;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import java.util.function.Function;

import ai.timefold.solver.core.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

import org.acme.employeescheduling.domain.Employee;
import org.acme.employeescheduling.domain.Shift;
import org.threeten.extra.YearWeek;

public class EmployeeSchedulingConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
            noOverlappingShifts(constraintFactory),
            unavailableEmployee(constraintFactory),
            maxWorkingHoursPerWeek(constraintFactory),
            roleRequirementConstraint(constraintFactory),
            balanceEmployeeShiftAssignments(constraintFactory),
            preferredHolidaysConstraint(constraintFactory)
        };
    }

    Constraint noOverlappingShifts(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachUniquePair(Shift.class,
                equal(Shift::getEmployee), // Same employee
                equal(Shift::getStart)) // Same start time
            .penalize(HardSoftBigDecimalScore.ONE_HARD)
            .asConstraint("Overlapping shifts for same employee");
    }

    Constraint unavailableEmployee(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Shift.class)
            .join(Employee.class, equal(Shift::getEmployee, Function.identity()))
            .flattenLast(employee -> employee.getUnavailableDates() != null ? employee.getUnavailableDates() : Set.of())
            .filter(Shift::isOverlappingWithDate)
            .penalize(HardSoftBigDecimalScore.ONE_HARD, Shift::getOverlappingDurationInMinutes)
            .asConstraint("Unavailable employee");
    }

    Constraint maxWorkingHoursPerWeek(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Shift.class)
            // Group by (employee, date), counting number of shifts that day.
            .groupBy(
                Shift::getEmployee,
                shift -> shift.getStart().toLocalDate(),
                ConstraintCollectors.count()
            )
            // dailyHours is 8 if shiftCount == 1, else 12 (assuming max 2 shifts/day).
            .groupBy(
                (employee, date, shiftCount) -> employee,
                (employee, date, shiftCount) -> YearWeek.from(date),
                ConstraintCollectors.sum((employee, date, shiftCount) ->
                    shiftCount == 1 ? 8 : 12
                )
            )
            // Filter out those that exceed maxWorkingHoursPerWeek.
            .filter((employee, yearWeek, totalWeeklyHours) ->
                totalWeeklyHours > employee.getMaxWorkingHoursPerWeek()
            )
            .penalize(
                HardSoftBigDecimalScore.ONE_HARD,
                (employee, yearWeek, totalWeeklyHours) ->
                    totalWeeklyHours - employee.getMaxWorkingHoursPerWeek()
            )
            .asConstraint("Max weekly working hours exceeded");
    }


    Constraint roleRequirementConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Shift.class)
            .groupBy(
                shift -> shift.getStart().toLocalDate(), // Group by date
                shift -> getTimeSlot(shift),             // Group by morning or afternoon slot
                ConstraintCollectors.toSet(Function.identity()) // Collect all shifts in the time slot
            )
            .filter((date, timeSlot, shifts) ->
                shifts.stream().noneMatch(shift ->
                    shift.getEmployee() != null && "Specialist".equals(shift.getEmployee().getRole())))
            .penalize(HardSoftBigDecimalScore.ONE_HARD)
            .asConstraint("At least one Specialist per time slot");
    }

    Constraint balanceEmployeeShiftAssignments(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Shift.class)
            .groupBy(Shift::getEmployee, ConstraintCollectors.count())
            .penalizeBigDecimal(HardSoftBigDecimalScore.ONE_SOFT)
            .asConstraint("Balance employee shift assignments");
    }


    Constraint preferredHolidaysConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Shift.class)
            .filter(shift -> shift.getEmployee() != null) // Ensure the shift has an assigned employee
            .join(Employee.class, equal(Shift::getEmployee, Function.identity()))
            .flattenLast(employee -> employee.getPreferredHolidays() != null ? employee.getPreferredHolidays() : Set.of())
            .filter((shift, holiday) -> shift.getStart().toLocalDate().equals(holiday)) // Check if shift starts on a preferred holiday
            .penalize(HardSoftBigDecimalScore.ONE_SOFT)
            .asConstraint("Preferred holiday not respected");
    }

    private String getTimeSlot(Shift shift) {
        LocalDateTime start = shift.getStart();
        return start.toLocalTime().isBefore(LocalTime.of(14, 0)) ? "Morning" : "Afternoon";
    }
}
