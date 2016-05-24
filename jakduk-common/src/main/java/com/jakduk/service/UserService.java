package com.jakduk.service;

import com.jakduk.authentication.common.CommonPrincipal;
import com.jakduk.authentication.jakduk.JakdukPrincipal;
import com.jakduk.authentication.social.SocialUserDetail;
import com.jakduk.common.CommonConst;
import com.jakduk.common.CommonRole;
import com.jakduk.exception.DuplicateDataException;
import com.jakduk.exception.UnauthorizedAccessException;
import com.jakduk.model.db.FootballClub;
import com.jakduk.model.db.User;
import com.jakduk.model.embedded.CommonWriter;
import com.jakduk.model.simple.SocialUserOnAuthentication;
import com.jakduk.model.simple.UserOnPasswordUpdate;
import com.jakduk.model.simple.UserProfile;
import com.jakduk.model.web.user.UserPasswordUpdate;
import com.jakduk.model.web.user.UserProfileForm;
import com.jakduk.repository.FootballClubRepository;
import com.jakduk.repository.user.UserProfileRepository;
import com.jakduk.repository.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.StandardPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

import java.util.*;

@Service
@Slf4j
public class UserService {
	
	@Autowired
	private CommonService commonService;
	
	@Autowired
	private StandardPasswordEncoder encoder;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UserProfileRepository userProfileRepository;
	
	@Autowired
	private FootballClubRepository footballClubRepository;

	@Autowired
	private MongoTemplate mongoTemplate;

	public User findById(String id) {
		return userRepository.findOne(id);
	}

	public List<User> findAll() {
		return userRepository.findAll();
	}

	public UserProfile findUserProfileById(String id) {
		return userProfileRepository.findOne(id);
	}

	// email과 일치하는 회원 찾기.
	public UserProfile findOneByEmail(String email) {
		return userProfileRepository.findOneByEmail(email);
	}

	// username과 일치하는 회원 찾기.
	public UserProfile findOneByUsername(String username) {
		return userProfileRepository.findOneByUsername(username);
	}

	// 해당 ID를 제외하고 username과 일치하는 회원 찾기.
	public UserProfile findByNEIdAndUsername(String id, String username) {
		return userRepository.findByNEIdAndUsername(id, username);
	};

	// 해당 ID를 제외하고 email과 일치하는 회원 찾기.
	public UserProfile findByNEIdAndEmail(String id, String email) {
		return userRepository.findByNEIdAndEmail(id, email);
	}

	// SNS 계정으로 가입한 회원 찾기.
	public UserProfile findOneByProviderIdAndProviderUserId(CommonConst.ACCOUNT_TYPE providerId, String providerUserId) {
		return userProfileRepository.findOneByProviderIdAndProviderUserId(providerId, providerUserId);
	}

	// 회원 정보 저장.
	public void save(User user) {
		userRepository.save(user);
	}

	public CommonWriter testFindId(String userid) {
		Query query = new Query();
		query.addCriteria(Criteria.where("email").is(userid));
		
		return mongoTemplate.findOne(query, CommonWriter.class);
	}

	public void oauthUserWrite(SocialUserOnAuthentication oauthUserOnLogin) {
		User user = new User();
		
		user.setUsername(oauthUserOnLogin.getUsername());
		//user.setSocialInfo(oauthUserOnLogin.getSocialInfo());
		user.setRoles(oauthUserOnLogin.getRoles());
		
		userRepository.save(user);
	}
	
	public Boolean existEmail(Locale locale, String email) {
		Boolean result = false;

		if (commonService.isAnonymousUser()) {
			User user = userRepository.findOneByEmail(email);

			if (Objects.nonNull(user))
				throw new DuplicateDataException(commonService.getResourceBundleMessage(locale, "messages.user", "user.msg.already.email"));
		} else {
			throw new UnauthorizedAccessException(commonService.getResourceBundleMessage(locale, "messages.common", "common.exception.already.you.are.user"));
		}
		
		return result;
	}
	
	public Boolean existUsernameOnWrite(Locale locale, String username) {
		Boolean result = false;

		if (commonService.isAnonymousUser()) {
			User user = userRepository.findOneByUsername(username);

			if (Objects.nonNull(user))
				throw new DuplicateDataException(commonService.getResourceBundleMessage(locale, "messages.user", "user.msg.already.username"));
		} else {
			throw new UnauthorizedAccessException(commonService.getResourceBundleMessage(locale, "messages.common", "common.exception.already.you.are.user"));
		}

		return result;
	}

	/**
	 * 회원 프로필 업데이트 시 별명 중복 체크.
	 * @param locale
	 * @param username
     * @return
     */
	public Boolean existUsernameOnUpdate(Locale locale, String username) {

		CommonPrincipal commonPrincipal = this.getCommonPrincipal();

		if (Objects.isNull(commonPrincipal.getId()))
			throw new UnauthorizedAccessException(commonService.getResourceBundleMessage(locale, "messages.common", "common.exception.access.denied"));

		UserProfile userProfile = userRepository.findByNEIdAndUsername(commonPrincipal.getId(), username);

		if (Objects.nonNull(userProfile))
			throw new DuplicateDataException(commonService.getResourceBundleMessage(locale, "messages.user", "user.msg.replicated.data"));

		return false;
	}

	public Model getUserPasswordUpdate(Model model) {

		model.addAttribute("userPasswordUpdate", new UserPasswordUpdate());
		
		return model;
	}
	
	public void checkUserPasswordUpdate(UserPasswordUpdate userPasswordUpdate, BindingResult result) {
		
		String oldPwd = userPasswordUpdate.getOldPassword();
		String newPwd = userPasswordUpdate.getNewPassword();
		String newPwdCfm = userPasswordUpdate.getNewPasswordConfirm();
		
		JakdukPrincipal jakdukPrincipal = (JakdukPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		String id = jakdukPrincipal.getId();
		
		UserOnPasswordUpdate user = userRepository.userOnPasswordUpdateFindById(id);
		String pwd = user.getPassword();
		
		if (!encoder.matches(oldPwd, pwd)) {
			result.rejectValue("oldPassword", "user.msg.old.password.is.not.vaild");
		}
		
		if (!newPwd.equals(newPwdCfm)) {
			result.rejectValue("passwordConfirm", "user.msg.password.mismatch");
		}
	}
	
	public void userPasswordUpdate(UserPasswordUpdate userPasswordUpdate) {
		
		JakdukPrincipal jakdukPrincipal = (JakdukPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		String id = jakdukPrincipal.getId();
		
		User user = userRepository.findById(id);
		
		user.setPassword(encoder.encode(userPasswordUpdate.getNewPassword()));
		
		this.save(user);
		
		if (log.isInfoEnabled()) {
			log.info("jakduk user password changed. id=" + user.getId() + ", username=" + user.getUsername());
		}
	}

	public void userPasswordUpdateByEmail(String email, String password) {
		User user = userRepository.findByEmail(email);
		user.setPassword(encoder.encode(password));
		this.save(user);

		if (log.isInfoEnabled()) {
			log.info("jakduk user password changed. id=" + user.getId() + ", username=" + user.getUsername());
		}
	}

	public User editSocialProfile(UserProfileForm userProfileForm) {

		SocialUserDetail principal = (SocialUserDetail) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

		User user = userRepository.findById(principal.getId());

		String email = userProfileForm.getEmail();
		String username = userProfileForm.getUsername();
		String footballClub = userProfileForm.getFootballClub();
		String about = userProfileForm.getAbout();

		if (Objects.nonNull(email) && email.isEmpty() == false) {
			user.setEmail(email.trim());
		}

		if (Objects.nonNull(username) && username.isEmpty() == false) {
			user.setUsername(username.trim());
		}

		if (Objects.nonNull(footballClub) && footballClub.isEmpty() == false) {
			FootballClub supportFC = footballClubRepository.findOne(userProfileForm.getFootballClub());
			user.setSupportFC(supportFC);
		}

		if (Objects.nonNull(about) && about.isEmpty() == false) {
			user.setAbout(about.trim());
		}

		userRepository.save(user);

		return user;
	}

	/**
	 * 로그인 중인 회원의 정보를 가져온다.
	 * @return
     */
	public CommonPrincipal getCommonPrincipal() {
		CommonPrincipal commonPrincipal = new CommonPrincipal();
		
		if (SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
			if (SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof SocialUserDetail) {
				SocialUserDetail userDetail = (SocialUserDetail) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
				commonPrincipal.setId(userDetail.getId());
				commonPrincipal.setEmail(userDetail.getUserId());
				commonPrincipal.setUsername(userDetail.getUsername());
				commonPrincipal.setProviderId(userDetail.getProviderId());
			} else if (SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof JakdukPrincipal) {
				JakdukPrincipal principal = (JakdukPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
				commonPrincipal.setId(principal.getId());
				commonPrincipal.setEmail(principal.getUsername());
				commonPrincipal.setUsername(principal.getNickname());
				commonPrincipal.setProviderId(principal.getProviderId());
			}
		}
		
		return commonPrincipal;
	}

	// jakduk 회원의 로그인 처리.
	public void signUpJakdukUser(User user) {

		boolean enabled = true;
		boolean accountNonExpired = true;
		boolean credentialsNonExpired = true;
		boolean accountNonLocked = true;

		JakdukPrincipal jakdukPrincipal = new JakdukPrincipal(user.getEmail(), user.getId(),
				user.getPassword(), user.getUsername(), user.getProviderId(),
				enabled, accountNonExpired, credentialsNonExpired, accountNonLocked, getAuthorities(user.getRoles()));

		UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(jakdukPrincipal, user.getPassword(), jakdukPrincipal.getAuthorities());

		SecurityContextHolder.getContext().setAuthentication(token);
	}

	// SNS 인증 회원의 로그인 처리.
	public void signUpSocialUser(User user) {

		SocialUserDetail userDetail = new SocialUserDetail(user.getId(), user.getEmail(), user.getUsername(), user.getProviderId(), user.getProviderUserId(),
				true, true, true, true, getAuthorities(user.getRoles()));

		Authentication authentication = new UsernamePasswordAuthenticationToken(userDetail, null, userDetail.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	public Collection<? extends GrantedAuthority> getAuthorities(List<Integer> roles) {
		List<GrantedAuthority> authList = getGrantedAuthorities(getRoles(roles));

		return authList;
	}

	public List<String> getRoles(List<Integer> roles) {
		List<String> newRoles = new ArrayList<String>();

		if (roles != null) {
			for (Integer roleNumber : roles) {
				String roleName = CommonRole.getRoleName(roleNumber);
				if (!roleName.isEmpty()) {
					newRoles.add(roleName);
				}
			}
		}

		return newRoles;
	}

	public static List<GrantedAuthority> getGrantedAuthorities(List<String> roles) {
		List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();

		for (String role : roles) {
			authorities.add(new SimpleGrantedAuthority(role));
		}

		return authorities;
	}
		
}