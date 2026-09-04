package de.kickbase.h2h;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
class H2hController {

  private final MatchResultRepository repository;
  private final KickbaseImporter importer;

  H2hController(
          MatchResultRepository repository,
          KickbaseImporter importer
  ) {
    this.repository = repository;
    this.importer = importer;
  }

  @PostMapping("/admin/import-kickbase")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void importKickbasePoints() {
    importer.importCurrentMatchday();
  }

  @GetMapping("/matchdays/{matchday}")
  List<FixtureDto> fixtures(@PathVariable int matchday) {
    return repository.findByMatchdayOrderById(matchday)
            .stream()
            .map(FixtureDto::from)
            .toList();
  }

  @GetMapping("/table")
  List<TableRowDto> table() {
    return buildTable(repository.findAllByOrderByMatchdayAscIdAsc());
  }

  /**
   * Returns all statistics required by the H2H information overlay.
   *
   * Only completed matchdays BEFORE the selected matchday are considered.
   */
  @GetMapping("/h2h/{matchday}")
  H2hDto h2h(
          @PathVariable int matchday,
          @RequestParam String homePlayer,
          @RequestParam String awayPlayer
  ) {

    List<MatchResult> allMatches =
            repository.findAllByOrderByMatchdayAscIdAsc();

    // Only completed matches before the selected matchday.
    List<MatchResult> completedMatches = allMatches.stream()
            .filter(m -> m.matchday < matchday)
            .filter(m -> m.homePoints != null && m.awayPoints != null)
            .toList();

    List<TableRowDto> currentTable = buildTable(completedMatches);

    Map<String, Integer> positions = new HashMap<>();

    for (int i = 0; i < currentTable.size(); i++) {
      positions.put(currentTable.get(i).player, i + 1);
    }

    PlayerStatsDto home = buildPlayerStats(
            homePlayer,
            completedMatches,
            positions.getOrDefault(homePlayer, 0)
    );

    PlayerStatsDto away = buildPlayerStats(
            awayPlayer,
            completedMatches,
            positions.getOrDefault(awayPlayer, 0)
    );

    PredictionDto prediction = calculatePrediction(home, away);

    return new H2hDto(
            matchday,
            homePlayer,
            awayPlayer,
            home,
            away,
            prediction
    );
  }

  @PutMapping("/matchdays/{matchday}/points")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void savePoints(
          @PathVariable int matchday,
          @RequestBody Map<String, Integer> points
  ) {
    for (MatchResult m : repository.findByMatchdayOrderById(matchday)) {
      m.homePoints = points.get(m.homePlayer);
      m.awayPoints = points.get(m.awayPlayer);
      repository.save(m);
    }
  }

  /**
   * Builds the league table from completed matches.
   */
  private List<TableRowDto> buildTable(List<MatchResult> matches) {

    Map<String, TableRowDto> table = new TreeMap<>();

    for (MatchResult m : matches) {

      table.computeIfAbsent(
              m.homePlayer,
              TableRowDto::new
      );

      table.computeIfAbsent(
              m.awayPlayer,
              TableRowDto::new
      );

      if (m.homePoints == null || m.awayPoints == null) {
        continue;
      }

      TableRowDto home = table.get(m.homePlayer);
      TableRowDto away = table.get(m.awayPlayer);

      home.played++;
      away.played++;

      if (m.homePoints > m.awayPoints) {
        home.wins++;
        away.losses++;
        home.points += 3;

      } else if (m.homePoints < m.awayPoints) {
        away.wins++;
        home.losses++;
        away.points += 3;

      } else {
        home.draws++;
        away.draws++;
        home.points++;
        away.points++;
      }
    }

    return table.values()
            .stream()
            .sorted(
                    Comparator
                            .comparingInt(TableRowDto::getPoints)
                            .reversed()
                            .thenComparing(
                                    Comparator.comparingInt(TableRowDto::getWins)
                                            .reversed()
                            )
                            .thenComparing(TableRowDto::getPlayer)
            )
            .toList();
  }

  /**
   * Calculates all player-specific statistics.
   */
  private PlayerStatsDto buildPlayerStats(
          String player,
          List<MatchResult> matches,
          int position
  ) {

    List<Integer> points = new ArrayList<>();

    List<FormResult> results = new ArrayList<>();

    for (MatchResult m : matches) {

      boolean isHome = m.homePlayer.equals(player);
      boolean isAway = m.awayPlayer.equals(player);

      if (!isHome && !isAway) {
        continue;
      }

      int playerPoints = isHome
              ? m.homePoints
              : m.awayPoints;

      int opponentPoints = isHome
              ? m.awayPoints
              : m.homePoints;

      points.add(playerPoints);

      String result;

      if (playerPoints > opponentPoints) {
        result = "W";
      } else if (playerPoints < opponentPoints) {
        result = "N";
      } else {
        result = "U";
      }

      results.add(new FormResult(m.matchday, result));
    }

    double average = points.isEmpty()
            ? 0
            : points.stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0);

    int min = points.isEmpty()
            ? 0
            : points.stream()
            .mapToInt(Integer::intValue)
            .min()
            .orElse(0);

    int max = points.isEmpty()
            ? 0
            : points.stream()
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0);

    List<FormResult> recentResults = results.stream()
            .sorted(Comparator.comparingInt(FormResult::matchday).reversed())
            .limit(5)
            .toList();

    List<String> form = recentResults.stream()
            .sorted(Comparator.comparingInt(FormResult::matchday))
            .map(FormResult::result)
            .toList();

    List<Integer> recentPoints = new ArrayList<>();

    // Re-read the player's points in the same last-5 order.
    List<MatchResult> recentMatches = matches.stream()
            .filter(m ->
                    m.homePlayer.equals(player)
                            || m.awayPlayer.equals(player)
            )
            .sorted(Comparator.comparingInt((MatchResult m) -> m.matchday).reversed())
            .limit(5)
            .toList();

    for (MatchResult m : recentMatches) {
      recentPoints.add(
              m.homePlayer.equals(player)
                      ? m.homePoints
                      : m.awayPoints
      );
    }

    double recentAverage = recentPoints.isEmpty()
            ? 0
            : recentPoints.stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0);

    return new PlayerStatsDto(
            player,
            position,
            round(average),
            round(recentAverage),
            min,
            max,
            form
    );
  }

  /**
   * Prediction model:
   *
   * 50% recent average
   * 35% season average
   * 15% league position
   *
   * The result is converted into a percentage and limited
   * to 10%-90% so that the prediction never becomes absurdly certain.
   */
  private PredictionDto calculatePrediction(
          PlayerStatsDto home,
          PlayerStatsDto away
  ) {

    if (
            home.average == 0
                    && away.average == 0
                    && home.recentAverage == 0
                    && away.recentAverage == 0
    ) {
      return new PredictionDto(50, 50);
    }

    double recentTotal =
            home.recentAverage + away.recentAverage;

    double seasonTotal =
            home.average + away.average;

    double homeRecentShare =
            recentTotal == 0
                    ? 0.5
                    : home.recentAverage / recentTotal;

    double awayRecentShare =
            recentTotal == 0
                    ? 0.5
                    : away.recentAverage / recentTotal;

    double homeSeasonShare =
            seasonTotal == 0
                    ? 0.5
                    : home.average / seasonTotal;

    double awaySeasonShare =
            seasonTotal == 0
                    ? 0.5
                    : away.average / seasonTotal;

    // 10 players in the league.
    double homePositionScore =
            positionScore(home.position);

    double awayPositionScore =
            positionScore(away.position);

    double positionTotal =
            homePositionScore + awayPositionScore;

    double homePositionShare =
            positionTotal == 0
                    ? 0.5
                    : homePositionScore / positionTotal;

    double awayPositionShare =
            positionTotal == 0
                    ? 0.5
                    : awayPositionScore / positionTotal;

    double homeStrength =
            (homeRecentShare * 0.50)
                    + (homeSeasonShare * 0.35)
                    + (homePositionShare * 0.15);

    double awayStrength =
            (awayRecentShare * 0.50)
                    + (awaySeasonShare * 0.35)
                    + (awayPositionShare * 0.15);

    double homeProbability =
            50 + ((homeStrength - awayStrength) * 100);

    int homePrediction =
            (int) Math.round(
                    Math.max(10, Math.min(90, homeProbability))
            );

    int awayPrediction = 100 - homePrediction;

    return new PredictionDto(
            homePrediction,
            awayPrediction
    );
  }

  private double positionScore(int position) {
    if (position <= 0) {
      return 0;
    }

    // 1st = 10 points, 2nd = 9, ..., 10th = 1.
    return Math.max(1, 11 - position);
  }

  private double round(double value) {
    return Math.round(value * 10.0) / 10.0;
  }

  record FixtureDto(
          int matchday,
          String homePlayer,
          String awayPlayer,
          Integer homePoints,
          Integer awayPoints
  ) {
    static FixtureDto from(MatchResult m) {
      return new FixtureDto(
              m.matchday,
              m.homePlayer,
              m.awayPlayer,
              m.homePoints,
              m.awayPoints
      );
    }
  }

  record H2hDto(
          int matchday,
          String homePlayer,
          String awayPlayer,
          PlayerStatsDto home,
          PlayerStatsDto away,
          PredictionDto prediction
  ) {}

  record PlayerStatsDto(
          String player,
          int position,
          double average,
          double recentAverage,
          int min,
          int max,
          List<String> form
  ) {}

  record PredictionDto(
          int home,
          int away
  ) {}

  record FormResult(
          int matchday,
          String result
  ) {}

  public static class TableRowDto {

    final String player;
    int played;
    int wins;
    int draws;
    int losses;
    int points;

    TableRowDto(String player) {
      this.player = player;
    }

    public String getPlayer() {
      return player;
    }

    public int getPlayed() {
      return played;
    }

    public int getWins() {
      return wins;
    }

    public int getDraws() {
      return draws;
    }

    public int getLosses() {
      return losses;
    }

    public int getPoints() {
      return points;
    }
  }
}