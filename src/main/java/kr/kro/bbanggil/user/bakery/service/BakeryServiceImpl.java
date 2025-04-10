package kr.kro.bbanggil.user.bakery.service;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;

import groovy.transform.Undefined.EXCEPTION;
import kr.kro.bbanggil.admin.dto.response.myBakeryResponseDTO;
import kr.kro.bbanggil.common.dto.PageInfoDTO;
import kr.kro.bbanggil.common.util.AwsS3Util;
import kr.kro.bbanggil.common.util.FileUploadUtil;
import kr.kro.bbanggil.common.util.PaginationUtil;
import kr.kro.bbanggil.global.exception.BbanggilException;
import kr.kro.bbanggil.user.bakery.api.KakaoController;
import kr.kro.bbanggil.user.bakery.dto.BakeryDto;
import kr.kro.bbanggil.user.bakery.dto.BakeryImageDTO;
import kr.kro.bbanggil.user.bakery.dto.BakeryInfoDTO;
import kr.kro.bbanggil.user.bakery.dto.BakerySearchDTO;
import kr.kro.bbanggil.user.bakery.dto.BakeryTimeSetDTO;
import kr.kro.bbanggil.user.bakery.dto.request.BakeryImgRequestDTO;
import kr.kro.bbanggil.user.bakery.dto.request.BakeryRequestDTO;
import kr.kro.bbanggil.user.bakery.dto.request.BakeryTimeRequestDTO;
import kr.kro.bbanggil.user.bakery.dto.request.FileRequestDTO;
import kr.kro.bbanggil.user.bakery.dto.request.MenuDetailRequestDto;
import kr.kro.bbanggil.user.bakery.dto.request.MenuRequestDTO;
import kr.kro.bbanggil.user.bakery.dto.response.BakeryDetailResponseDto;
import kr.kro.bbanggil.user.bakery.dto.response.CategoryResponseDTO;
import kr.kro.bbanggil.user.bakery.dto.response.FileResponseDTO;
import kr.kro.bbanggil.user.bakery.dto.response.MenuResponseDto;
import kr.kro.bbanggil.user.bakery.dto.response.MenuUpdateResponseDTO;
import kr.kro.bbanggil.user.bakery.dto.response.bakeryUpdateResponseDTO;
import kr.kro.bbanggil.user.bakery.mapper.BakeryMapper;
import kr.kro.bbanggil.user.bakery.util.ListPageNation;
import kr.kro.bbanggil.user.bakery.util.LocationSelectUtil;
import kr.kro.bbanggil.user.bakery.vo.BakeryDetailVO;
import kr.kro.bbanggil.user.bakery.vo.BakeryInfoVO;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BakeryServiceImpl implements BakeryService{
	private final BakeryMapper bakeryMapper;
	private final FileUploadUtil fileUpload;
	private final KakaoController kakao;
	private final LocationSelectUtil locationSelect;
	private final AwsS3Util s3Upload;
	private static final Logger logger = LogManager.getLogger(BakeryServiceImpl.class);
	
	@Override
	public Map<String, Object> bakeryList(PaginationUtil pageNation,
											int currentPage,
											int postCount,
											int pageLimit,
											int boardLimit,
											String orderType,
											BakerySearchDTO bakerySearchDTO,
											int userNo){
		String searchText = bakerySearchDTO.getSearchText();
        String[] keywords = searchText.split("\\s+"); // 공백 기준으로 분리

        if (keywords.length >= 2) {
            bakerySearchDTO.setKeyword1(keywords[0]); // 첫 번째 단어 (ex: 경기)
            bakerySearchDTO.setKeyword2(keywords[1]); // 두 번째 단어 (ex: 단팥빵)
        } else {
            bakerySearchDTO.setKeyword1(searchText);  // 단어 하나만 입력된 경우
            bakerySearchDTO.setKeyword2(searchText);
        }
		
        String locationAgree = bakeryMapper.getLocationAgree(userNo);
        System.out.println(locationAgree);
		PageInfoDTO pi = pageNation.getPageInfo(postCount, currentPage, pageLimit, boardLimit);
		
		List<BakeryInfoDTO> posts = bakeryMapper.bakeryList(pi, 
															getTodayDayOfWeek(),
															orderType,
															bakerySearchDTO);
		
		List<List<BakeryInfoDTO>> images = new ArrayList<>();
		
		for (int i = 0; i < posts.size(); i++) {
		    images.add(bakeryMapper.bakeryImage(posts.get(i).getBakeryNo()));
		}
		
		Map<String,Object> result = new HashMap<>();
		
		result.put("pi", pi);
		result.put("posts", posts);
		result.put("images", images);
		result.put("locationAgree", locationAgree);
		
		return result;
	}
	
	// 빵집 수 
	@Override
	public int totalCount(BakerySearchDTO bakerySearchDTO) {
		return bakeryMapper.totalCount(bakerySearchDTO);
	}
	

	
	//오늘 요일 구하기
	@Override
	public String getTodayDayOfWeek() {
		LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeek = today.getDayOfWeek();
        return getKoreanDayOfWeek(dayOfWeek);
	}
	
	private String getKoreanDayOfWeek(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }
	/**
	 * location : 카카오 api로 bakeryRequestDTO에 있는 주소 값을 통해 데이터를 받아오는 변수
	 * address : 받아온 주소를 " "기준으로 잘라서 배열에 넣어두는 변수
	 * @param role 
	 * @throws Exception 
	 */
	@Override
	@Transactional(rollbackFor = EXCEPTION.class)
	public int bakeryInsert(BakeryRequestDTO bakeryRequestDTO, BakeryImgRequestDTO bakeryImgRequestDTO,int userNo, String role) throws Exception {
		try {
			if(!role.equals("owner"))
				throw new BbanggilException("사장이 아닙니다","common/error",HttpStatus.BAD_REQUEST);
			
			JsonNode location=kakao.getLocationFromAddress(bakeryRequestDTO.getBakeryAddress());
				
			String[] address = bakeryRequestDTO.getBakeryAddress().split(" ");
			String region = locationSelect.selectLocation(address[0]);
			
			BakeryInfoVO bakeryVO = BakeryInfoVO.builder()
									
									.bakeryName(bakeryRequestDTO.getBakeryName())
									.bakeryAddress(bakeryRequestDTO.getBakeryAddress())
									.bakeryPhone(bakeryRequestDTO.getBakeryPhone())
									.latitude(location.get("y").asDouble())
									.longitude(location.get("x").asDouble())
									.region(region)
									.build();
			

				bakeryMapper.bakeryInsert(bakeryVO);
				bakeryRequestDTO.setBakeryNo(bakeryVO.getBakeryNo());

				
			BakeryDetailVO detailVO = BakeryDetailVO.builder()
									  .amenity(bakeryRequestDTO.getParkingInfo())
									  .insideInfo(bakeryRequestDTO.getInsideInfo())
									  .outsideInfo(bakeryRequestDTO.getOutsideInfo())
									  .createdDate(bakeryRequestDTO.getCreatedDate())
									  .bakeryNo(bakeryRequestDTO.getBakeryNo())
									  .build();
			bakeryMapper.bakeryDetailInsert(detailVO);
				
				for(BakeryTimeSetDTO item : bakeryRequestDTO.getTime()) {
					bakeryMapper.bakeryScheduleInsert(item,bakeryRequestDTO.getBakeryNo());
				}
				/**
				 * filemap : 이미지가 들어가지는 위치에 따라 ("이미지의 위치",이미지 내용)으로 매핑되는 변수
				 */
			Map<String,List<MultipartFile>> filemap = setImg(bakeryImgRequestDTO);
						
				/**
				 * imgLocation : 이미지가 입력된 위치
				 * files : 해당 위치에 들어간 이미지들
				 */
				for(Map.Entry<String,List<MultipartFile>> entry : filemap.entrySet()) {
					String imgLocation = entry.getKey();
					List<MultipartFile> files = entry.getValue();
					
					
					if(bakeryImgRequestDTO.checkFile(files)) {
						for(int i=0;i<files.size();i++) {
							s3Upload.saveFile(files.get(i),bakeryRequestDTO.getFileDTO());
							bakeryRequestDTO.setImgLocation(imgLocation);
							bakeryMapper.bakeryFileUpload(bakeryRequestDTO);
						}
					} else {
						logger.warn("파일업로드 실패! : {}",imgLocation);
					}
				}
				bakeryMapper.setBakery(bakeryRequestDTO.getBakeryNo(),userNo);	
				bakeryMapper.UserCountInsert(bakeryRequestDTO.getBakeryNo());
			return bakeryRequestDTO.getBakeryNo();
				
		} catch (Exception e) {
			
			logger.error("에러발생! : {}",e.getMessage());
			throw new BbanggilException("신청작업 오류","common/error",HttpStatus.BAD_REQUEST);
		}
		
		
	}
	/**
	 * bakeryUpdate 페이지를 로드할떄 사용
	 * response : 특정 번호의 빵집에 대한 데이터
	 * setBakeryOperatingHours : 데이터 형태 변환용 메서드(요일에 맞게 시간 형식 맞춤)
	 * 							 ex)월 : open_time = 09:00 , close_time = 17:00
	 * 								월 : 09:00~17:00
	 */
	@Override
	public bakeryUpdateResponseDTO getbakeryInfo(int bakeryNo) {
		bakeryUpdateResponseDTO response = bakeryMapper.getBakeryInfo(bakeryNo);
		response.setImgDTO(bakeryMapper.getBakeryImg(bakeryNo));
		List<BakeryTimeSetDTO> timeDTO = bakeryMapper.getBakerySchedule(bakeryNo);
		setBakeryOperatingHours(response,timeDTO);
		return response;
	}
	
	@Override
	@Transactional
	public void bakeryUpdate(BakeryRequestDTO bakeryRequestDTO,
			   				 BakeryImgRequestDTO bakeryImgRequestDTO,
			   				 int userNo) {
		int requestUserNo = bakeryMapper.requestUserNo(bakeryRequestDTO.getBakeryNo());
		Map<String,List<MultipartFile>> filemap = setImg(bakeryImgRequestDTO);
		try {
		if(requestUserNo==userNo) {
			for(Map.Entry<String,List<MultipartFile>> entry : filemap.entrySet()) {
				String imgLocation = entry.getKey();
				List<MultipartFile> files = entry.getValue();
					if(bakeryImgRequestDTO.checkFile(files)) {
						List<FileResponseDTO> fileCheck = bakeryMapper.getFileInfo(imgLocation);
						for(int i=0;i<fileCheck.size();i++) {
							String fileName = fileCheck.get(i).getChangeName();
							String localPath = fileCheck.get(i).getLocalPath();
							bakeryMapper.deleteFile(fileName);
							bakeryMapper.deleteFile(fileName);
							fileUpload.deleteFile(localPath, imgLocation, fileName);
							s3Upload.deleteImage(fileName);
						}
						
						for(int i=0;i<files.size();i++) {
							s3Upload.saveFile(files.get(i),bakeryRequestDTO.getFileDTO());
							bakeryRequestDTO.setImgLocation(imgLocation);
							bakeryMapper.bakeryFileUpload(bakeryRequestDTO);
						}
							
					}else {
						logger.warn("파일업로드 실패! : {}",imgLocation);
					}
					
				}
			bakeryMapper.bakeryUpdate(bakeryRequestDTO);
			bakeryMapper.bakeryDetailUpdate(bakeryRequestDTO);
			bakeryRequestDTO.setTime();
			for(BakeryTimeSetDTO item : bakeryRequestDTO.getTime()) {
				bakeryMapper.bakeryScheduleUpdate(item,bakeryRequestDTO.getBakeryNo());
			}
			bakeryMapper.bakeryAccessUpdate(bakeryRequestDTO);
			
			}
		} catch(Exception e) {
			logger.error("에러발생! : {}",e.getMessage());
			throw new BbanggilException("신청작업 오류","common/error",HttpStatus.BAD_REQUEST);
		}
	}

	@Override
	public List<BakeryDto> getBakeriesByRegion(String region) {

		return bakeryMapper.getBakeriesByRegion(region);
	}

	@Override
	public List<BakeryDto> getPopularBakeries() {

		return bakeryMapper.getPopularBakeries();
	}

	@Override
	public List<BakeryDto> getRecentBakeries(double bakeryNo) {

		return bakeryMapper.getRecentBakeries(bakeryNo);
	}

	@Override
	public List<BakeryDto> getCategoryBakeries(List<BakeryDto> topBakeries) {

		List<String> categoryNames = topBakeries.stream().map(bakery -> bakery.getResponse().getCategory()).distinct()
				.collect(Collectors.toList());

		return bakeryMapper.getCategoryBakeries(categoryNames);
	}

	@Override
	public List<BakeryDto> getTopFiveOrders() {
		return bakeryMapper.getTopFiveOrders();
	}

	@Override
	public List<BakeryDto> getBakeryImages(double no) {

		return bakeryMapper.findBakeryImages(no);

	}
	
	@Override
	public List<BakeryDto> getBakeriesInfo(double no){
		return bakeryMapper.findBakeriesInfo(no);
	}

	@Override
	public List<MenuResponseDto> getMenuInfo(double no){
	
		List<MenuResponseDto> menuList = bakeryMapper.getMenuInfo(no);
		return menuList;
	}

	@Override
	public void addCart(int userNo, List<MenuDetailRequestDto> menuDto) {

		bakeryMapper.insertCart(userNo);
		Integer cartNo = bakeryMapper.getCartNoByUserNo(userNo);
		for (MenuDetailRequestDto item : menuDto) {
			bakeryMapper.insertCartInfo(cartNo, item.getMenuNo(), item.getMenuCount());
		}

	}
	@Override
	public BakeryDto getBakeryByNo(double bakeryNo) {
		return bakeryMapper.findBakeryByNo(bakeryNo);
	}
	
	public List<BakeryDto> getBakeryDetail(double no) {
		
		return bakeryMapper.getBakeryDetail(no);
	}
	@Override
	public List<BakeryImageDTO> getInsideImages(double bakeryNo) {
	    return bakeryMapper.getInsideImages(bakeryNo);
	}

	@Override
	public List<BakeryImageDTO> getOutsideImages(double bakeryNo) {
	    return bakeryMapper.getOutsideImages(bakeryNo);
	}	
	
	private Map<String,List<MultipartFile>> setImg(BakeryImgRequestDTO bakeryImgRequestDTO) {
		Map<String,List<MultipartFile>> result = new LinkedHashMap<>();
		result.put("main", bakeryImgRequestDTO.getMain());
		result.put("inside", bakeryImgRequestDTO.getInside());
		result.put("outside", bakeryImgRequestDTO.getOutside());
		result.put("parking", bakeryImgRequestDTO.getParking());
		return result;

	}
	
	
	
	@Override
	public void updateUserCount(int bakeryNo) {
		int count = bakeryMapper.getUserCountBybakeryNo(bakeryNo);
		count++;
		bakeryMapper.updateUserCount(bakeryNo,count);
	}

	public List<BakeryImageDTO> getParkingImages(double bakeryNo) {
		return bakeryMapper.getParkingImages(bakeryNo);
	}

	public List<BakeryDto> getBakeriesTime(double no) {
		return bakeryMapper.getBakeriesTime(no);
	}
	
	@Override
	public myBakeryResponseDTO bakeryInfo(int bakeryNo) {
		return bakeryMapper.bakeryInfo(bakeryNo);
	}

	
	private void setBakeryOperatingHours(bakeryUpdateResponseDTO bakeryInfo, List<BakeryTimeSetDTO> timeDTO) {
		BakeryTimeRequestDTO requestDTO =  bakeryInfo.getTimeDTO();
		Map<String, Consumer<String>> daySetterMap = new HashMap<>();
	    daySetterMap.put("월", value -> requestDTO.setMonday(value));
	    daySetterMap.put("화", value -> requestDTO.setTuesday(value));
	    daySetterMap.put("수", value -> requestDTO.setWednesday(value));
	    daySetterMap.put("목", value -> requestDTO.setThursday(value));
	    daySetterMap.put("금", value -> requestDTO.setFriday(value));
	    daySetterMap.put("토", value -> requestDTO.setSaturday(value));
	    daySetterMap.put("일", value -> requestDTO.setSunday(value));

	    // 영업시간을 BakeryUpdateResponseDTO에 세팅
	    for (BakeryTimeSetDTO time : timeDTO) {
	        String formattedTime = time.getStart() + "~" + time.getEnd();
	        if (daySetterMap.containsKey(time.getDay())) {
	            daySetterMap.get(time.getDay()).accept(formattedTime);
	        }
	    }
	    Map<String, Supplier<String>> dayGetterMap = new HashMap<>();
	    dayGetterMap.put("월", bakeryInfo::getMonday);
	    dayGetterMap.put("화", bakeryInfo::getTuesday);
	    dayGetterMap.put("수", bakeryInfo::getWednesday);
	    dayGetterMap.put("목", bakeryInfo::getThursday);
	    dayGetterMap.put("금", bakeryInfo::getFriday);
	    dayGetterMap.put("토", bakeryInfo::getSaturday);
	    dayGetterMap.put("일", bakeryInfo::getSunday);

	    // 월~금 값이 모두 같은지 확인
	    Set<String> uniqueTimes = new HashSet<>();
	    Set<String> checkTimes = new HashSet<>();
	    for (String day : Arrays.asList("월", "화", "수", "목", "금")) {
	        uniqueTimes.add(dayGetterMap.get(day).get());
	    }
	    if(uniqueTimes.size() == 1) {
	    	bakeryInfo.setWeekday(uniqueTimes.iterator().next());
	    }
	    for (String day : Arrays.asList("토", "일")) {
	    	checkTimes.add(dayGetterMap.get(day).get());
	    }
	    if(checkTimes.size() == 1) {
	    	 bakeryInfo.setWeekend(checkTimes.iterator().next());
	    }
	}
}
