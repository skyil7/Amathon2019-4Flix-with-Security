package com.example.netflix.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.netflix.entity.NetflixAccountEntity;
import com.example.netflix.entity.UserEntity;
import com.example.netflix.repository.NetflixAccountRepository;
import com.example.netflix.repository.UserRepository;

@Service
public class UserServiceImpl implements UserService{
	
	@Autowired
	UserRepository userRepository;
	
	@Autowired
	NetflixAccountRepository netflixAccountRepository;
	
	@Autowired
	NetflixAccountUserRelationshipService netflixAccountUserRelationshipService;
	
	@Autowired
	NetflixAccountService netflixAccountService;
	
	@Autowired
	PasswordEncoder passwordEncoder;
	
	@Autowired
	JwtService jwtService;
	
	@Autowired
	EmailSender emailSender;


	// 이메일, 비밀번호 받아서 암호화 후 저장. 이미 존재하는 이메일이거나 실패하면 return null, 아니면 Entity 리턴
	@Override
	public UserEntity regist(UserEntity userEntity) throws Exception {
		boolean available = userRepository.existsByEmail(userEntity.getEmail()); //이메일이 존재하면 true가 리턴됨
		if (available) {
			//이미 존재하는 이름이므로 실패
			return null;
		}
		userEntity.setPassword(passwordEncoder.encode(userEntity.getPassword())); //패스워드 암호화
		UserEntity savedUser = userRepository.save(userEntity); //저장완료
		savedUser.setPassword(""); //비밀번호 숨기기
		
		//TODO 회원가입 이메일 보내주기
		String body = "<div style=\"background-color: black; align-content: center;\">\n" + 
				"<div align=\"center\">\n" + 
				"	<img src=\"https://lh3.googleusercontent.com/HBVtGFIOgppmhAFcLCr41znBXJScF8OlgRfTdqiLL5NV6b48EmR9zfleS1uwQdlXr9w\">\n" + 
				"	<h1 style=\"color: white\">4Flix에 가입하신 것을 환영합니다!</h1>\n" + 
				"	<h3 style=\"color: white\">앞으로 안심하시고 계정공유 서비스를 누리시기 바랍니다.</h3>\n" + 
				"</div>\n" + 
				"<br><br><br><br><br><br><br><br>" + 
				"</div>";
		emailSender.setSUBJECT("Welcome To 4FLIX!");
		emailSender.setTEXTBODY(body);
		emailSender.setHTMLBODY(body);
		emailSender.setTO(emailSender.getFROM());
		try {
			emailSender.sendEmail();
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}
		
		return savedUser;
	}

	// 비밀번호 맞는지 확인 후 맞으면 토큰 map 리턴, 틀리면 null 리턴
	@Override
	public Map<String, Object> login(UserEntity userEntity) throws Exception {
		
		UserEntity findedUser = userRepository.findByEmail(userEntity.getEmail()); //암호화된 비밀번호 받아와
		if (findedUser==null) {
			//실패 :이메일 없음
			return null;
		}
		
		if (passwordEncoder.matches(userEntity.getPassword(), findedUser.getPassword())) {//비밀번호 체크 시 맞으면 
			Map<String, Object> result = new HashMap<>();
			result.put("token", jwtService.makeJwt(findedUser));
			findedUser.setPassword("");
			result.put("user", findedUser);
			return result; // "token" : 토큰 형식의 map
		} else
			return null; //비밀번호가 다르면 null 리턴
	}

	//결제
	@Override
	public void pay(UserEntity userEntity) throws Exception{
		UserEntity findedEntity = userRepository.findById(userEntity.getId());
		findedEntity.setPayed(true);
		findedEntity.setPgTID(userEntity.getPgTID());
		userRepository.save(findedEntity);
		
		//결제 했으니까 이제 계정 배정 해주어야 함
		//3개 2개 1개 순으로 찾아서 배정 해주자
		List<NetflixAccountEntity> accountList;
		int randomIndex;
		NetflixAccountEntity selectedEntity;
		
		int i;
		for (i=3 ; i>=1 ; i--) {
			//날짜가 오늘날짜여야 함
			accountList = netflixAccountRepository.findByPeopleCountAndStartDate(i, LocalDate.now());
			if (accountList==null || accountList.size()<=0)
				continue;
			randomIndex = (int)(Math.random()*(accountList.size()));
			selectedEntity = accountList.get(randomIndex);
			
			//랜덤으로 찾은 계정에 인원 할당
			//relationship 추가
			netflixAccountUserRelationshipService.makeRelationship(selectedEntity, userEntity);
			
			break;
		} 
		if (i<=0) {
			//날짜가 없으므로 비어있는거중에 할당시켜 줘야 함
			//사람 0인거 찾아서
			NetflixAccountEntity netflixAccount = netflixAccountRepository.findByPeopleCount(0).get(0);
			//시작시간 고정 해주고
			netflixAccount.setStartDate(LocalDate.now());
			//저장
			netflixAccountRepository.save(netflixAccount);
			//relationship 맺어주기
			netflixAccountUserRelationshipService.makeRelationship(netflixAccount, userEntity);
		}
		
		//TODO 유저에게 계정 정보 이메일로 보내주기
		NetflixAccountEntity account = netflixAccountService.getUsersAccount(userEntity);
		String body = "<div style=\"background-color: black; align-content: center;\">\n" + 
				"<div align=\"center\">\n" + 
				"	<img src=\"https://lh3.googleusercontent.com/HBVtGFIOgppmhAFcLCr41znBXJScF8OlgRfTdqiLL5NV6b48EmR9zfleS1uwQdlXr9w\">\n" + 
				"	<h1 style=\"color: white\">이제 조금 남았습니다..!</h1>\n" + 
				"	<h3 style=\"color: white\">아래 계정으로 사이트에 로그인 하시면 됩니다.</h3>\n" + 
				"	<h3 style=\"color: white\">요금은 매월 결제일마다 자동으로 청구됩니다.</h3>\n" + 
				"\n" + 
				"	<table style=\"background-color: white; width: 300px; border: 1px solid black\">\n" + 
				"		<tbody>\n" + 
				"			<tr style=\"border: 1px solid black\">\n" + 
				"				<td style=\"border: 1px solid black\">Email</td>\n" + 
				String.format("				<td style=\"border: 1px solid black\">%s</td>\n",account.getEmail()) + 
				"			</tr>\n" + 
				"			<tr style=\"border: 1px solid black\">\n" + 
				"				<td style=\"border: 1px solid black\">Password</td>\n" + 
				String.format("				<td style=\"border: 1px solid black\">%s</td>\n",account.getPassword()) + 
				"			</tr>\n" + 
				"		</tbody>\n" + 
				"	</table>\n" + 
				"</div>\n" + 
				"<br><br><br><br><br><br><br><br>" +
				"</div>";
		emailSender.setSUBJECT("4Flix : Your Account!");
		emailSender.setTEXTBODY(body);
		emailSender.setHTMLBODY(body);
		emailSender.setTO(emailSender.getFROM());
		try {
			emailSender.sendEmail();
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}

	}

	@Override
	public UserEntity changePassword(UserEntity userEntity) throws Exception{
		//아이디로 찾기
		UserEntity findedUser = userRepository.findById(userEntity.getId());
		
		//새로운 비밀번호
		findedUser.setPassword(passwordEncoder.encode(userEntity.getPassword())); //패스워드 암호화
		UserEntity savedUser = userRepository.save(findedUser); //저장완료
		savedUser.setPassword(""); //비밀번호 숨기기

		return savedUser;
	}

	@Override
	public void cancelPay(UserEntity userEntity) throws Exception {
		UserEntity findedUser = userRepository.findById(userEntity.getId());
		findedUser.setPayed(false);
		findedUser.setPgTID("");
		userRepository.save(findedUser);
	}
	

}
