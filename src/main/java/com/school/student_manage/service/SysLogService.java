package com.school.student_manage.service;

import com.school.student_manage.entity.SysLog;
import java.util.List;

public interface SysLogService {
    List<SysLog> listAll();
    void addLog(String username, String operDesc, String ip);
}