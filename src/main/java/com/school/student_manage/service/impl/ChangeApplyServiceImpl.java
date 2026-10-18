package com.school.student_manage.service.impl;

import com.school.student_manage.entity.ChangeApply;
import com.school.student_manage.mapper.ChangeApplyMapper;
import com.school.student_manage.service.ChangeApplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ChangeApplyServiceImpl implements ChangeApplyService {
    @Autowired
    private ChangeApplyMapper mapper;

    public List<ChangeApply> listAll() { return mapper.listAll(); }
    public List<ChangeApply> listByStuId(String stuId) { return mapper.listByStuId(stuId); }
    public List<ChangeApply> listPending() { return mapper.listPending(); }
    public boolean add(ChangeApply a) { return mapper.insert(a) > 0; }
    public boolean audit(Integer applyId, String status) { return mapper.updateStatus(applyId, status) > 0; }
    public boolean delete(Integer applyId) { return mapper.deleteById(applyId) > 0; }
}