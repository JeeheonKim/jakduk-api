package com.jakduk.core.model.db;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;

/**
 * @author <a href="mailto:phjang1983@daum.net">Jang,Pyohwan</a>
 * @company  : http://jakduk.com
 * @date     : 2015. 3. 10.
 * @desc     :
 */

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document
public class AttendanceLeague {

	@Id  @GeneratedValue(strategy=GenerationType.AUTO)
	private String id;

	@DBRef
	private Competition competition;

	private Integer season;
	
	private Integer games;
	
	private Integer total;
	
	private Integer average;
	
	private Integer numberOfClubs;
}