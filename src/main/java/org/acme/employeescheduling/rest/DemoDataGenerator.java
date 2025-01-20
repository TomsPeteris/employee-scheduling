package org.acme.employeescheduling.rest;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;

import org.acme.employeescheduling.domain.Employee;
import org.acme.employeescheduling.domain.EmployeeSchedule;
import org.acme.employeescheduling.domain.Shift;

@ApplicationScoped
public class DemoDataGenerator {
    public enum DemoData {
        SMALL(new DemoDataParameters(
            List.of("Specialist", "Assistant"),
            7,
            5,
            List.of(
                new CountDistribution(1, 1)
            ),
            List.of(
                new CountDistribution(1, 1)
            ),
            0)
        ),
        MEDIUM(new DemoDataParameters(
            List.of("Specialist", "Assistant"),
            30,
            9,
            List.of(
                new CountDistribution(1, 2),
                new CountDistribution(2, 1)

            ),
            List.of(
                new CountDistribution(1, 2),
                new CountDistribution(2, 1)

            ),
            0)
        ),
        LARGE(new DemoDataParameters(
            List.of("Specialist", "Assistant"),
            90,
            12,
            List.of(
                new CountDistribution(1, 3),
                new CountDistribution(2, 2),
                new CountDistribution(3, 1)

            ),
            List.of(
                new CountDistribution(1, 3),
                new CountDistribution(2, 2),
                new CountDistribution(3, 1)

            ),
            0)
        ),
        HUGE(new DemoDataParameters(
            List.of("Specialist", "Assistant"),
            365,
            16,
            List.of(
                new CountDistribution(1, 3),
                new CountDistribution(2, 2),
                new CountDistribution(3, 1)

            ),
            List.of(
                new CountDistribution(1, 10),
                new CountDistribution(2, 10),
                new CountDistribution(3, 10),
                new CountDistribution(4, 10),
                new CountDistribution(5, 10),
                new CountDistribution(6, 10),
                new CountDistribution(7, 10),
                new CountDistribution(8, 10)
            ),
            0)
        );


        private final DemoDataParameters parameters;

        DemoData(DemoDataParameters parameters) {
            this.parameters = parameters;
        }

        public DemoDataParameters getParameters() {
            return parameters;
        }
    }

    public record CountDistribution(int count, double weight) {
    }

    public record DemoDataParameters(List<String> roles, int daysInSchedule, int employeeCount,
                                     List<CountDistribution> availabilityCountDistribution, List<CountDistribution> preferredHolidayCountDistribution, int randomSeed) {
    }

    private static final String[] FIRST_NAMES = {"Amy", "Beth", "Carl", "Dan", "Elsa", "Flo", "Gus", "Hugo", "Ivy", "Jay"};
    private static final String[] LAST_NAMES = {"Cole", "Fox", "Green", "Jones", "King", "Li", "Poe", "Rye", "Smith", "Watt"};
    private static final Duration SHIFT_LENGTH = Duration.ofHours(8);
    private static final LocalTime MORNING_SHIFT_START_TIME = LocalTime.of(10, 0);
    private static final LocalTime AFTERNOON_SHIFT_START_TIME = LocalTime.of(14, 0);

    public EmployeeSchedule generateDemoData(DemoData demoData) {
        return generateDemoData(demoData.getParameters());
    }

    public EmployeeSchedule generateDemoData(DemoDataParameters parameters) {
        EmployeeSchedule employeeSchedule = new EmployeeSchedule();

        LocalDate startDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        Random random = new Random(parameters.randomSeed);

        List<String> namePermutations = joinAllCombinations(FIRST_NAMES, LAST_NAMES);
        Collections.shuffle(namePermutations, random);

        List<Employee> employees = new ArrayList<>();
        for (int i = 0; i < parameters.employeeCount; i++) {
            String role = pickRandom(parameters.roles, random);
            Employee employee = new Employee(namePermutations.get(i), role, new LinkedHashSet<>(), new LinkedHashSet<>(), 40);
            employees.add(employee);
        }
        employeeSchedule.setEmployees(employees);

        List<Shift> shifts = new LinkedList<>();
        for (int i = 0; i < parameters.daysInSchedule; i++) {
            LocalDate date = startDate.plusDays(i);

            // Assign unavailable dates
            Set<Employee> employeesWithAvailabilitiesOnDay = pickSubset(employees, random, parameters.availabilityCountDistribution);
            for (Employee employee : employeesWithAvailabilitiesOnDay) {
                employee.getUnavailableDates().add(date);
            }
        }

        // Assign preferred holidays ensuring no overlap with unavailableDates
        for (Employee employee : employees) {
            int preferredHolidayCount = pickCount(random, parameters.preferredHolidayCountDistribution);

            for (int j = 0; j < preferredHolidayCount; j++) {
                LocalDate holiday;
                int attempts = 0;

                // Find a non-overlapping holiday
                do {
                    holiday = startDate.plusDays(random.nextInt(parameters.daysInSchedule));
                    attempts++;
                } while ((employee.getUnavailableDates().contains(holiday) || employee.getPreferredHolidays().contains(holiday)) && attempts < 100);

                // Add holiday if a valid one is found
                if (attempts < 100) {
                    employee.getPreferredHolidays().add(holiday);
                }
            }
        }

        // Generate shifts for all days
        for (int i = 0; i < parameters.daysInSchedule; i++) {
            LocalDate date = startDate.plusDays(i);
            shifts.addAll(generateShiftsForDay(parameters, date, random));
        }

        AtomicInteger countShift = new AtomicInteger();
        shifts.forEach(s -> s.setId(Integer.toString(countShift.getAndIncrement())));
        employeeSchedule.setShifts(shifts);

        return employeeSchedule;
    }



    private List<Shift> generateShiftsForDay(DemoDataParameters parameters, LocalDate date, Random random) {
        List<Shift> shifts = new LinkedList<>();

        // Morning Shift
        LocalDateTime shiftStartDateTime = date.atTime(MORNING_SHIFT_START_TIME);
        LocalDateTime shiftEndDateTime = shiftStartDateTime.plus(SHIFT_LENGTH);
        shifts.addAll(generateShiftForTimeslot(parameters, shiftStartDateTime, shiftEndDateTime, random));
        // Afternoon Shift
        shiftStartDateTime = date.atTime(AFTERNOON_SHIFT_START_TIME);
        shiftEndDateTime = shiftStartDateTime.plus(SHIFT_LENGTH);
        shifts.addAll(generateShiftForTimeslot(parameters, shiftStartDateTime, shiftEndDateTime, random));

        return shifts;
    }

    private List<Shift> generateShiftForTimeslot(DemoDataParameters parameters, LocalDateTime timeslotStart, LocalDateTime timeslotEnd, Random random) {
        var shiftCount = 2;

        List<Shift> shifts = new LinkedList<>();
        for (int i = 0; i < shiftCount; i++) {
            shifts.add(new Shift(timeslotStart, timeslotEnd, "Specialist"));
        }
        return shifts;
    }

    private <T> T pickRandom(List<T> source, Random random) {
        return source.get(random.nextInt(source.size()));
    }

    private int pickCount(Random random, List<CountDistribution> countDistribution) {
        double probabilitySum = 0;
        for (var possibility : countDistribution) {
            probabilitySum += possibility.weight;
        }
        var choice = random.nextDouble(probabilitySum);
        int numOfItems = 0;
        while (choice >= countDistribution.get(numOfItems).weight) {
            choice -= countDistribution.get(numOfItems).weight;
            numOfItems++;
        }
        return countDistribution.get(numOfItems).count;
    }

    private <T> Set<T> pickSubset(List<T> sourceSet, Random random, List<CountDistribution> countDistribution) {
        var count = pickCount(random, countDistribution);
        List<T> items = new ArrayList<>(sourceSet);
        Collections.shuffle(items, random);
        return new HashSet<>(items.subList(0, count));
    }

    private List<String> joinAllCombinations(String[]... partArrays) {
        int size = 1;
        for (String[] partArray : partArrays) {
            size *= partArray.length;
        }
        List<String> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            StringBuilder item = new StringBuilder();
            int sizePerIncrement = 1;
            for (String[] partArray : partArrays) {
                item.append(' ');
                item.append(partArray[(i / sizePerIncrement) % partArray.length]);
                sizePerIncrement *= partArray.length;
            }
            item.delete(0, 1);
            out.add(item.toString());
        }
        return out;
    }
}
