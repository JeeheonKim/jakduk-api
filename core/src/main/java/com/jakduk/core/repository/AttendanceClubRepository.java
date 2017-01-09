package com.jakduk.core.repository;

import com.jakduk.core.model.db.AttendanceClub;
import com.jakduk.core.model.db.FootballClubOrigin;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * @author <a href="mailto:phjang1983@daum.net">Jang,Pyohwan</a>
 * @company  : http://jakduk.com
 * @date     : 2015. 3. 18.
 * @desc     :
 */
public interface AttendanceClubRepository extends MongoRepository<AttendanceClub, String>{

	List<AttendanceClub> findByClub(FootballClubOrigin club, Sort sort);
	List<AttendanceClub> findBySeasonAndLeague(Integer season, String league, Sort sort);
	List<AttendanceClub> findBySeason(Integer season, Sort sort);

}
