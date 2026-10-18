package com.school.student_manage.service;

import com.school.student_manage.entity.Archive;
import java.util.List;

public interface ArchiveService {
    List<Archive> listAll();
    boolean add(Archive archive);
    boolean update(Archive archive);
    boolean delete(Integer id);
}