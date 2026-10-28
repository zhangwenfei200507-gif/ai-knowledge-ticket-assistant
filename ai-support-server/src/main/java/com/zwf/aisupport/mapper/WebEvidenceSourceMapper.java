package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.WebEvidenceSource;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WebEvidenceSourceMapper {

    @Insert("""
        insert into web_evidence_sources (
            evidence_id, title, url, domain, excerpt, credibility_score, source_rank,
            source_type, official_source, published_time, retrieved_time, source_status
        ) values (
            #{evidenceId}, #{title}, #{url}, #{domain}, #{excerpt}, #{credibilityScore}, #{sourceRank},
            #{sourceType}, #{officialSource}, #{publishedTime}, #{retrievedTime}, #{sourceStatus}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(WebEvidenceSource source);

    @Select("""
        select * from web_evidence_sources
        where evidence_id = #{evidenceId}
        order by source_rank, id
        """)
    List<WebEvidenceSource> findByEvidenceId(Long evidenceId);

    @Select("""
        <script>
        select * from web_evidence_sources
        where evidence_id in
        <foreach collection="evidenceIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
        order by evidence_id, source_rank, id
        </script>
        """)
    List<WebEvidenceSource> findByEvidenceIds(@Param("evidenceIds") List<Long> evidenceIds);
}
