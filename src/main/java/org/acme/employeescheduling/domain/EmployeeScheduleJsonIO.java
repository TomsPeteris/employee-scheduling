package org.acme.employeescheduling.domain;

import ai.timefold.solver.jackson.impl.domain.solution.JacksonSolutionFileIO;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EmployeeScheduleJsonIO extends JacksonSolutionFileIO<EmployeeSchedule> {
    public EmployeeScheduleJsonIO() { super(EmployeeSchedule.class,
        new ObjectMapper().findAndRegisterModules()); }
}
