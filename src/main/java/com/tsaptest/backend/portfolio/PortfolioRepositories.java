package com.tsaptest.backend.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByUserIdOrderByDisplayOrder(Long userId);

    long countByUserId(Long userId);

    void deleteByUserId(Long userId);
}

interface HoldingRepository extends JpaRepository<Holding, Long> {
    List<Holding> findByUserIdOrderByDisplayOrder(Long userId);

    void deleteByUserId(Long userId);
}

interface ActivityEntryRepository extends JpaRepository<ActivityEntry, Long> {
    List<ActivityEntry> findByUserIdOrderByDisplayOrder(Long userId);

    void deleteByUserId(Long userId);
}

interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {
    Optional<PortfolioSnapshot> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
