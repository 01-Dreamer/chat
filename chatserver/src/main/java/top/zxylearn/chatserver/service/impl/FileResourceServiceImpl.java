package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.FileResource;
import top.zxylearn.chatserver.mapper.FileResourceMapper;
import top.zxylearn.chatserver.service.FileResourceService;

@Service
public class FileResourceServiceImpl
        extends ServiceImpl<FileResourceMapper, FileResource>
        implements FileResourceService {
}
