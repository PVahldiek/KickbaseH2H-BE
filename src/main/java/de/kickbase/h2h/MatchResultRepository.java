package de.kickbase.h2h;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface MatchResultRepository extends JpaRepository<MatchResult, Long> {
  List<MatchResult> findByMatchdayOrderById(int matchday);
  List<MatchResult> findAllByOrderByMatchdayAscIdAsc();
  Optional<MatchResult> findByMatchdayAndHomePlayer(int matchday, String homePlayer);
}
