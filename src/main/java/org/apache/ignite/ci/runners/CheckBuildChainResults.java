package org.apache.ignite.ci.runners;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.ignite.Ignite;
import org.apache.ignite.ci.ITeamcity;
import org.apache.ignite.ci.IgnitePersistentTeamcity;
import org.apache.ignite.ci.IgniteTeamcityHelper;
import org.apache.ignite.ci.db.TcHelperDb;
import org.apache.ignite.ci.model.hist.Build;
import org.apache.ignite.ci.model.result.FullBuildInfo;
import org.apache.ignite.ci.model.result.TestOccurrencesRef;
import org.apache.ignite.ci.model.result.problems.ProblemOccurrence;
import org.apache.ignite.ci.model.result.problems.ProblemOccurrences;

/**
 * Created by dpavlov on 03.08.2017
 */
public class CheckBuildChainResults {

    private static class ChainContext {
        private FullBuildInfo results;
        private List<FullSuiteContext> list = new ArrayList<>();

        public ChainContext(FullBuildInfo results, List<FullSuiteContext> list) {
            this.results = results;
            this.list = list;
        }

        public int buildProblems() {
            return (int)list.stream().filter(FullSuiteContext::hasNontestBuildProblem).count();
        }

        public List<FullSuiteContext> suites() {
            return list;
        }

        public String suiteName() {
            return results.suiteName();
        }

        public int failedTests() {
            return list.stream().mapToInt(FullSuiteContext::failedTests).sum();
        }
    }

    private static class FullSuiteContext {
        private FullBuildInfo buildInfo;
        private List<ProblemOccurrence> problems;

        public FullSuiteContext(FullBuildInfo buildInfo) {
            this.buildInfo = buildInfo;
        }

        public void setProblems(List<ProblemOccurrence> problems) {
            this.problems = problems;
        }

        public String suiteName() {
            return buildInfo.suiteName();
        }

        public String suiteId() {
            return buildInfo.getId();
        }

        public boolean hasNontestBuildProblem() {
            return problems != null && problems.stream().anyMatch(problem ->
                !"TC_FAILED_TESTS".equals(problem.type)
                    && !"SNAPSHOT_DEPENDENCY_ERROR_BUILD_PROCEEDS_TYPE".equals(problem.type)
                    && !"BuildFailureOnMessage".equals(problem.type));

            //todo what to do with BuildFailureOnMessage, now it is ignored
        }

        public int failedTests() {
            TestOccurrencesRef testOccurrences = buildInfo.testOccurrences;
            if (testOccurrences == null)
                return 0;
            Integer failed = testOccurrences.failed;
            return failed == null ? 0 : failed;
        }
    }

    private static class SuiteInBranch implements Comparable<SuiteInBranch> {
        String id;
        String branch;

        public SuiteInBranch(String id, String branch) {
            this.id = id;
            this.branch = branch;
        }

        @Override public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            SuiteInBranch id = (SuiteInBranch)o;

            if (this.id != null ? !this.id.equals(id.id) : id.id != null)
                return false;
            return branch != null ? branch.equals(id.branch) : id.branch == null;
        }

        @Override public int hashCode() {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + (branch != null ? branch.hashCode() : 0);
            return result;
        }

        @Override public int compareTo(SuiteInBranch o) {
            int runConfCompare = id.compareTo(o.id);
            if (runConfCompare != 0)
                return runConfCompare;
            return branch.compareTo(o.branch);
        }

        @Override public String toString() {
            return "{" +
                "id='" + id + '\'' +
                ", branch='" + branch + '\'' +
                '}';
        }
    }

    private static class BuildHistory {
        Map<String, ChainContext> map = new TreeMap<>();
    }

    private static class FailuresHistory {
        int success = 0;
        int totalRun = 0;

        public void addRun(boolean ok) {
            totalRun++;
            if (ok)
                success++;
        }

        public String passRateStr() {
            return String.format("%.2f", passRate());
        }

        private double passRate() {
            return (double)(success) / totalRun;
        }
    }

    private static class BuildMetricsHistory {
        private Map<SuiteInBranch, BuildHistory> map = new TreeMap<>();
        private LinkedHashSet<SuiteInBranch> keys = new LinkedHashSet<>();
        private Map<String, FailuresHistory> failuresHistoryMap = new TreeMap<>();

        public BuildHistory history(SuiteInBranch id) {
            return map.computeIfAbsent(id, k -> {
                keys.add(k);
                return new BuildHistory();
            });
        }

        public Set<SuiteInBranch> builds() {
            return keys;
        }

        public TreeSet<String> dates() {
            Stream<String> stream = map.values().stream().flatMap(v -> v.map.keySet().stream());
            TreeSet<String> dates = new TreeSet<>();
            stream.forEach(dates::add);
            return dates;
        }

        public ChainContext build(SuiteInBranch next, String date) {
            BuildHistory hist = map.get(next);
            if (hist == null)
                return null;
            return hist.map.get(date);
        }

        public void addSuiteResult(String suiteName, boolean ok) {
            failuresHistoryMap.computeIfAbsent(suiteName, k -> new FailuresHistory())
                .addRun(ok);
        }
    }

    public static void main(String[] args) throws Exception {

        Ignite ignite = TcHelperDb.start();
        BuildMetricsHistory history;
        try {
            history = new BuildMetricsHistory();


            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
                collectHistory(history, teamcity, "Ignite20Tests_RunAll", "pull/2296/head");
            }

            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "private")) {
                collectHistory(history, teamcity, "id8xIgniteGridGainTests_RunAll", "ignite-2.1.3");
            }

            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "public")) {
                collectHistory(history, teamcity, "Ignite20Tests_RunAll", "refs/heads/master");
            }
            try (ITeamcity teamcity = new IgnitePersistentTeamcity(ignite, "private")) {
                collectHistory(history, teamcity, "id8xIgniteGridGainTests_RunAll", "refs/heads/master");
            }
        }
        finally {
            TcHelperDb.stop(ignite);
        }

        printTable(history);

        history.failuresHistoryMap.forEach(
            (k, v) -> {
                if (v.passRate() < 0.2)
                    System.out.println(k + " " + v.passRateStr());
            }
        );
    }

    private static void printTable(BuildMetricsHistory history) throws ParseException {
        System.out.print("Date\t");
        for (SuiteInBranch next : history.builds()) {
            System.out.print(next.id + "\t" + next.branch + "\t \t");
        }
        System.out.print("\n");

        for (String date : history.dates()) {
            Date mddd = new SimpleDateFormat("yyyyMMdd").parse(date);
            String dispDate = new SimpleDateFormat("dd.MM.yyyy").format(mddd);
            System.out.print(dispDate + "\t");
            for (SuiteInBranch next : history.builds()) {
                ChainContext suiteCtx = history.build(next, date);

                System.out.print(
                    (suiteCtx == null ? " " : suiteCtx.buildProblems()) + "\t"
                        + (suiteCtx == null ? " " : suiteCtx.failedTests()) + "\t \t");
            }

            System.out.print("\n");
        }
        System.out.println();
    }

    private static void collectHistory(BuildMetricsHistory history,
        ITeamcity teamcity, String id, String branch) throws ParseException {
        SuiteInBranch branchId = new SuiteInBranch(id, branch);
        BuildHistory suiteHistory = history.history(branchId);
        List<Build> all = teamcity.getFinishedBuildsIncludeFailed(id, branch);
        List<FullBuildInfo> fullBuildInfoList = all.stream().map(b -> teamcity.getBuildResults(b.href)).collect(Collectors.toList());
        for (FullBuildInfo next : fullBuildInfoList) {
            Date parse = next.getFinishDate();
            String dateForTable = new SimpleDateFormat("dd.MM.yyyy").format(parse);
            System.err.println(dateForTable);

            String dateForMap = new SimpleDateFormat("yyyyMMdd").format(parse);
            suiteHistory.map.computeIfAbsent(dateForMap, k -> {
                ChainContext ctx = loadChainContext(teamcity, next);
                for (FullSuiteContext suite : ctx.suites()) {
                    boolean suiteOk = suite.failedTests() == 0 && !suite.hasNontestBuildProblem();
                    history.addSuiteResult(teamcity.serverId() + "\t" + suite.suiteName(), suiteOk);
                }
                return ctx;
            });
        }
    }

    private static ChainContext getLatestSuiteRunStatus(IgniteTeamcityHelper teamcity,
        String suite, String branch) {
        List<Build> all = teamcity.getFinishedBuildsIncludeFailed(suite, branch);
        return all.isEmpty() ? null : loadChainContext(teamcity, all.get(0));
    }

    private static ChainContext loadChainContext(ITeamcity teamcity, Build latest) {
        FullBuildInfo results = teamcity.getBuildResults(latest.href);
        return loadChainContext(teamcity, results);
    }

    private static ChainContext loadChainContext(ITeamcity teamcity, FullBuildInfo results) {
        List<Build> builds = results.getSnapshotDependenciesNonNull();

        List<FullSuiteContext> fullBuildInfoList = new ArrayList<>();
        for (Build next : builds) {
            FullBuildInfo dep = teamcity.getBuildResults(next.href);

            FullSuiteContext ctx = new FullSuiteContext(dep);
            if (dep.problemOccurrences != null) {
                ProblemOccurrences problems = teamcity.getProblems(dep.problemOccurrences.href);
                ctx.setProblems(problems.getProblemsNonNull());
            }
            fullBuildInfoList.add(ctx);
        }

        return new ChainContext(results, fullBuildInfoList);
    }
}
