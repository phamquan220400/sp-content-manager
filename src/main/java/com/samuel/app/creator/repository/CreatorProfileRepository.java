package com.samuel.app.creator.repository;

import com.samuel.app.creator.model.CreatorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, String> {
    
    Optional<CreatorProfile> findByUserId(String userId);
    
    boolean existsByUserId(String userId);
}