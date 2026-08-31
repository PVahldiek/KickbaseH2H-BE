package de.kickbase.h2h;

import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ScheduleSeeder {
  private static final String[][] ROUNDS = {
    {"Luki|Müller","Chris|Passi","Robin|Tobi","Frank|Maddin","Jo|Jörg"},
    {"Luki|Passi","Müller|Tobi","Chris|Maddin","Robin|Jörg","Frank|Jo"},
    {"Luki|Tobi","Passi|Maddin","Müller|Jörg","Chris|Jo","Robin|Frank"},
    {"Luki|Maddin","Tobi|Jörg","Passi|Jo","Müller|Frank","Chris|Robin"},
    {"Luki|Jörg","Maddin|Jo","Tobi|Frank","Passi|Robin","Müller|Chris"},
    {"Luki|Jo","Jörg|Frank","Maddin|Robin","Tobi|Chris","Passi|Müller"},
    {"Luki|Frank","Jo|Robin","Jörg|Chris","Maddin|Müller","Tobi|Passi"},
    {"Luki|Robin","Frank|Chris","Jo|Müller","Jörg|Passi","Maddin|Tobi"},
    {"Luki|Chris","Robin|Müller","Frank|Passi","Jo|Tobi","Jörg|Maddin"}
  };
  // Reihenfolge der von dir gelieferten 34 Spieltage, referenziert Runde 1–9.
  private static final int[] SEASON = {1,2,3,4,5,6,7,8,9,6,2,9,4,1,8,5,3,7,3,8,1,7,5,9,2,6,4,8,4,2,9,5,1,7};
  @Bean CommandLineRunner seedSchedule(MatchResultRepository repository) {
    return args -> { if (repository.count() == 0) for (int day=1; day<=SEASON.length; day++)
      for (String fixture : ROUNDS[SEASON[day-1]-1]) { String[] p=fixture.split("\\|"); repository.save(new MatchResult(day,p[0],p[1])); }
    };
  }
}
