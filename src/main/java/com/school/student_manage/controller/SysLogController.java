package com.school.student_manage.controller;

import com.school.student_manage.entity.SysLog;
import com.school.student_manage.service.SysLogService;
import com.school.student_manage.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/log")
public class SysLogController {
    @Autowired
    private SysLogService sysLogService;

    @GetMapping("/list")
    public R list() {
        List<SysLog> list = sysLogService.listAll();
        return R.success(list);
    }
}