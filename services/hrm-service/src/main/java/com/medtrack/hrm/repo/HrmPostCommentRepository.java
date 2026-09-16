package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.HrmPostCommentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HrmPostCommentRepository extends JpaRepository<HrmPostCommentEntity, String> {
  List<HrmPostCommentEntity> findByPostIdOrderByCreationDateAsc(String postId);

  List<HrmPostCommentEntity> findByPostIdInOrderByCreationDateAsc(List<String> postIds);

  long countByPostId(String postId);
}
