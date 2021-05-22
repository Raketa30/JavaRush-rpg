package com.game.repository;

import com.game.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PlayerRepo extends JpaRepository<Player, Long> {
    @Transactional
    void deletePlayersById(Long id);

    Optional<Player> findPlayerById(Long id);

    boolean existsPlayerById(Long id);

}
