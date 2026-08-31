package de.kickbase.h2h;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
class KickbaseImporter {
  private static final Logger log = LoggerFactory.getLogger(KickbaseImporter.class);
  private final KickbaseProperties properties;
  private final MatchResultRepository repository;

  KickbaseImporter(KickbaseProperties properties, MatchResultRepository repository) {
    this.properties = properties;
    this.repository = repository;
  }

  @Scheduled(cron = "${kickbase.import-cron}", zone = "Europe/Berlin")
  public void importCurrentMatchday() {
    if (properties.getCommunityApiUrl().isBlank() || properties.getLeagueId().isBlank() || properties.getAccessToken().isBlank()) return;
    KickbaseRanking response = RestClient.builder().baseUrl(properties.getCommunityApiUrl()).build().get()
      .uri("/v4/leagues/{leagueId}/ranking", properties.getLeagueId())
      .header("Accept", "application/json")
      .header("Authorization", "Bearer " + properties.getAccessToken())
      .retrieve().body(KickbaseRanking.class);
    if (response == null || response.day() == null || response.users() == null) return;

    Map<String, Integer> pointsByApiName = response.users().stream()
      .collect(java.util.stream.Collectors.toMap(user -> normalizeName(user.name()), KickbaseManager::matchdayPoints, (first, ignored) -> first));
    for (MatchResult match : repository.findByMatchdayOrderById(response.day())) {
      String homeApiName = resolveApiName(match.homePlayer);
      String awayApiName = resolveApiName(match.awayPlayer);
      Integer home = pointsByApiName.get(normalizeName(homeApiName));
      Integer away = pointsByApiName.get(normalizeName(awayApiName));
      if (home == null || away == null) {
        log.warn("No Kickbase points found for {} ({}) vs {} ({})", match.homePlayer, homeApiName, match.awayPlayer, awayApiName);
        continue;
      }
      match.homePoints = home;
      match.awayPoints = away;
      repository.save(match);
    }
  }

  private String resolveApiName(String h2hName) {
    return properties.getPlayerAliases().entrySet().stream()
      .filter(entry -> normalizeName(entry.getKey()).equals(normalizeName(h2hName)))
      .map(Map.Entry::getValue).findFirst().orElse(h2hName);
  }

  private String normalizeName(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFC).replaceAll("[\\s\\p{Z}]+", " ").trim().toLowerCase(Locale.ROOT);
  }

  record KickbaseRanking(Integer day, List<KickbaseManager> us) { List<KickbaseManager> users() { return us; } }
  record KickbaseManager(String n, Integer mdp) { String name() { return n; } Integer matchdayPoints() { return mdp; } }
}
