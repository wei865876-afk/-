package com.school.student_manage.service;

import com.school.student_manage.entity.Dict;
import java.util.List;

public interface DictService {
    List<Dict> listAll();
    boolean add(Dict dict);
    boolean update(Dict dict);
    boolean delete(Integer id);
}