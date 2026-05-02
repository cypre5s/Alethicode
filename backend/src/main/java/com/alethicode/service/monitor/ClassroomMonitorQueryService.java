package com.alethicode.service.monitor;

import com.alethicode.dto.response.ClassroomErrorClusterItemResponse;
import com.alethicode.dto.response.ClassroomErrorClustersResponse;
import com.alethicode.dto.response.ClassroomMonitorStatsResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Lazy
public class ClassroomMonitorQueryService {

    private final JdbcTemplate jdbcTemplate;

    public ClassroomMonitorQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ClassroomMonitorStatsResponse queryStats(String classroomId) {
        String sql = """
                with students as (
                    select cm.user_id
                    from classroom_member cm
                    where cm.classroom_id = ? and cm.role = 'student'
                ),
                latest_snapshot as (
                    select s.user_id, s.activity_status, s.error_taxonomy, s.snapshot_time
                    from (
                        select s.user_id, s.activity_status, s.error_taxonomy, s.snapshot_time,
                               row_number() over (partition by s.user_id order by s.snapshot_time desc) as rn
                        from student_monitoring_snapshot s
                        join students st on st.user_id = s.user_id
                        where s.classroom_id = ?
                    ) s
                    where s.rn = 1
                ),
                classroom_problem_ids as (
                    select cp.problem_id
                    from classroom_problem cp
                    where cp.classroom_id = ?
                ),
                classroom_problem_count as (
                    select count(*) as cnt from classroom_problem_ids
                ),
                assignment_total as (
                    select count(*) as total
                    from classroom_assignment_problem ap
                    join classroom_assignment_section sec on sec.id = ap.section_id
                    join classroom_assignment a on a.id = sec.assignment_id
                    where a.classroom_id = ? and a.start_time <= now()
                ),
                assignment_completed as (
                    select s.user_id, count(distinct d.assignment_problem_id) as completed
                    from classroom_assignment_problem_submission d
                    join classroom_assignment_submission s on s.id = d.submission_id
                    join classroom_assignment_problem ap on ap.id = d.assignment_problem_id
                    join classroom_assignment_section sec on sec.id = ap.section_id
                    join classroom_assignment a on a.id = sec.assignment_id
                    where a.classroom_id = ? and a.start_time <= now() and d.judge_status = 'AC'
                    group by s.user_id
                ),
                submission_stats as (
                    select sub.user_id,
                           count(*) as total_submission,
                           count(distinct case when sub.result = 0 then sub.problem_id end) as ac_problem_count
                    from submission sub
                    join classroom_problem_ids cp on cp.problem_id = sub.problem_id
                    group by sub.user_id
                ),
                progress_by_user as (
                    select st.user_id,
                           case
                               when at.total > 0 and coalesce(ac.completed, 0) > 0
                                   then coalesce(ac.completed, 0) * 100.0 / at.total
                               when cpc.cnt = 0 then 0
                               when coalesce(ss.total_submission, 0) = 0 then 0
                               else coalesce(ss.ac_problem_count, 0) * 100.0 / cpc.cnt
                           end as progress
                    from students st
                    cross join assignment_total at
                    cross join classroom_problem_count cpc
                    left join assignment_completed ac on ac.user_id = st.user_id
                    left join submission_stats ss on ss.user_id = st.user_id
                )
                select
                    (select count(*) from students) as total_members,
                    (select count(*) from latest_snapshot ls where ls.snapshot_time >= now() - interval '30 second') as online_count,
                    (select count(*) from latest_snapshot ls where ls.snapshot_time >= now() - interval '30 second'
                        and ls.activity_status in ('typing', 'running')) as coding_count,
                    (select count(*) from latest_snapshot ls where ls.snapshot_time >= now() - interval '30 second'
                        and ls.activity_status = 'abnormal') as abnormal_count,
                    coalesce((select avg(progress) from progress_by_user), 0) as avg_progress
                """;
        Map<String, Object> row = jdbcTemplate.queryForMap(
                sql,
                classroomId,
                classroomId,
                classroomId,
                classroomId,
                classroomId
        );
        int totalMembers = intValue(row.get("total_members"));
        int onlineCount = intValue(row.get("online_count"));
        int codingCount = intValue(row.get("coding_count"));
        int abnormalCount = intValue(row.get("abnormal_count"));
        double avgProgress = doubleValue(row.get("avg_progress"));
        return new ClassroomMonitorStatsResponse(
                totalMembers,
                onlineCount,
                codingCount,
                codingCount,
                Math.max(totalMembers - codingCount, 0),
                codingCount,
                abnormalCount,
                avgProgress
        );
    }

    public List<MonitorSnapshotRow> querySnapshotRows(String classroomId) {
        String sql = """
                with students as (
                    select cm.user_id, u.username, coalesce(up.real_name, '') as real_name, cm.join_time
                    from classroom_member cm
                    join "user" u on u.id = cm.user_id
                    left join user_profile up on up.user_id = cm.user_id
                    where cm.classroom_id = ? and cm.role = 'student'
                ),
                latest_snapshot as (
                    select s.user_id, s.activity_status, s.error_taxonomy, s.snapshot_time, s.code_snapshot,
                           s.elapsed_time_seconds, s.submission_count, s.ac_count, s.classroom_problem_id
                    from (
                        select s.user_id, s.activity_status, s.error_taxonomy, s.snapshot_time, s.code_snapshot,
                               s.elapsed_time_seconds, s.submission_count, s.ac_count, s.classroom_problem_id,
                               row_number() over (partition by s.user_id order by s.snapshot_time desc) as rn
                        from student_monitoring_snapshot s
                        join students st on st.user_id = s.user_id
                        where s.classroom_id = ?
                    ) s
                    where s.rn = 1
                ),
                classroom_problem_ids as (
                    select cp.problem_id
                    from classroom_problem cp
                    where cp.classroom_id = ?
                ),
                classroom_problem_count as (
                    select count(*) as cnt from classroom_problem_ids
                ),
                submission_ranked as (
                    select sub.user_id, sub.code, sub.create_time, sub.result, sub.problem_id,
                           row_number() over (partition by sub.user_id order by sub.create_time desc) as rn
                    from submission sub
                    join classroom_problem_ids cp on cp.problem_id = sub.problem_id
                ),
                submission_stats as (
                    select sr.user_id,
                           count(*) as total_submission,
                           count(case when sr.result = 0 then 1 end) as ac_submission,
                           count(distinct case when sr.result = 0 then sr.problem_id end) as ac_problem_count,
                           max(case when sr.rn = 1 then sr.code end) as latest_code,
                           max(case when sr.rn = 1 then sr.create_time end) as latest_create_time
                    from submission_ranked sr
                    group by sr.user_id
                ),
                assignment_total as (
                    select count(*) as total
                    from classroom_assignment_problem ap
                    join classroom_assignment_section sec on sec.id = ap.section_id
                    join classroom_assignment a on a.id = sec.assignment_id
                    where a.classroom_id = ? and a.start_time <= now()
                ),
                assignment_completed as (
                    select s.user_id, count(distinct d.assignment_problem_id) as completed
                    from classroom_assignment_problem_submission d
                    join classroom_assignment_submission s on s.id = d.submission_id
                    join classroom_assignment_problem ap on ap.id = d.assignment_problem_id
                    join classroom_assignment_section sec on sec.id = ap.section_id
                    join classroom_assignment a on a.id = sec.assignment_id
                    where a.classroom_id = ? and a.start_time <= now() and d.judge_status = 'AC'
                    group by s.user_id
                ),
                progress_by_user as (
                    select st.user_id,
                           case
                               when at.total > 0 and coalesce(ac.completed, 0) > 0
                                   then coalesce(ac.completed, 0) * 100.0 / at.total
                               when cpc.cnt = 0 then 0
                               when coalesce(ss.total_submission, 0) = 0 then 0
                               else coalesce(ss.ac_problem_count, 0) * 100.0 / cpc.cnt
                           end as progress
                    from students st
                    cross join assignment_total at
                    cross join classroom_problem_count cpc
                    left join assignment_completed ac on ac.user_id = st.user_id
                    left join submission_stats ss on ss.user_id = st.user_id
                )
                select st.user_id, st.username, st.real_name,
                       case
                           when ls.snapshot_time is null or ls.snapshot_time < now() - interval '30 second'
                               then 'offline'
                           else coalesce(ls.activity_status, 'offline')
                       end as activity_status,
                       case
                           when ls.snapshot_time is null or ls.snapshot_time < now() - interval '30 second'
                               then null
                           else ls.error_taxonomy
                       end as error_taxonomy,
                       case
                           when ls.code_snapshot is not null and ls.code_snapshot <> ''
                               then length(ls.code_snapshot)
                           else length(coalesce(ss.latest_code, ''))
                       end as code_length,
                       coalesce(ls.snapshot_time, ss.latest_create_time) as last_activity,
                       round(coalesce(ls.elapsed_time_seconds, 0) / 60.0) as active_time,
                       coalesce(ls.submission_count, ss.total_submission, 0) as submission_count,
                       coalesce(ls.ac_count, ss.ac_submission, 0) as ac_count,
                       coalesce(pb.progress, 0) as progress,
                       p.id as current_problem_id,
                       p.title as current_problem_title
                from students st
                left join latest_snapshot ls on ls.user_id = st.user_id
                left join submission_stats ss on ss.user_id = st.user_id
                left join progress_by_user pb on pb.user_id = st.user_id
                left join classroom_problem cp on cp.id = ls.classroom_problem_id
                left join problem p on p.id = cp.problem_id
                order by st.join_time asc
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new MonitorSnapshotRow(
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        rs.getString("real_name"),
                        rs.getString("activity_status"),
                        rs.getString("error_taxonomy"),
                        rs.getInt("code_length"),
                        rs.getTimestamp("last_activity"),
                        rs.getLong("active_time"),
                        rs.getInt("submission_count"),
                        rs.getInt("ac_count"),
                        rs.getDouble("progress"),
                        rs.getObject("current_problem_id") == null ? null : rs.getLong("current_problem_id"),
                        rs.getString("current_problem_title")
                ),
                classroomId,
                classroomId,
                classroomId,
                classroomId,
                classroomId
        );
    }

    public ClassroomErrorClustersResponse queryErrorClusters(String classroomId, int minutes) {
        int boundedMinutes = Math.max(1, Math.min(minutes, 1440));
        Timestamp cutoff = Timestamp.from(Instant.now().minusSeconds(boundedMinutes * 60L));
        List<ClassroomErrorClusterItemResponse> clusters = jdbcTemplate.query(
                """
                select error_taxonomy, count(*) as total
                from student_monitoring_snapshot
                where classroom_id = ? and snapshot_time >= ?
                  and activity_status = 'abnormal' and error_taxonomy is not null
                group by error_taxonomy
                order by total desc
                """,
                (rs, rowNum) -> new ClassroomErrorClusterItemResponse(
                        rs.getString("error_taxonomy"),
                        rs.getLong("total")
                ),
                classroomId,
                cutoff
        );
        String hint = clusters.isEmpty() ? "近 " + boundedMinutes + " 分钟暂无可聚类的误区数据" : null;
        return new ClassroomErrorClustersResponse(clusters, hint);
    }

    private int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        return ((Number) value).intValue();
    }

    private double doubleValue(Object value) {
        if (value == null) {
            return 0;
        }
        return ((Number) value).doubleValue();
    }

    public record MonitorSnapshotRow(
            Long userId,
            String username,
            String realName,
            String activityStatus,
            String errorTaxonomy,
            int codeLength,
            Timestamp lastActivity,
            long activeTime,
            int submissionCount,
            int acCount,
            double progress,
            Long currentProblemId,
            String currentProblemTitle
    ) {
    }
}
