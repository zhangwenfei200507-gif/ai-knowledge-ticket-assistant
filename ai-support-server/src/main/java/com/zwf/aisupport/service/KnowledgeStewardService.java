package com.zwf.aisupport.service;

import com.zwf.aisupport.vo.KnowledgeArticleVO;
import com.zwf.aisupport.vo.KnowledgeStewardRunVO;
import com.zwf.aisupport.vo.KnowledgeStewardTaskVO;
import com.zwf.aisupport.vo.PageVO;

public interface KnowledgeStewardService {

    KnowledgeStewardRunVO run(Integer limit);

    PageVO<KnowledgeStewardTaskVO> listTasks(String keyword,
                                             String status,
                                             String sourceType,
                                             String targetDomain,
                                             Integer page,
                                             Integer size);

    KnowledgeStewardTaskVO updateTask(Long id, KnowledgeStewardTaskVO updateVO);

    KnowledgeArticleVO approve(Long id);

    void reject(Long id);

    void archive(Long id);
}
