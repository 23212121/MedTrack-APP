package com.medtrack.hrm.repo;

import com.medtrack.hrm.domain.HrmPostLikeEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface HrmPostLikeRepository extends JpaRepository<HrmPostLikeEntity, String> {
  Optional<HrmPostLikeEntity> findByPostIdAndUserId(String postId, String userId);

  boolean existsByPostIdAndUserId(String postId, String userId);

  long countByPostId(String postId);

  List<HrmPostLikeEntity> findByPostIdIn(List<String> postIds);

  @Modifying(clearAutomatically = true)
  void deleteByPostIdAndUserId(String postId, String userId);
}
