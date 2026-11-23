package com.smartgrievance.complaint;

import com.smartgrievance.common.PageResult;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ComplaintTrackingRepository {
    private final JdbcClient jdbc;

    ComplaintTrackingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    PageResult<ComplaintListItem> list(long citizen, CitizenListFilter filter, boolean watching, int reviewDays) {
        var statuses = StatusGroup.expand(filter.status());
        String from = watching
                ? " FROM complaint_watcher w JOIN complaint c ON c.id = w.complaint_id"
                : " FROM complaint c";
        String scope = watching ? "w.user_id = :citizen AND w.active = TRUE" : "c.owner_id = :citizen";
        String statusClause = statuses.isEmpty() || statuses.size() == ComplaintStatus.values().length
                ? "" : " AND c.status IN (:statuses)";
        String query = watching || filter.q() == null ? "" : filter.q().strip();
        String search = query.isEmpty() ? "" : " AND (c.complaint_no = :number OR LOWER(c.title) LIKE :q ESCAPE '!')";
        String where = from + " JOIN category k ON k.id = c.category_id WHERE " + scope + statusClause + search;
        String value = "%" + query.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var names = statuses.stream().map(Enum::name).toList();
        long total = parameters("SELECT COUNT(*)" + where, citizen, statusClause, names, search, value, query.toUpperCase(Locale.ROOT))
                .query(Long.class).single();
        var page = filter.paging().clampTo(total);
        var rows = parameters("""
                SELECT c.complaint_no, c.title, k.name AS category_name, c.status, c.created_at, c.resolved_at
                """ + where + " ORDER BY c.created_at DESC, c.id DESC LIMIT :limit OFFSET :offset",
                citizen, statusClause, names, search, value, query.toUpperCase(Locale.ROOT)).param("limit", page.size()).param("offset", page.offset())
                .query((row, index) -> {
                    var status = ComplaintStatus.valueOf(row.getString("status"));
                    var resolved = row.getObject("resolved_at", LocalDateTime.class);
                    return new ComplaintListItem(row.getString("complaint_no"), row.getString("title"),
                            row.getString("category_name"), null, null, status,
                            row.getObject("created_at", LocalDateTime.class), false, null, 0,
                            status == ComplaintStatus.RESOLVED && resolved != null ? resolved.plusDays(reviewDays) : null);
                }).list();
        return new PageResult<>(rows, page.page(), page.size(), total);
    }

    private JdbcClient.StatementSpec parameters(String sql, long citizen, String statusClause,
            java.util.List<String> statuses, String search, String query, String number) {
        var statement = jdbc.sql(sql).param("citizen", citizen);
        if (!statusClause.isEmpty()) statement.param("statuses", statuses);
        if (!search.isEmpty()) statement.param("q", query).param("number", number);
        return statement;
    }
}
