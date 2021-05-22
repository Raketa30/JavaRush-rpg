package com.game.service;

import com.game.controller.PlayerOrder;
import com.game.entity.Player;
import com.game.repository.PlayerRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlayerService {
    private EntityManagerFactory entityManagerFactory;
    private PlayerRepo playerRepo;

    @Autowired
    public PlayerService(PlayerRepo playerRepo, EntityManagerFactory entityManagerFactory) {
        this.playerRepo = playerRepo;
        this.entityManagerFactory = entityManagerFactory;
    }

    public List<Player> showSortedList(HttpServletRequest request) {
        int pageNumber = request.getParameter("pageNumber") != null
                ? Integer.parseInt(request.getParameter("pageNumber")) : 0;

        int pageSize = request.getParameter("pageSize") != null
                ? Integer.parseInt(request.getParameter("pageSize")) : 3;

        PlayerOrder order = request.getParameter("order") != null
                ? PlayerOrder.valueOf(request.getParameter("order")) : PlayerOrder.ID;

        List<Player> players = getPlayersFromQuery(request);

        sortPlayerList(players, order);
        return showByPages(players, pageNumber, pageSize);
    }

    public Integer getFilteredPlayersCount(HttpServletRequest request) {
        return getPlayersFromQuery(request).size();
    }

    private List<Player> showByPages(List<Player> players, Integer pageNumber, Integer pageSize) {
        int skip = (pageNumber + 1) * pageSize - pageSize;
        return players.stream().skip(skip).limit(pageSize).collect(Collectors.toList());
    }

    public boolean deletePlayerById(String id) {
        long playerId = Long.parseLong(id);
        if (playerExistById(playerId)) {
            playerRepo.deletePlayersById(playerId);
            return !playerExistById(playerId);
        }
        return false;
    }

    public Optional<Player> findPlayerById(String id) {
        long playerId = Long.parseLong(id);
        return playerRepo.findPlayerById(playerId);
    }

    public boolean playerExistById(Long id) {
        return playerRepo.existsPlayerById(id);
    }

    public boolean validatePlayer(Player player) {
        if (player.getName() == null || player.getName().length() > 12
                || player.getTitle() == null || player.getTitle().length() > 30
                || player.getRace() == null
                || player.getProfession() == null
                || player.getBirthday() == null
                || player.getExperience() == null || player.getExperience() > 10_000_000
                || player.getBirthday().getTime() < 0
        ) {
            return false;
        }

        setCurrentLevel(player);

        playerRepo.save(player);
        return true;
    }

    public ResponseEntity<Player> updateExistPlayerById(String id, Player incomingPlayer) {
        Optional<Player> optionalPlayer = findPlayerById(id);

        if (optionalPlayer.isPresent()) {
            Player playerForUpdate = optionalPlayer.get();
            if (updateValidTitile(incomingPlayer, playerForUpdate)) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            if (updatePlayerExperience(incomingPlayer, playerForUpdate)) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            if (updateValidName(incomingPlayer, playerForUpdate)) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            if (updateValidBirthday(incomingPlayer, playerForUpdate)) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            updateValidRace(incomingPlayer, playerForUpdate);
            updateValidProfession(incomingPlayer, playerForUpdate);

            updatePlayerBan(incomingPlayer, playerForUpdate);

            setCurrentLevel(playerForUpdate);
            playerRepo.saveAndFlush(playerForUpdate);

            return new ResponseEntity<>(playerForUpdate, HttpStatus.OK);

        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    private boolean updatePlayerExperience(Player incomingPlayer, Player playerForUpdate) {
        if (incomingPlayer.getExperience() != null) {
            if (incomingPlayer.getExperience() > 10_000_000 || incomingPlayer.getExperience() < 0) {
                return true;
            }
            playerForUpdate.setExperience(incomingPlayer.getExperience());
        }
        return false;
    }

    private void updatePlayerBan(Player incomingPlayer, Player playerForUpdate) {
        if (incomingPlayer.getBanned() != null) {
            playerForUpdate.setBanned(incomingPlayer.getBanned());
        }
    }

    private boolean updateValidBirthday(Player incomingPlayer, Player playerForUpdate) {
        if (incomingPlayer.getBirthday() != null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(incomingPlayer.getBirthday());

            if (calendar.get(Calendar.YEAR) < 2000 || calendar.get(Calendar.YEAR) > 3000) {
                return true;
            }
            playerForUpdate.setBirthday(incomingPlayer.getBirthday());
        }
        return false;
    }

    private void updateValidProfession(Player incomingPlayer, Player playerForUpdate) {
        if (incomingPlayer.getProfession() != null) {
            playerForUpdate.setProfession(incomingPlayer.getProfession());
        }
    }

    private void updateValidRace(Player incomingPlayer, Player playerForUpdate) {
        if (incomingPlayer.getRace() != null) {
            playerForUpdate.setRace(incomingPlayer.getRace());
        }
    }

    private boolean updateValidTitile(Player incomingPlayer, Player playerForUpdate) {
        if (incomingPlayer.getTitle() != null) {
            if (incomingPlayer.getTitle().length() > 30) {
                return true;
            }
            playerForUpdate.setTitle(incomingPlayer.getTitle());
        }
        return false;
    }

    private boolean updateValidName(Player incomingPlayer, Player playerForUpdate) {
        if (incomingPlayer.getName() != null) {
            if (incomingPlayer.getName().length() > 12) {
                return true;
            }
            playerForUpdate.setName(incomingPlayer.getName());
        }
        return false;
    }

    private void sortPlayerList(List<Player> players, PlayerOrder order) {
        if (order != null) {
            players.sort((p1, p2) -> {
                switch (order) {
                    case ID:
                        return p1.getId().compareTo(p2.getId());
                    case NAME:
                        return p1.getName().compareTo(p2.getName());
                    case EXPERIENCE:
                        return p1.getExperience().compareTo(p2.getExperience());
                    case BIRTHDAY:
                        return p1.getBirthday().compareTo(p2.getBirthday());
                    case LEVEL:
                        return p1.getLevel().compareTo(p2.getLevel());
                    default:
                        return 0;
                }
            });
        }
    }

    private List<Player> getPlayersFromQuery(HttpServletRequest request) {
        EntityManager em = entityManagerFactory.createEntityManager();
        List<Player> players = new ArrayList<>();

        try {
            Query query = em.createNativeQuery(queryBuilderByPlayerData(request), Player.class);
            if (request.getParameter("name") != null) {
                query.setParameter("name", "%" + request.getParameter("name") + "%");
            }
            if (request.getParameter("title") != null) {
                query.setParameter("title", "%" + request.getParameter("title") + "%");
            }
            if (request.getParameter("race") != null) {
                query.setParameter("race", request.getParameter("race"));
            }
            if (request.getParameter("banned") != null) {
                if (request.getParameter("banned").equals("true")) {
                    query.setParameter("banned", "1");
                } else {
                    query.setParameter("banned", "0");
                }
            }
            if (request.getParameter("profession") != null) {
                query.setParameter("profession", request.getParameter("profession"));
            }
            if (request.getParameter("before") != null) {
                String dateText = getStringDateFormat(request.getParameter("before"));
                query.setParameter("before", dateText);
            }
            if (request.getParameter("after") != null) {
                String dateText = getStringDateFormat(request.getParameter("after"));
                query.setParameter("after", dateText);
            }
            if (request.getParameter("minExperience") != null) {
                query.setParameter("minExperience", request.getParameter("minExperience"));
            }
            if (request.getParameter("maxExperience") != null) {
                query.setParameter("maxExperience", request.getParameter("maxExperience"));
            }
            if (request.getParameter("minLevel") != null) {
                query.setParameter("minLevel", request.getParameter("minLevel"));
            }
            if (request.getParameter("maxLevel") != null) {
                query.setParameter("maxLevel", request.getParameter("maxLevel"));
            }

            players.addAll(query.getResultList());
        } finally {
            em.close();
        }

        return players;
    }

    private String getStringDateFormat(String dataFromRequest) {
        long dateLong = Long.parseLong(dataFromRequest);
        Date date = new Date(dateLong);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return dateFormat.format(date);
    }

    private String queryBuilderByPlayerData(HttpServletRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append(" SELECT * FROM player WHERE 1=1 ");

        if (request.getParameter("name") != null) {
            builder.append("AND name LIKE ").append(":name ");
        }
        if (request.getParameter("title") != null) {
            builder.append("AND title LIKE ").append(":title ");
        }
        if (request.getParameter("race") != null) {
            builder.append("AND race = ").append(":race ");
        }
        if (request.getParameter("banned") != null) {
            builder.append("AND banned = :banned ");
        }
        if (request.getParameter("profession") != null) {
            builder.append("AND profession = ").append(":profession ");
        }
        if (request.getParameter("before") != null) {
            builder.append("AND birthday <= ").append(":before ");
        }
        if (request.getParameter("after") != null) {
            builder.append("AND birthday >= ").append(":after ");
        }
        if (request.getParameter("minExperience") != null) {
            builder.append("AND experience >= ").append(":minExperience ");
        }
        if (request.getParameter("maxExperience") != null) {
            builder.append("AND experience <= ").append(":maxExperience ");
        }
        if (request.getParameter("minLevel") != null) {
            builder.append("AND level >= ").append(":minLevel ");
        }
        if (request.getParameter("maxLevel") != null) {
            builder.append("AND level <= ").append(":maxLevel ");
        }
        return builder.toString();
    }

    private void setCurrentLevel(Player player) {
        int currentLevel = (int) ((Math.sqrt(2500 + 200 * player.getExperience()) - 50) / 100);
        player.setLevel(currentLevel);

        int expUntilNextLevel = 50 * (currentLevel + 1) * (currentLevel + 2) - player.getExperience();
        player.setUntilNextLevel(expUntilNextLevel);
    }


}
