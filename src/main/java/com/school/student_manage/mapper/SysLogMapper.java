package com.school.student_manage.mapper;

import com.school.student_manage.entity.SysLog;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface SysLogMapper {
    List<SysLog> listAll();
    int insert(SysLog log);
}