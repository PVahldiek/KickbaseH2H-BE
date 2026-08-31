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
  H2hController(MatchResultRepository repository, KickbaseImporter importer) { this.repository=repository; this.importer=importer; }

  @PostMapping("/admin/import-kickbase") @ResponseStatus(HttpStatus.NO_CONTENT)
  void importKickbasePoints() { importer.importCurrentMatchday(); }

  @GetMapping("/matchdays/{matchday}") List<FixtureDto> fixtures(@PathVariable int matchday) {
    return repository.findByMatchdayOrderById(matchday).stream().map(FixtureDto::from).toList();
  }
  @GetMapping("/table") List<TableRowDto> table() {
    Map<String, TableRowDto> table=new TreeMap<>();
    for (MatchResult m:repository.findAllByOrderByMatchdayAscIdAsc()) {
      table.computeIfAbsent(m.homePlayer,TableRowDto::new); table.computeIfAbsent(m.awayPlayer,TableRowDto::new);
      if (m.homePoints == null || m.awayPoints == null) continue;
      TableRowDto h=table.get(m.homePlayer), a=table.get(m.awayPlayer); h.played++; a.played++;
      if(m.homePoints>m.awayPoints){h.wins++;a.losses++;h.points+=3;} else if(m.homePoints<m.awayPoints){a.wins++;h.losses++;a.points+=3;} else {h.draws++;a.draws++;h.points++;a.points++;}
    }
    return table.values().stream().sorted(Comparator.comparingInt(TableRowDto::getPoints).reversed()
      .thenComparing(Comparator.comparingInt(TableRowDto::getWins).reversed()).thenComparing(TableRowDto::getPlayer)).toList();
  }
  @PutMapping("/matchdays/{matchday}/points") @ResponseStatus(HttpStatus.NO_CONTENT) void savePoints(@PathVariable int matchday, @RequestBody Map<String,Integer> points) {
    for(MatchResult m:repository.findByMatchdayOrderById(matchday)) { m.homePoints=points.get(m.homePlayer); m.awayPoints=points.get(m.awayPlayer); repository.save(m); }
  }
  record FixtureDto(int matchday,String homePlayer,String awayPlayer,Integer homePoints,Integer awayPoints) { static FixtureDto from(MatchResult m){return new FixtureDto(m.matchday,m.homePlayer,m.awayPlayer,m.homePoints,m.awayPoints);} }
  public static class TableRowDto {
    final String player; int played,wins,draws,losses,points;
    TableRowDto(String player){this.player=player;}
    public String getPlayer(){return player;}
    public int getPlayed(){return played;}
    public int getWins(){return wins;}
    public int getDraws(){return draws;}
    public int getLosses(){return losses;}
    public int getPoints(){return points;}
  }
}
