package com.jakduk.model.db;

import com.jakduk.model.embedded.JakduScheduleScore;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

/**
 * Created by pyohwan on 15. 12. 23.
 */

@Data
@Document
public class JakduSchedule {

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private String id;

    @DBRef
    private JakduScheduleGroup group;

    @Temporal(TemporalType.DATE)
    private Date date;

    @DBRef
    private FootballClubOrigin home;

    @DBRef
    private FootballClubOrigin away;

    @DBRef
    private Competition competition;

    private JakduScheduleScore score;

    private boolean timeUp;
}
