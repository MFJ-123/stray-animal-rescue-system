package cn.edu.sar.domain.access;

import java.util.List;
import cn.edu.sar.domain.access.AccessModels.Responsibility;

/** B/C/E implement TASK, CARE, FOLLOWUP providers. Query inside caller's transaction after the volunteer account lock. */
public interface ResponsibilityProvider {
    String module();
    List<Responsibility> findOpen(long userId);
}
