package com.samuel.app.platform.repository;

import com.samuel.app.platform.adapter.PlatformType;
import com.samuel.app.platform.model.PlatformConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformConnectionRepository extends JpaRepository<PlatformConnection, String> {

    Optional<PlatformConnection> findByCreatorProfileIdAndPlatformType(String creatorProfileId, PlatformType platformType);

    Optional<PlatformConnection> findByPlatformUserIdAndPlatformType(String platformUserId, PlatformType platformType);

    List<PlatformConnection> findByCreatorProfileId(String creatorProfileId);
}