package com.game.controller;

import com.game.entity.Player;
import com.game.service.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/rest/players")
public class PlayerController {
    private PlayerService playerService;

    @Autowired
    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping
    public List<Player> getAllByRequestParam(HttpServletRequest servletRequest) {
        return playerService.showSortedList(servletRequest);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Player> getPlayerById(@PathVariable("id") String id) {
        if (verifyId(id)) {
            Optional<Player> playerOptional = playerService.findPlayerById(id);
            return playerOptional
                    .map(player -> new ResponseEntity<>(player, HttpStatus.OK))
                    .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
        }
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/count")
    public ResponseEntity<Integer> getPlayersCount(HttpServletRequest httpServletRequest) {
        return new ResponseEntity<>(playerService.getFilteredPlayersCount(httpServletRequest), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<Player> createNewPlayer(@RequestBody Player player) {
        if (playerService.validatePlayer(player)) {
            return new ResponseEntity<>(player, HttpStatus.OK);
        }
        return new ResponseEntity<>(player, HttpStatus.BAD_REQUEST);
    }

    @PostMapping("/{id}")
    public ResponseEntity<Player> updateExistPlayer(@PathVariable("id") String id, @RequestBody Player player) {
        if(verifyId(id)) {
           return playerService.updateExistPlayerById(id, player);
        }
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deletePlayerById(@PathVariable("id") String id) {
        if (verifyId(id)) {
            if (playerService.deletePlayerById(id)) {
                return new ResponseEntity<>(id, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(id, HttpStatus.NOT_FOUND);
            }
        }
        return new ResponseEntity<>(id, HttpStatus.BAD_REQUEST);
    }

    private boolean verifyId(String id) {
        return id.matches("^[1-9]\\d*$");
    }

}
