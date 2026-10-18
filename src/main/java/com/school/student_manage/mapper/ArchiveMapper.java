package com.school.student_manage.mapper;

import com.school.student_manage.entity.Archive;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ArchiveMapper {
    List<Archive> listAll();
    int insert(Archive archive);
    int update(Archive archive);
    int deleteById(Integer id);
}