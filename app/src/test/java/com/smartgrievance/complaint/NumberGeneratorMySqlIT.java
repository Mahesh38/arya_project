package com.smartgrievance.complaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgrievance.testsupport.FlowTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** BR-11, D19: Java port of docs/db/verify/concurrency-test.sh. Each number is taken in its own transaction. */
@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
class NumberGeneratorMySqlIT extends FlowTestBase {
    @Autowired ComplaintNumberGenerator generator;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcClient jdbc;

    @ParameterizedTest(name = "TC-Q-41 {0} threads x {1} numbers on {2} new year(s)")
    @CsvSource({"8,50,1", "16,25,3"})
    @DisplayName("TC-Q-41 parallel numbering gives distinct gap-free numbers per year")
    void parallelNumbersAreDistinctAndGapFree(int threads, int perThread, int years) throws Exception {
        for (int y = 0; y < years; y++) {
            int year = 2090 + threads + y;
            assertThat(jdbc.sql("SELECT COUNT(*) FROM complaint_sequence WHERE seq_year = :year")
                    .param("year", year).query(Long.class).single()).isZero();
            var start = new CyclicBarrier(threads);
            var tasks = new ArrayList<Callable<List<Integer>>>();
            for (int t = 0; t < threads; t++) {
                tasks.add(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    var mine = new ArrayList<Integer>();
                    for (int i = 0; i < perThread; i++) mine.add(transactions.execute(status -> generator.next(year)));
                    return mine;
                });
            }
            var numbers = new ArrayList<Integer>();
            try (var pool = Executors.newFixedThreadPool(threads)) {
                for (var future : pool.invokeAll(tasks, 120, TimeUnit.SECONDS)) numbers.addAll(future.get());
            }
            int expected = threads * perThread;
            assertThat(numbers).as("year %d", year).hasSize(expected).doesNotHaveDuplicates()
                    .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, expected).boxed().toList());
            assertThat(jdbc.sql("SELECT last_number FROM complaint_sequence WHERE seq_year = :year")
                    .param("year", year).query(Integer.class).single()).isEqualTo(expected);
        }
    }
}
