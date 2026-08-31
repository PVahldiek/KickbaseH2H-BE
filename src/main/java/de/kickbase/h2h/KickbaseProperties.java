package de.kickbase.h2h;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kickbase")
class KickbaseProperties {
  private String communityApiUrl = "";
  private String leagueId = "";
  private String accessToken = "";
  private Map<String, String> playerAliases = new HashMap<>();
  public String getCommunityApiUrl() { return communityApiUrl; }
  public void setCommunityApiUrl(String value) { communityApiUrl = value; }
  public String getLeagueId() { return leagueId; }
  public void setLeagueId(String value) { leagueId = value; }
  public String getAccessToken() { return accessToken; }
  public void setAccessToken(String value) { accessToken = value; }
  public Map<String, String> getPlayerAliases() { return playerAliases; }
  public void setPlayerAliases(Map<String, String> value) { playerAliases = value; }
}
