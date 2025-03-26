package kr.kro.bbanggil.admin.mapper;

import java.util.List;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.kro.bbanggil.admin.dto.request.InquiryReplyRequestDto;
import kr.kro.bbanggil.admin.dto.request.InquiryRequestDto;
import kr.kro.bbanggil.admin.dto.response.AdminResponseDto;
import kr.kro.bbanggil.admin.dto.response.InquiryResponseDto;
import kr.kro.bbanggil.admin.dto.response.MenuResponseDto;

@Mapper
public interface AdminMapper {

	List<AdminResponseDto> subList();

	List<AdminResponseDto> bakeryList();

	List<AdminResponseDto> userList();
	
	AdminResponseDto bakeryDetailList(@Param("bakeryNo") int bakeryNo, 
		    						  @Param("userNo") int userNo);
	
	AdminResponseDto userDetailList(int userNo);
	
	AdminResponseDto acceptList(@Param("bakeryNo") int bakeryNo, 
							    @Param("userNo") int userNo);

	List<MenuResponseDto> menuList(int bakeryNo);
	
	void update(@Param("action") String action,
				@Param("bakeryNo") int listNum,
				@Param("rejectReason") String rejectReason);

	void insertInquiry(InquiryRequestDto inquiryRequestDto);
	
	public String getUserType(int userNo);

	List<InquiryResponseDto> selectInquiryList();

	void insertInquiryReply(InquiryReplyRequestDto inquiryReplyDto);

	void updateInquiryStatusToAnswered(@Param("inquiryNo")int inquiryNo);
	
}
