package com.jakduk.model.web;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.validator.constraints.NotEmpty;


/**
 * @author <a href="mailto:phjang1983@daum.net">Jang,Pyohwan</a>
 * @company  : http://jakduk.com
 * @date     : 2014. 9. 2.
 * @desc     :
 */
public class OAuthUserWrite {

	@NotNull
	@Size(min = 2, max=20)
	private String username;
	
	private String about;
	
	private String footballClub;
	
	private Integer existUsername;
	
	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getAbout() {
		return about;
	}

	public void setAbout(String about) {
		this.about = about;
	}

	public String getFootballClub() {
		return footballClub;
	}

	public void setFootballClub(String footballClub) {
		this.footballClub = footballClub;
	}

	public Integer getExistUsername() {
		return existUsername;
	}

	public void setExistUsername(Integer existUsername) {
		this.existUsername = existUsername;
	}

	@Override
	public String toString() {
		return "OAuthUserWrite [username=" + username + ", about=" + about
				+ ", footballClub=" + footballClub + ", existUsername="
				+ existUsername + "]";
	}

}
