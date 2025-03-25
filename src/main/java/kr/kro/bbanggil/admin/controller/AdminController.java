package kr.kro.bbanggil.admin.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpSession;
import kr.kro.bbanggil.admin.dto.request.InquiryReplyRequestDto;
import kr.kro.bbanggil.admin.dto.request.InquiryRequestDto;
import kr.kro.bbanggil.admin.dto.response.AdminResponseDto;
import kr.kro.bbanggil.admin.dto.response.InquiryResponseDto;
import kr.kro.bbanggil.admin.service.AdminService;
import lombok.AllArgsConstructor;

@Controller
@RequestMapping("/admin")
@AllArgsConstructor
public class AdminController {

	private final AdminService adminService;
	
	@GetMapping("/login")
	public String adminLoginForm() {
		return "admin/admin-login";
	}
	
	@GetMapping("/form")
	public String adminForm(Model model) {

		List<AdminResponseDto> sublist = adminService.subList();

		List<AdminResponseDto> bakeryList = adminService.bakeryList();
		List<AdminResponseDto> userList = adminService.userList();
		
		model.addAttribute("sublists", sublist);
		model.addAttribute("bakeryLists", bakeryList);
		model.addAttribute("userLists", userList);
		
		return "admin/admin-page";
	}

	@GetMapping("/bakery/detail")
	public String bakeryDetailForm(@RequestParam("bakeryNo") int bakeryNo,
			   					   @RequestParam("userNo") int userNo,
			   					   Model model) {

		AdminResponseDto result = adminService.bakeryDetailList(bakeryNo, userNo);
		
		model.addAttribute("result", result);

		return "admin/bakery-detail";
	}

	@GetMapping("/user/detail")
	public String userDetailForm(@RequestParam("userNo") int userNo,
								 Model model) {
		
		AdminResponseDto result = adminService.userDetailList(userNo);
		
		model.addAttribute("result", result);
		
		return "admin/user-detail";
	}

	@GetMapping("/bakery/accept")
	public String bakeryAcceptForm(@RequestParam("listNum") int listNum,
								   @RequestParam("bakeryNo") int bakeryNo,
								   @RequestParam("userNo") int userNo,
								   Model model) {
		
		AdminResponseDto result = adminService.acceptList(bakeryNo, userNo);
		
		model.addAttribute("result", result);
		model.addAttribute("listNum", listNum);

		return "admin/bakery-accept";
	}

	@PostMapping("/bakery/update")
	@ResponseBody
	public String bakeryUpdateForm(@RequestParam("action") String action,
								   @RequestParam("bakeryNo") int bakeryNo,
								   @RequestParam("rejectReason") String rejectReason) {
		
		adminService.update(action, bakeryNo, rejectReason);
		
		String message = ("승인".equals(action) ? "승인" : "거절") + " 완료되었습니다.";

		return "<script>alert('" + message + "'); window.opener.location.reload(); window.close();</script>";
	}

	@GetMapping("/inquiry-write")
	public String inquiryWriteForm() {
		return "admin/admin-inquiry";
	}

	@GetMapping("/inquiry-list")
	public String inquiryListForm() {
		return "admin/admin-inquiry-list";
	}

	/*
	 * 문의 등록 처리
	 */
	@PostMapping("/submit")
	public String submitInquiry(HttpSession session, @ModelAttribute InquiryRequestDto inquiryRequestDto, Model model) {

		// 1.문의 저장
		Integer userNo = (Integer) session.getAttribute("userNum");

		inquiryRequestDto.setUserNo(userNo);

		adminService.saveInquiry(inquiryRequestDto);

		return "redirect:/";
	}

	
	@GetMapping("/inquiry/list")
	public String inquiryList(Model model) {
		List<InquiryResponseDto> inquiries = adminService.getInquiryList();
		
		 model.addAttribute("inquiries", inquiries);
		 
	      return "admin/inquiry-list";
	}
	@PostMapping("/inquiry/answer")
	public String saveAnswer(@ModelAttribute InquiryReplyRequestDto inquiryReplyDto,
						HttpSession session){
		
		
		  Integer adminNo = (Integer) session.getAttribute("userNum");
		  inquiryReplyDto.setAdminNo(adminNo);
		
			adminService.saveAnswer(inquiryReplyDto);
			
			return "redirect:/admin/inquiry/list"; // 저장 후 리스트로 리다이렉트
	}
	
}
