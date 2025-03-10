package kr.kro.bbanggil.subscribe.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SubscriptionRequsetDto {
	
	@NotBlank(message = "이메일을 입력해주세요.")
	@Email(message = "유효한 이메일 형식을 입력해주세요.")
	private String email;

}
