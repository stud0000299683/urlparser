package com.utmn.chamortsev.urlparser.repository;

import com.utmn.chamortsev.urlparser.entity.UrlEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UrlRepository extends JpaRepository<UrlEntity, Long> {
    Optional<UrlEntity> findByUrl(String url);
    List<UrlEntity> findAllByOrderByCreatedAtDesc();
    List<UrlEntity> findByActiveTrueOrderByCreatedAtDesc();
    boolean existsByUrl(String url);

    @Query("SELECT COUNT(*) FROM UrlEntity  WHERE active = true")
    long countActiveUrls();
}