package com.school.student_manage.mapper;

import com.school.student_manage.entity.Dict;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface DictMapper {
    List<Dict> listAll();
    int insert(Dict dict);
    int update(Dict dict);
    int deleteById(Integer id);
}