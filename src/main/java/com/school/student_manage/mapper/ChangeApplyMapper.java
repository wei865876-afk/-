package com.school.student_manage.mapper;

import com.school.student_manage.entity.ChangeApply;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ChangeApplyMapper {
    List<ChangeApply> listAll();                // 所有申请（学生查看自己的可加 stuId 参数）
    List<ChangeApply> listByStuId(String stuId);
    List<ChangeApply> listPending();            // 待审核列表（管理员用）
    int insert(ChangeApply apply);
    int updateStatus(Integer applyId, String status);
    int deleteById(Integer applyId);
}