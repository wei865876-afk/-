package com.school.student_manage.service.impl;

import com.school.student_manage.entity.Dict;
import com.school.student_manage.mapper.DictMapper;
import com.school.student_manage.service.DictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DictServiceImpl implements DictService {
    @Autowired
    private DictMapper mapper;

    public List<Dict> listAll() { return mapper.listAll(); }
    public boolean add(Dict d) { return mapper.insert(d) > 0; }
    public boolean update(Dict d) { return mapper.update(d) > 0; }
    public boolean delete(Integer id) { return mapper.deleteById(id) > 0; }
}