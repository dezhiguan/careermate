-- 订正存量僵尸 agent_run。
--
-- fork 出来的 run 只复制快照、不启动执行，要等外部 resume 才推进，代码此前却把它置成
-- RUNNING（已在 CheckpointedAgentEngine 改为 PAUSED）。存量数据仍留着若干条自 7 月起
-- status=RUNNING、finished_at 为空、而快照里 currentStep 早已是终态的记录，列表上一直显示
-- 「运行中」，既误导用户也干扰排查。
--
-- 两类分别处理，避免误伤真正在跑的 run：
--   1) 有终态快照的 → DONE，并用最后一次快照时间回填 finished_at
--   2) 无终态快照但已超过 24 小时没有任何进展的 → PAUSED（等待 resume，语义与新逻辑一致）
-- 24 小时远大于任何一次正常运行的时长（正常在秒级完成或立即 PAUSED）。

UPDATE agent_run r
SET status = 'DONE',
    finished_at = COALESCE(r.finished_at, latest.created_at, now())
FROM (
    SELECT DISTINCT ON (c.run_id) c.run_id, c.step_name, c.created_at
    FROM agent_checkpoint c
    ORDER BY c.run_id, c.created_at DESC
) AS latest
WHERE r.run_id = latest.run_id
  AND r.status = 'RUNNING'
  AND r.finished_at IS NULL
  AND latest.step_name = 'done';

UPDATE agent_run
SET status = 'PAUSED'
WHERE status = 'RUNNING'
  AND finished_at IS NULL
  AND started_at < now() - INTERVAL '24 hours';
