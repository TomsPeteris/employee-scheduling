package org.acme.employeescheduling.rest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicType;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchType;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.SolverManagerConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import ai.timefold.solver.core.api.score.analysis.ScoreAnalysis;
import ai.timefold.solver.core.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScore;
import ai.timefold.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;

import org.acme.employeescheduling.domain.EmployeeSchedule;
import org.acme.employeescheduling.rest.exception.EmployeeScheduleSolverException;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("schedules")
public class EmployeeScheduleResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmployeeScheduleResource.class);

    SolverManager<EmployeeSchedule, String> solverManager;
    SolutionManager<EmployeeSchedule, HardSoftBigDecimalScore> solutionManager;

    private final ConcurrentMap<String, Job> jobIdToJob = new ConcurrentHashMap<>();

    @Inject
    SolverConfig baseSolverConfig;

    @Inject
    public EmployeeScheduleResource(SolverManager<EmployeeSchedule, String> solverManager,
            SolutionManager<EmployeeSchedule, HardSoftBigDecimalScore> solutionManager) {
        this.solverManager = solverManager;
        this.solutionManager = solutionManager;
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces(MediaType.TEXT_PLAIN)
    public String solve(@QueryParam("algorithm") String algorithm, EmployeeSchedule problem) {
        String jobId = UUID.randomUUID().toString();
        jobIdToJob.put(jobId, Job.ofSchedule(problem));

        SolverConfig solverConfig = new SolverConfig(baseSolverConfig);
        ConstructionHeuristicPhaseConfig constructionHeuristicPhaseConfig = new ConstructionHeuristicPhaseConfig();
        constructionHeuristicPhaseConfig.setConstructionHeuristicType(ConstructionHeuristicType.FIRST_FIT);
        LocalSearchPhaseConfig localSearchPhaseConfig = new LocalSearchPhaseConfig();

        switch(algorithm) {
            case "TABU_SEARCH":
                localSearchPhaseConfig.setLocalSearchType(LocalSearchType.TABU_SEARCH);
                break;
            case "LATE_ACCEPTANCE":
                localSearchPhaseConfig.setLocalSearchType(LocalSearchType.LATE_ACCEPTANCE);
                break;
            default:
                localSearchPhaseConfig.setLocalSearchType(LocalSearchType.HILL_CLIMBING);
        }

        solverConfig.setPhaseConfigList(List.of(constructionHeuristicPhaseConfig, localSearchPhaseConfig));

        SolverFactory<EmployeeSchedule> solverFactory = SolverFactory.create(solverConfig);
        solverManager = this.overrideSolverManager(solverFactory);

        solverManager.solveBuilder()
                .withProblemId(jobId)
                .withProblemFinder(jobId_ -> jobIdToJob.get(jobId).schedule)
                .withBestSolutionConsumer(solution -> jobIdToJob.put(jobId, Job.ofSchedule(solution)))
                .withExceptionHandler((jobId_, exception) -> {
                    jobIdToJob.put(jobId, Job.ofException(exception));
                    LOGGER.error("Failed solving jobId ({}).", jobId, exception);
                })
                .run();
        return jobId;
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces(MediaType.APPLICATION_JSON)
    @Path("analyze")
    public ScoreAnalysis<HardSoftBigDecimalScore> analyze(EmployeeSchedule problem,
            @QueryParam("fetchPolicy") ScoreAnalysisFetchPolicy fetchPolicy) {
        return fetchPolicy == null ? solutionManager.analyze(problem) : solutionManager.analyze(problem, fetchPolicy);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{jobId}")
    public EmployeeSchedule getEmployeeSchedule(
            @Parameter(description = "The job ID returned by the POST method.") @PathParam("jobId") String jobId) {
        EmployeeSchedule schedule = getEmployeeScheduleAndCheckForExceptions(jobId);
        SolverStatus solverStatus = solverManager.getSolverStatus(jobId);
        schedule.setSolverStatus(solverStatus);
        return schedule;
    }

    private EmployeeSchedule getEmployeeScheduleAndCheckForExceptions(String jobId) {
        Job job = jobIdToJob.get(jobId);
        if (job == null) {
            throw new EmployeeScheduleSolverException(jobId, Response.Status.NOT_FOUND, "No schedule found.");
        }
        if (job.exception != null) {
            throw new EmployeeScheduleSolverException(jobId, job.exception);
        }
        return job.schedule;
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{jobId}")
    public EmployeeSchedule terminateSolving(
            @Parameter(description = "The job ID returned by the POST method.") @PathParam("jobId") String jobId) {
        solverManager.terminateEarly(jobId);
        return getEmployeeSchedule(jobId);
    }
//
//    @GET
//    @Produces(MediaType.APPLICATION_JSON)
//    @Path("{jobId}/status")
//    public EmployeeSchedule getStatus(
//            @Parameter(description = "The job ID returned by the POST method.") @PathParam("jobId") String jobId) {
//        EmployeeSchedule schedule = getEmployeeScheduleAndCheckForExceptions(jobId);
//        SolverStatus solverStatus = solverManager.getSolverStatus(jobId);
//        return new EmployeeSchedule(schedule.getScore(), solverStatus);
//    }

    private SolverManager<EmployeeSchedule, String> overrideSolverManager(SolverFactory<EmployeeSchedule> solverFactory) {
        SolverManagerConfig solverManagerConfig = new SolverManagerConfig();
        return SolverManager.create(solverFactory, solverManagerConfig);
    }

    private record Job(EmployeeSchedule schedule, Throwable exception) {

        static Job ofSchedule(EmployeeSchedule schedule) {
            return new Job(schedule, null);
        }

        static Job ofException(Throwable error) {
            return new Job(null, error);
        }
    }
}
