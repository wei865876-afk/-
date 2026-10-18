package com.school.student_manage.service.impl;

import com.school.student_manage.entity.SysLog;
import com.school.student_manage.mapper.SysLogMapper;
import com.school.student_manage.service.SysLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SysLogServiceImpl implements SysLogService {
    @Autowired
    private SysLogMapper mapper;

    public List<SysLog> listAll() { return mapper.listAll(); }
    public void addLog(String username, String operDesc, String ip) {
        SysLog log = new SysLog();
        log.setUsername(username);
        log.setOperDesc(operDesc);
        log.setIp(ip);
        mapper.insert(log);
    }
}