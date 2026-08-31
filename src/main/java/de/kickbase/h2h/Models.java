package de.kickbase.h2h;

import jakarta.persistence.*;

@Entity
class MatchResult {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
  @Column(name = "matchday_no", nullable = false) int matchday;
  @Column(nullable = false) String homePlayer;
  @Column(nullable = false) String awayPlayer;
  Integer homePoints;
  Integer awayPoints;
  MatchResult() {}
  MatchResult(int d, String h, String a) { matchday=d; homePlayer=h; awayPlayer=a; }
}
