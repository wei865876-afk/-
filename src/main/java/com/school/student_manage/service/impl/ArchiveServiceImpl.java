package com.school.student_manage.service.impl;

import com.school.student_manage.entity.Archive;
import com.school.student_manage.mapper.ArchiveMapper;
import com.school.student_manage.service.ArchiveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ArchiveServiceImpl implements ArchiveService {
    @Autowired
    private ArchiveMapper archiveMapper;

    public List<Archive> listAll() { return archiveMapper.listAll(); }
    public boolean add(Archive a) { return archiveMapper.insert(a) > 0; }
    public boolean update(Archive a) { return archiveMapper.update(a) > 0; }
    public boolean delete(Integer id) { return archiveMapper.deleteById(id) > 0; }
}