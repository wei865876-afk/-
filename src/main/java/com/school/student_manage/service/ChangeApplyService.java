package com.school.student_manage.service;

import com.school.student_manage.entity.ChangeApply;
import java.util.List;

public interface ChangeApplyService {
    List<ChangeApply> listAll();
    List<ChangeApply> listByStuId(String stuId);
    List<ChangeApply> listPending();
    boolean add(ChangeApply apply);
    boolean audit(Integer applyId, String status);
    boolean delete(Integer applyId);
}