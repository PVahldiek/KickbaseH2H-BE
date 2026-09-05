package de.kickbase.h2h;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
class KickbaseImporter {

  private static final Logger log =
          LoggerFactory.getLogger(KickbaseImporter.class);

  private final KickbaseProperties properties;
  private final MatchResultRepository repository;

  KickbaseImporter(
          KickbaseProperties properties,
          MatchResultRepository repository
  ) {
    this.properties = properties;
    this.repository = repository;
  }

  /**
   * Existing scheduled import.
   * Imports only the current matchday.
   */
  @Scheduled(
          cron = "${kickbase.import-cron}",
          zone = "Europe/Berlin"
  )
  public void importCurrentMatchday() {

    if (!isConfigured()) {
      return;
    }

    importMatchday(null);
  }

  /**
   * Imports ALL matchdays.
   *
   * Used manually via:
   *
   * POST /api/admin/import-kickbase-all
   */
  public void importAllMatchdays() {

    if (!isConfigured()) {
      log.warn("Kickbase importer is not configured.");
      return;
    }

    log.info("Starting Kickbase import for all matchdays...");

    /*
     * Bundesliga normally has 34 matchdays.
     *
     * We deliberately try 1..34 instead of relying on the current
     * matchday returned by Kickbase.
     */
    for (int matchday = 1; matchday <= 34; matchday++) {

      try {

        log.info(
                "Importing Kickbase matchday {}/34...",
                matchday
        );

        importMatchday(matchday);

      } catch (Exception e) {

        /*
         * Don't abort the entire import if one matchday fails.
         */
        log.error(
                "Error importing Kickbase matchday {}",
                matchday,
                e
        );
      }
    }

    log.info("Kickbase import for all matchdays finished.");
  }

  /**
   * Imports one matchday.
   *
   * @param matchday null = let Kickbase return the current matchday
   *                 otherwise explicitly request the given matchday
   */
  private void importMatchday(Integer matchday) {

    RestClient client =
            RestClient
                    .builder()
                    .baseUrl(properties.getCommunityApiUrl())
                    .build();

    KickbaseRanking response;

    if (matchday == null) {

      /*
       * Existing behavior:
       * GET /v4/leagues/{leagueId}/ranking
       */
      response =
              client
                      .get()
                      .uri(
                              "/v4/leagues/{leagueId}/ranking",
                              properties.getLeagueId()
                      )
                      .header(
                              "Accept",
                              "application/json"
                      )
                      .header(
                              "Authorization",
                              "Bearer " + properties.getAccessToken()
                      )
                      .retrieve()
                      .body(KickbaseRanking.class);

    } else {

      /*
       * Import a specific matchday:
       *
       * GET /v4/leagues/{leagueId}/ranking?day=1
       * GET /v4/leagues/{leagueId}/ranking?day=2
       * ...
       */
      response =
              client
                      .get()
                      .uri(
                              uriBuilder ->
                                      uriBuilder
                                              .path(
                                                      "/v4/leagues/{leagueId}/ranking"
                                              )
                                              .queryParam(
                                                      "dayNumber",
                                                      matchday
                                              )
                                              .build(
                                                      properties.getLeagueId()
                                              )
                      )
                      .header(
                              "Accept",
                              "application/json"
                      )
                      .header(
                              "Authorization",
                              "Bearer " + properties.getAccessToken()
                      )
                      .retrieve()
                      .body(KickbaseRanking.class);
    }

    if (
            response == null
                    || response.day() == null
                    || response.users() == null
    ) {

      log.warn(
              "No Kickbase ranking data returned for matchday {}",
              matchday
      );

      return;
    }

      if (matchday != null && response.day() != matchday) {
          log.info(
                  "Matchday {} is not available yet. Kickbase returned day {}. Skipping.",
                  matchday,
                  response.day()
          );

          return;
      }

    int actualMatchday = response.day();

    log.info(
            "Kickbase returned matchday {} with {} users",
            actualMatchday,
            response.users().size()
    );

    Map<String, Integer> pointsByApiName =
            response.users()
                    .stream()
                    .collect(
                            Collectors.toMap(
                                    user ->
                                            normalizeName(
                                                    user.name()
                                            ),
                                    KickbaseManager::matchdayPoints,
                                    (first, ignored) -> first
                            )
                    );

    List<MatchResult> matches =
            repository.findByMatchdayOrderById(
                    actualMatchday
            );

    log.info(
            "Found {} H2H matches for matchday {}",
            matches.size(),
            actualMatchday
    );

    for (MatchResult match : matches) {

      String homeApiName =
              resolveApiName(
                      match.homePlayer
              );

      String awayApiName =
              resolveApiName(
                      match.awayPlayer
              );

      Integer home =
              pointsByApiName.get(
                      normalizeName(
                              homeApiName
                      )
              );

      Integer away =
              pointsByApiName.get(
                      normalizeName(
                              awayApiName
                      )
              );

      if (home == null || away == null) {

        log.warn(
                "No Kickbase points found for {} ({}) vs {} ({}) on matchday {}",
                match.homePlayer,
                homeApiName,
                match.awayPlayer,
                awayApiName,
                actualMatchday
        );

        continue;
      }

      match.homePoints = home;
      match.awayPoints = away;

      repository.save(match);
    }
  }

  private boolean isConfigured() {

    return !properties.getCommunityApiUrl().isBlank()
            && !properties.getLeagueId().isBlank()
            && !properties.getAccessToken().isBlank();
  }

  private String resolveApiName(String h2hName) {

    return properties
            .getPlayerAliases()
            .entrySet()
            .stream()
            .filter(
                    entry ->
                            normalizeName(
                                    entry.getKey()
                            )
                                    .equals(
                                            normalizeName(
                                                    h2hName
                                            )
                                    )
            )
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(h2hName);
  }

  private String normalizeName(String value) {

    return Normalizer
            .normalize(
                    value,
                    Normalizer.Form.NFC
            )
            .replaceAll(
                    "[\\s\\p{Z}]+",
                    " "
            )
            .trim()
            .toLowerCase(Locale.ROOT);
  }

  record KickbaseRanking(
          Integer day,
          List<KickbaseManager> us
  ) {

    List<KickbaseManager> users() {
      return us;
    }
  }

  record KickbaseManager(
          String n,
          Integer mdp
  ) {

    String name() {
      return n;
    }

    Integer matchdayPoints() {
      return mdp;
    }
  }
}