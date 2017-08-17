package com.jakduk.api.service;

import com.jakduk.api.common.Constants;
import com.jakduk.api.common.board.category.BoardCategory;
import com.jakduk.api.common.board.category.BoardCategoryGenerator;
import com.jakduk.api.common.rabbitmq.RabbitMQPublisher;
import com.jakduk.api.common.util.AuthUtils;
import com.jakduk.api.common.util.DateUtils;
import com.jakduk.api.common.util.JakdukUtils;
import com.jakduk.api.common.util.UrlGenerationUtils;
import com.jakduk.api.exception.ServiceError;
import com.jakduk.api.exception.ServiceException;
import com.jakduk.api.model.aggregate.BoardFeelingCount;
import com.jakduk.api.model.aggregate.BoardPostTop;
import com.jakduk.api.model.aggregate.CommonCount;
import com.jakduk.api.model.db.BoardFree;
import com.jakduk.api.model.db.BoardFreeComment;
import com.jakduk.api.model.db.Gallery;
import com.jakduk.api.model.db.UsersFeeling;
import com.jakduk.api.model.embedded.*;
import com.jakduk.api.model.simple.*;
import com.jakduk.api.repository.board.free.BoardFreeOnListRepository;
import com.jakduk.api.repository.board.free.BoardFreeRepository;
import com.jakduk.api.repository.board.free.comment.BoardFreeCommentRepository;
import com.jakduk.api.repository.gallery.GalleryRepository;
import com.jakduk.api.restcontroller.vo.board.*;
import com.jakduk.api.restcontroller.vo.home.LatestPost;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BoardFreeService {

	@Autowired private UrlGenerationUtils urlGenerationUtils;
	@Autowired private BoardFreeRepository boardFreeRepository;
	@Autowired private BoardFreeOnListRepository boardFreeOnListRepository;
	@Autowired private BoardFreeCommentRepository boardFreeCommentRepository;
	@Autowired private GalleryRepository galleryRepository;
	@Autowired private CommonService commonService;
	@Autowired private CommonGalleryService commonGalleryService;
	@Autowired private RabbitMQPublisher rabbitMQPublisher;

	public BoardFree findOneBySeq(Integer seq) {
        return boardFreeRepository.findOneBySeq(seq)
                .orElseThrow(() -> new ServiceException(ServiceError.NOT_FOUND_POST));
    }

    /**
     * 자유게시판 글쓰기
	 *
     * @param subject 글 제목
     * @param content 글 내용
     * @param categoryCode 글 말머리 Code
     * @param galleries 글과 연동된 사진들
     * @param device 디바이스
     */
	public BoardFree insertFreePost(CommonWriter writer, Constants.BOARD_TYPE board, String subject, String content, String categoryCode,
									List<Gallery> galleries, Constants.DEVICE_TYPE device) {

		if (! new BoardCategoryGenerator().existCategory(board, categoryCode))
			throw new ServiceException(ServiceError.NOT_FOUND_CATEGORY);

		// shortContent 만듦
		String stripHtmlContent = StringUtils.defaultIfBlank(JakdukUtils.stripHtmlTag(content), StringUtils.EMPTY);
		String shortContent = StringUtils.truncate(stripHtmlContent, Constants.BOARD_SHORT_CONTENT_LENGTH);

		// 글 상태
		BoardStatus boardStatus = BoardStatus.builder()
				.device(device)
				.build();

		ObjectId logId = new ObjectId();
		// lastUpdated
		LocalDateTime lastUpdated = LocalDateTime.ofInstant(logId.getDate().toInstant(), ZoneId.systemDefault());

		// 연관된 사진 id 배열 (검증 후)
		List<String> galleryIds = galleries.stream()
				.map(Gallery::getId)
				.collect(Collectors.toList());

		BoardFree boardFree = BoardFree.builder()
				.writer(writer)
				.board(board.name())
				.category(categoryCode)
				.subject(subject)
				.content(content)
				.shortContent(shortContent)
				.views(0)
				.seq(commonService.getNextSequence(Constants.SEQ_BOARD))
				.status(boardStatus)
				.logs(this.initBoardLogs(logId, Constants.BOARD_FREE_HISTORY_TYPE.CREATE.name(), writer))
				.lastUpdated(lastUpdated)
				.linkedGallery(! galleries.isEmpty())
				.build();

		boardFreeRepository.save(boardFree);

	 	// 엘라스틱서치 색인 요청
		rabbitMQPublisher.indexDocumentBoard(boardFree.getId(), boardFree.getSeq(), boardFree.getWriter(), boardFree.getSubject(),
				boardFree.getContent(), boardFree.getBoard(), boardFree.getCategory(), galleryIds);

		log.info("new post created. post seq={}, subject={}", boardFree.getSeq(), boardFree.getSubject());

		return boardFree;
	}

	/**
	 * 자유게시판 글 고치기
	 *
	 * @param seq 글 seq
	 * @param subject 글 제목
	 * @param content 글 내용
	 * @param categoryCode 글 말머리 Code
	 * @param galleryIds 글과 연동된 사진들
     * @param device 디바이스
     */
	public BoardFree updateFreePost(CommonWriter writer, Integer seq, String subject, String content, String categoryCode,
									List<String> galleryIds, Constants.DEVICE_TYPE device) {

		BoardFree boardFree = boardFreeRepository.findOneBySeq(seq)
				.orElseThrow(() -> new ServiceException(ServiceError.NOT_FOUND_POST));

		if (! boardFree.getWriter().getUserId().equals(writer.getUserId()))
			throw new ServiceException(ServiceError.FORBIDDEN);

		// shortContent 만듦
		String stripHtmlContent = StringUtils.defaultIfBlank(JakdukUtils.stripHtmlTag(content), StringUtils.EMPTY);
		String shortContent = StringUtils.truncate(stripHtmlContent, Constants.BOARD_SHORT_CONTENT_LENGTH);

		boardFree.setSubject(subject);
		boardFree.setContent(content);
		boardFree.setCategory(categoryCode);
		boardFree.setShortContent(shortContent);
		boardFree.setLinkedGallery(! galleryIds.isEmpty());

		// 글 상태
		BoardStatus boardStatus = boardFree.getStatus();

		if (Objects.isNull(boardStatus))
			boardStatus = new BoardStatus();

        boardStatus.setDevice(device);
		boardFree.setStatus(boardStatus);

		// boardHistory
		List<BoardLog> logs = boardFree.getLogs();

		if (CollectionUtils.isEmpty(logs))
			logs = new ArrayList<>();

		ObjectId logId = new ObjectId();
		BoardLog log = new BoardLog(logId.toString(), Constants.BOARD_FREE_HISTORY_TYPE.EDIT.name(), new SimpleWriter(writer));
		logs.add(log);
		boardFree.setLogs(logs);

		// lastUpdated
		boardFree.setLastUpdated(LocalDateTime.ofInstant(logId.getDate().toInstant(), ZoneId.systemDefault()));

		boardFreeRepository.save(boardFree);

		BoardFreeService.log.info("post was edited. post seq={}, subject=", boardFree.getSeq(), boardFree.getSubject());

		// 엘라스틱서치 색인 요청
		rabbitMQPublisher.indexDocumentBoard(boardFree.getId(), boardFree.getSeq(), boardFree.getWriter(), boardFree.getSubject(),
				boardFree.getContent(), boardFree.getBoard(), boardFree.getCategory(), galleryIds);

		return boardFree;
	}

	/**
	 * 자유게시판 글 지움
	 *
	 * @param seq 글 seq
	 * @return Constants.BOARD_DELETE_TYPE
     */
    public Constants.BOARD_DELETE_TYPE deleteFreePost(CommonWriter writer, Integer seq) {

        BoardFree boardFree = boardFreeRepository.findOneBySeq(seq)
				.orElseThrow(() -> new ServiceException(ServiceError.NOT_FOUND_POST));

        if (! boardFree.getWriter().getUserId().equals(writer.getUserId()))
            throw new ServiceException(ServiceError.FORBIDDEN);

        BoardItem boardItem = new BoardItem(boardFree.getId(), boardFree.getSeq(), boardFree.getBoard());

        Integer count = boardFreeCommentRepository.countByBoardItem(boardItem);

        // 댓글이 하나라도 달리면 글을 몽땅 지우지 못한다.
        if (count > 0) {
			boardFree.setContent(null);
			boardFree.setSubject(null);
			boardFree.setWriter(null);

            List<BoardLog> histories = boardFree.getLogs();

            if (Objects.isNull(histories))
                histories = new ArrayList<>();

			ObjectId boardHistoryId = new ObjectId();
            BoardLog history = new BoardLog(boardHistoryId.toString(), Constants.BOARD_FREE_HISTORY_TYPE.DELETE.name(), new SimpleWriter(writer));
            histories.add(history);
			boardFree.setLogs(histories);

            BoardStatus boardStatus = boardFree.getStatus();

            if (Objects.isNull(boardStatus))
                boardStatus = new BoardStatus();

            boardStatus.setDelete(true);
			boardFree.setStatus(boardStatus);
			boardFree.setLinkedGallery(false);

			// lastUpdated
			boardFree.setLastUpdated(LocalDateTime.ofInstant(boardHistoryId.getDate().toInstant(), ZoneId.systemDefault()));

			boardFreeRepository.save(boardFree);

			log.info("A post was deleted(post only). post seq={}, subject={}", boardFree.getSeq(), boardFree.getSubject());
        }
		// 몽땅 지우기
        else {
            boardFreeRepository.delete(boardFree);

			log.info("A post was deleted(all). post seq={}, subject={}", boardFree.getSeq(), boardFree.getSubject());
        }

        // 연결된 사진 끊기
        if (boardFree.getLinkedGallery())
			commonGalleryService.unlinkGalleries(boardFree.getId(), Constants.GALLERY_FROM_TYPE.BOARD_FREE);

		// 색인 지움
		rabbitMQPublisher.deleteDocumentBoard(boardFree.getId());

        return count > 0 ? Constants.BOARD_DELETE_TYPE.CONTENT : Constants.BOARD_DELETE_TYPE.ALL;
    }

	/**
	 * 자유게시판 글 목록
     */
	public FreePostsResponse getFreePosts(Constants.BOARD_TYPE board, String categoryCode, Integer page, Integer size) {

		Sort sort = new Sort(Sort.Direction.DESC, Collections.singletonList("_id"));
		Pageable pageable = new PageRequest(page - 1, size, sort);
		Page<BoardFreeOnList> postsPage;

		if ("ALL".equals(categoryCode)) {
			postsPage = boardFreeOnListRepository.findByBoard(board, pageable);
		} else {
			postsPage = boardFreeOnListRepository.findByBoardAndCategory(board, categoryCode, pageable);
		}

		// 자유 게시판 공지글 목록
		List<BoardFreeOnList> notices = boardFreeRepository.findNotices(board.name(), sort);

		// 게시물 VO 변환 및 썸네일 URL 추가
		Function<BoardFreeOnList, FreePost> convertToFreePost = post -> {
			FreePost freePosts = new FreePost();
			BeanUtils.copyProperties(post, freePosts);

			if (post.getLinkedGallery()) {
				List<Gallery> galleries = galleryRepository.findByItemIdAndFromType(
						new ObjectId(post.getId()), Constants.GALLERY_FROM_TYPE.BOARD_FREE, 1);

				if (! CollectionUtils.isEmpty(galleries)) {
					List<BoardGallerySimple> boardGalleries = galleries.stream()
							.map(gallery -> BoardGallerySimple.builder()
									.id(gallery.getId())
									.thumbnailUrl(urlGenerationUtils.generateGalleryUrl(Constants.IMAGE_SIZE_TYPE.SMALL, gallery.getId()))
									.build())
							.collect(Collectors.toList());

					freePosts.setGalleries(boardGalleries);
				}
			}

			return freePosts;
		};

		List<FreePost> freePosts = postsPage.getContent().stream()
				.map(convertToFreePost)
				.collect(Collectors.toList());

		List<FreePost> freeNotices = notices.stream()
				.map(convertToFreePost)
				.collect(Collectors.toList());

		// Board ID 뽑아내기.
		ArrayList<ObjectId> ids = new ArrayList<>();

		freePosts.forEach(post -> ids.add(new ObjectId(post.getId())));
		freeNotices.forEach(post -> ids.add(new ObjectId(post.getId())));

		// 게시물의 댓글수
		Map<String, Integer> commentCounts = boardFreeCommentRepository.findCommentsCountByIds(ids).stream()
				.collect(Collectors.toMap(CommonCount::getId, CommonCount::getCount));

		// 게시물의 감정수
		Map<String, BoardFeelingCount> feelingCounts = boardFreeRepository.findUsersFeelingCount(ids).stream()
				.collect(Collectors.toMap(BoardFeelingCount::getId, Function.identity()));

		// 댓글수, 감정 표현수 합치기.
		Consumer<FreePost> applyCounts = post -> {
			String boardId = post.getId();
			Integer commentCount = commentCounts.get(boardId);

			if (Objects.nonNull(commentCount))
				post.setCommentCount(commentCount);

			BoardFeelingCount feelingCount = feelingCounts.get(boardId);

			if (Objects.nonNull(feelingCount)) {
				post.setLikingCount(feelingCount.getUsersLikingCount());
				post.setDislikingCount(feelingCount.getUsersDislikingCount());
			}
		};

		freePosts.forEach(applyCounts);
		freeNotices.forEach(applyCounts);

		// 말머리
		List<BoardCategory> categories = new BoardCategoryGenerator().getCategories(Constants.BOARD_TYPE.FOOTBALL, JakdukUtils.getLocale());
		Map<String, String> categoriesMap = null;

		if (! CollectionUtils.isEmpty(categories)) {
			categoriesMap = categories.stream()
					.collect(Collectors.toMap(BoardCategory::getCode, boardCategory -> boardCategory.getNames().get(0).getName()));

			categoriesMap.put("ALL", JakdukUtils.getResourceBundleMessage("messages.board", "board.category.all"));
		}

		return FreePostsResponse.builder()
				.categories(categoriesMap)
				.posts(freePosts)
				.notices(freeNotices)
				.first(postsPage.isFirst())
				.last(postsPage.isLast())
				.totalPages(postsPage.getTotalPages())
				.totalElements(postsPage.getTotalElements())
				.numberOfElements(postsPage.getNumberOfElements())
				.size(postsPage.getSize())
				.number(postsPage.getNumber())
				.build();
	}

	/**
	 * 최근 글 가져오기
	 */
	public List<LatestPost> getFreeLatest() {

		Sort sort = new Sort(Sort.Direction.DESC, Collections.singletonList("_id"));

		List<BoardFreeOnList> posts = boardFreeRepository.findLatest(sort, Constants.HOME_SIZE_POST);

		// 게시물 VO 변환 및 썸네일 URL 추가
		List<LatestPost> latestPosts = posts.stream()
				.map(post -> {
					LatestPost latestPost = new LatestPost();
					BeanUtils.copyProperties(post, latestPost);

					if (post.getLinkedGallery()) {
						List<Gallery> galleries = galleryRepository.findByItemIdAndFromType(
								new ObjectId(post.getId()), Constants.GALLERY_FROM_TYPE.BOARD_FREE, 1);

						List<BoardGallerySimple> boardGalleries = galleries.stream()
								.sorted(Comparator.comparing(Gallery::getId))
								.limit(1)
								.map(gallery -> BoardGallerySimple.builder()
										.id(gallery.getId())
										.thumbnailUrl(urlGenerationUtils.generateGalleryUrl(Constants.IMAGE_SIZE_TYPE.SMALL, gallery.getId()))
										.build())
								.collect(Collectors.toList());

						latestPost.setGalleries(boardGalleries);
					}

					return latestPost;
				})
				.collect(Collectors.toList());

		return latestPosts;
	}

    /**
     * 글 감정 표현.
     */
	public BoardFree setFreeFeelings(CommonWriter writer, Integer seq, Constants.FEELING_TYPE feeling) {

		BoardFree boardFree = boardFreeRepository.findOneBySeq(seq)
                .orElseThrow(() -> new ServiceException(ServiceError.NOT_FOUND_POST));

        String userId = writer.getUserId();
        String username = writer.getUsername();

		CommonWriter postWriter = boardFree.getWriter();

		// 이 게시물의 작성자라서 감정 표현을 할 수 없음
		if (userId.equals(postWriter.getUserId()))
			throw new ServiceException(ServiceError.FEELING_YOU_ARE_WRITER);

		this.setUsersFeeling(userId, username, feeling, boardFree);

		boardFreeRepository.save(boardFree);

		return boardFree;
	}

	/**
	 * 게시판 댓글 달기
	 */
	public BoardFreeComment insertFreeComment(Integer seq, CommonWriter writer, String content, List<Gallery> galleries,
											  Constants.DEVICE_TYPE device) {

		BoardFree boardFree = boardFreeRepository.findOneBySeq(seq)
				.orElseThrow(() -> new ServiceException(ServiceError.NOT_FOUND_POST));

		// 연관된 사진 id 배열 (검증 후)
		List<String> galleryIds = galleries.stream()
				.map(Gallery::getId)
				.collect(Collectors.toList());

		BoardFreeComment boardFreeComment = BoardFreeComment.builder()
				.boardItem(new BoardItem(boardFree.getId(), boardFree.getSeq(), boardFree.getBoard()))
				.writer(writer)
				.content(content)
				.status(new BoardCommentStatus(device))
				.linkedGallery(! galleries.isEmpty())
				.logs(this.initBoardLogs(new ObjectId(), Constants.BOARD_FREE_COMMENT_HISTORY_TYPE.CREATE.name(), writer))
				.build();

		boardFreeCommentRepository.save(boardFreeComment);

		// 엘라스틱서치 색인 요청
		rabbitMQPublisher.indexDocumentComment(boardFreeComment.getId(), boardFreeComment.getBoardItem(), boardFreeComment.getWriter(),
				boardFreeComment.getContent(), galleryIds);

		return boardFreeComment;
	}

	/**
	 * 게시판 댓글 고치기
	 */
	public BoardFreeComment updateFreeComment(String id, Integer seq, CommonWriter writer, String content, List<String> galleryIds,
											  Constants.DEVICE_TYPE device) {

		boardFreeRepository.findOneBySeq(seq)
				.orElseThrow(() -> new ServiceException(ServiceError.NOT_FOUND_POST));

		BoardFreeComment boardFreeComment = boardFreeCommentRepository.findOneById(id)
				.orElseThrow(() -> new ServiceException(ServiceError.NOT_FOUND_COMMENT));

		if (! boardFreeComment.getWriter().getUserId().equals(writer.getUserId()))
			throw new ServiceException(ServiceError.FORBIDDEN);

		boardFreeComment.setWriter(writer);
		boardFreeComment.setContent(StringUtils.trim(content));
		BoardCommentStatus boardCommentStatus = boardFreeComment.getStatus();

		if (Objects.isNull(boardCommentStatus)) {
			boardCommentStatus = new BoardCommentStatus(device);
		} else {
			boardCommentStatus.setDevice(device);
		}

		boardFreeComment.setStatus(boardCommentStatus);
		boardFreeComment.setLinkedGallery(! galleryIds.isEmpty());

		// boardLogs
		List<BoardLog> logs = Optional.ofNullable(boardFreeComment.getLogs())
				.orElseGet(ArrayList::new);

		logs.add(new BoardLog(new ObjectId().toString(), Constants.BOARD_FREE_COMMENT_HISTORY_TYPE.EDIT.name(), new SimpleWriter(writer)));
		boardFreeComment.setLogs(logs);

		boardFreeCommentRepository.save(boardFreeComment);

		// 엘라스틱서치 색인 요청
		rabbitMQPublisher.indexDocumentComment(boardFreeComment.getId(), boardFreeComment.getBoardItem(), boardFreeComment.getWriter(),
				boardFreeComment.getContent(), galleryIds);

		return boardFreeComment;
	}

	/**
	 * 게시판 댓글 지움
	 */
	public void deleteFreeComment(String id, CommonWriter writer) {

		BoardFreeComment boardFreeComment = boardFreeCommentRepository.findOneById(id)
				.orElseThrow(() -> new ServiceException(ServiceError.NOT_FOUND_COMMENT));

		if (! boardFreeComment.getWriter().getUserId().equals(writer.getUserId()))
			throw new ServiceException(ServiceError.FORBIDDEN);

		boardFreeCommentRepository.delete(id);

		// 색인 지움
		rabbitMQPublisher.deleteDocumentComment(id);

		commonGalleryService.unlinkGalleries(id, Constants.GALLERY_FROM_TYPE.BOARD_FREE_COMMENT);
	}

	/**
	 * 게시물 댓글 목록
	 */
	public FreePostDetailCommentsResponse getBoardFreeDetailComments(Integer seq, String commentId) {

		List<BoardFreeComment> comments;

		if (StringUtils.isNotBlank(commentId)) {
			comments  = boardFreeCommentRepository.findByBoardSeqAndGTId(seq, new ObjectId(commentId));
		} else {
			comments  = boardFreeCommentRepository.findByBoardSeqAndGTId(seq, null);
		}

		CommonWriter commonWriter = AuthUtils.getCommonWriter();

		BoardFreeSimple boardFreeSimple = boardFreeRepository.findBoardFreeOfMinimumBySeq(seq);
		BoardItem boardItem = new BoardItem(boardFreeSimple.getId(), boardFreeSimple.getSeq(), boardFreeSimple.getBoard());

		Integer count = boardFreeCommentRepository.countByBoardItem(boardItem);

		List<FreePostDetailComment> postComments = comments.stream()
				.map(boardFreeComment -> {
					FreePostDetailComment freePostDetailComment = new FreePostDetailComment();
					BeanUtils.copyProperties(boardFreeComment, freePostDetailComment);

					List<CommonFeelingUser> usersLiking = boardFreeComment.getUsersLiking();
					List<CommonFeelingUser> usersDisliking = boardFreeComment.getUsersDisliking();

					freePostDetailComment.setNumberOfLike(CollectionUtils.isEmpty(usersLiking) ? 0 : usersLiking.size());
					freePostDetailComment.setNumberOfDislike(CollectionUtils.isEmpty(usersDisliking) ? 0 : usersDisliking.size());

					if (Objects.nonNull(commonWriter))
						freePostDetailComment.setMyFeeling(JakdukUtils.getMyFeeling(commonWriter, usersLiking, usersDisliking));

					if (! ObjectUtils.isEmpty(boardFreeComment.getLogs())) {
						List<BoardFreeCommentLog> logs = boardFreeComment.getLogs().stream()
								.map(boardLog -> {
									BoardFreeCommentLog boardFreeCommentLog = new BoardFreeCommentLog();
									BeanUtils.copyProperties(boardLog, boardFreeCommentLog);
									LocalDateTime timestamp = DateUtils.dateToLocalDateTime(new ObjectId(boardFreeCommentLog.getId()).getDate());
									boardFreeCommentLog.setType(Constants.BOARD_FREE_COMMENT_HISTORY_TYPE.valueOf(boardLog.getType()));
									boardFreeCommentLog.setTimestamp(timestamp);

									return boardFreeCommentLog;
								})
								.sorted(Comparator.comparing(BoardFreeCommentLog::getId).reversed())
								.collect(Collectors.toList());

						freePostDetailComment.setLogs(logs);
					}

					// 엮인 사진들
					if (boardFreeComment.getLinkedGallery()) {
						List<Gallery> galleries = galleryRepository.findByItemIdAndFromType(
								new ObjectId(boardFreeComment.getId()), Constants.GALLERY_FROM_TYPE.BOARD_FREE_COMMENT, 100);

						if (! ObjectUtils.isEmpty(galleries)) {
							List<BoardGallery> postDetailGalleries = galleries.stream()
									.map(gallery -> BoardGallery.builder()
											.id(gallery.getId())
											.name(StringUtils.isNotBlank(gallery.getName()) ? gallery.getName() : gallery.getFileName())
											.imageUrl(urlGenerationUtils.generateGalleryUrl(Constants.IMAGE_SIZE_TYPE.LARGE, gallery.getId()))
											.thumbnailUrl(urlGenerationUtils.generateGalleryUrl(Constants.IMAGE_SIZE_TYPE.LARGE, gallery.getId()))
											.build())
									.collect(Collectors.toList());

							freePostDetailComment.setGalleries(postDetailGalleries);
						}
					}

					return freePostDetailComment;
				})
				.collect(Collectors.toList());

		return FreePostDetailCommentsResponse.builder()
				.comments(postComments)
				.count(count)
				.build();
	}

	/**
	 * 자유게시판 댓글 감정 표현.
	 *
	 * @param commentId 댓글 ID
	 * @param feeling 감정표현 종류
     * @return 자유게시판 댓글 객체
     */
	public BoardFreeComment setFreeCommentFeeling(CommonWriter writer, String commentId, Constants.FEELING_TYPE feeling) {

		BoardFreeComment boardComment = boardFreeCommentRepository.findOneById(commentId)
				.orElseThrow(() -> new ServiceException(ServiceError.NOT_FOUND_COMMENT));

		String userId = writer.getUserId();
		String username = writer.getUsername();

		CommonWriter postWriter = boardComment.getWriter();

		// 이 게시물의 작성자라서 감정 표현을 할 수 없음
		if (userId.equals(postWriter.getUserId()))
			throw new ServiceException(ServiceError.FEELING_YOU_ARE_WRITER);

		this.setUsersFeeling(userId, username, feeling, boardComment);

		boardFreeCommentRepository.save(boardComment);

		return boardComment;
	}

	/**
	 * 자유게시판 글의 공지를 활성화/비활성화 한다.
	 * @param seq 글 seq
	 * @param isEnable 활성화/비활성화
     */
	public void setFreeNotice(CommonWriter writer, int seq, Boolean isEnable) {

		Optional<BoardFree> boardFree = boardFreeRepository.findOneBySeq(seq);

		if (!boardFree.isPresent())
			throw new ServiceException(ServiceError.NOT_FOUND_POST);

		BoardFree getBoardFree = boardFree.get();
		BoardStatus status = getBoardFree.getStatus();

		if (Objects.isNull(status))
			status = new BoardStatus();

		Boolean isNotice = status.getNotice();

		if (Objects.nonNull(isNotice)) {
			if (isEnable && isNotice)
				throw new ServiceException(ServiceError.ALREADY_ENABLE);

			if (! isEnable && ! isNotice)
				throw new ServiceException(ServiceError.ALREADY_DISABLE);
		}

		if (isEnable) {
			status.setNotice(true);
		} else {
			status.setNotice(null);
		}

		getBoardFree.setStatus(status);

		List<BoardLog> histories = getBoardFree.getLogs();

		if (CollectionUtils.isEmpty(histories))
			histories = new ArrayList<>();

		String historyType = isEnable ? Constants.BOARD_FREE_HISTORY_TYPE.ENABLE_NOTICE.name() : Constants.BOARD_FREE_HISTORY_TYPE.DISABLE_NOTICE.name();
		BoardLog history = new BoardLog(new ObjectId().toString(), historyType, new SimpleWriter(writer));
		histories.add(history);

		getBoardFree.setLogs(histories);

		boardFreeRepository.save(getBoardFree);

		if (log.isInfoEnabled())
			log.info("Set notice. post seq=" + getBoardFree.getSeq() + ", type=" + status.getNotice());
	}


	/**
	 * 자유게시판 주간 좋아요수 선두
     */
	public List<BoardPostTop> getFreeTopLikes(Constants.BOARD_TYPE board, ObjectId objectId) {
		return boardFreeRepository.findTopLikes(board.name(), objectId);
	}

	/**
	 * 자유게시판 주간 댓글수 선두
	 */
	public List<BoardPostTop> getFreeTopComments(Constants.BOARD_TYPE board, ObjectId objectId) {

		// 게시물의 댓글수
		Map<String, Integer> commentCounts = boardFreeCommentRepository.findCommentsCountGreaterThanBoardIdAndBoard(objectId, board.name()).stream()
				.collect(Collectors.toMap(CommonCount::getId, CommonCount::getCount));

		List<String> postIds = commentCounts.entrySet().stream()
				.map(Entry::getKey)
				.collect(Collectors.toList());

		// commentIds를 파라미터로 다시 글을 가져온다.
		List<BoardFree> posts = boardFreeRepository.findByIdInAndBoard(postIds, board.name());

		// sort
		Comparator<BoardPostTop> byCount = (b1, b2) -> b2.getCount() - b1.getCount();
		Comparator<BoardPostTop> byView = (b1, b2) -> b2.getViews() - b1.getViews();

		return posts.stream()
				.map(boardFree -> {
					BoardPostTop boardPostTop = new BoardPostTop();
					BeanUtils.copyProperties(boardFree, boardPostTop);
					boardPostTop.setCount(commentCounts.get(boardPostTop.getId()));
					return boardPostTop;
				})
				.sorted(byCount.thenComparing(byView))
				.limit(Constants.BOARD_TOP_LIMIT)
				.collect(Collectors.toList());
	}

	/**
	 * 자유게시판 댓글 목록
     */
	public FreePostCommentsResponse getBoardFreeComments(CommonWriter commonWriter, Constants.BOARD_TYPE board, Integer page, Integer size) {

		Sort sort = new Sort(Sort.Direction.DESC, Collections.singletonList("_id"));
		Pageable pageable = new PageRequest(page - 1, size, sort);

		Page<BoardFreeComment> commentsPage = boardFreeCommentRepository.findByBoardItemBoard(board.name(), pageable);

		// board id 뽑아내기.
		List<ObjectId> boardIds = commentsPage.getContent().stream()
				.map(comment -> new ObjectId(comment.getBoardItem().getId()))
				.distinct()
				.collect(Collectors.toList());

		// 댓글을 가진 글 목록
		List<BoardFreeOnSearch> posts = boardFreeRepository.findPostsOnSearchByIds(boardIds);

		Map<String, BoardFreeOnSearch> postsHavingComments = posts.stream()
				.collect(Collectors.toMap(BoardFreeOnSearch::getId, Function.identity()));

		List<FreePostComment> freePostComments = commentsPage.getContent().stream()
				.map(boardFreeComment -> {
							FreePostComment comment = new FreePostComment();
							BeanUtils.copyProperties(boardFreeComment, comment);

							comment.setBoardItem(
									Optional.ofNullable(postsHavingComments.get(boardFreeComment.getBoardItem().getId()))
											.orElse(new BoardFreeOnSearch())
							);

							comment.setNumberOfLike(CollectionUtils.isEmpty(boardFreeComment.getUsersLiking()) ? 0 : boardFreeComment.getUsersLiking().size());
							comment.setNumberOfDislike(CollectionUtils.isEmpty(boardFreeComment.getUsersDisliking()) ? 0 : boardFreeComment.getUsersDisliking().size());

							if (Objects.nonNull(commonWriter))
								comment.setMyFeeling(JakdukUtils.getMyFeeling(commonWriter, boardFreeComment.getUsersLiking(),
										boardFreeComment.getUsersDisliking()));

							if (boardFreeComment.getLinkedGallery()) {
								List<Gallery> galleries = galleryRepository.findByItemIdAndFromType(
										new ObjectId(boardFreeComment.getId()), Constants.GALLERY_FROM_TYPE.BOARD_FREE_COMMENT, 100);

								if (! CollectionUtils.isEmpty(galleries)) {
									List<BoardGallerySimple> boardGalleries = galleries.stream()
											.map(gallery -> BoardGallerySimple.builder()
													.id(gallery.getId())
													.thumbnailUrl(urlGenerationUtils.generateGalleryUrl(Constants.IMAGE_SIZE_TYPE.SMALL, gallery.getId()))
													.build())
											.collect(Collectors.toList());

									comment.setGalleries(boardGalleries);
								}
							}

							return comment;
						}
				)
				.collect(Collectors.toList());

		return FreePostCommentsResponse.builder()
				.comments(freePostComments)
				.first(commentsPage.isFirst())
				.last(commentsPage.isLast())
				.totalPages(commentsPage.getTotalPages())
				.totalElements(commentsPage.getTotalElements())
				.numberOfElements(commentsPage.getNumberOfElements())
				.size(commentsPage.getSize())
				.number(commentsPage.getNumber())
				.build();
	}

	/**
	 * RSS 용 게시물 목록
	 */
	public List<BoardFreeOnRSS> getBoardFreeOnRss(ObjectId objectId, Integer limit) {
		Sort sort = new Sort(Sort.Direction.DESC, Collections.singletonList("_id"));

		return boardFreeRepository.findPostsOnRss(objectId, sort, limit);

	}

	/**
	 * 사이트맵 용 게시물 목록
	 */
	public List<BoardFreeOnSitemap> getBoardFreeOnSitemap(ObjectId objectId, Integer limit) {
		Sort sort = new Sort(Sort.Direction.DESC, Collections.singletonList("_id"));

		return boardFreeRepository.findPostsOnSitemap(objectId, sort, limit);

	}

	/**
	 * 글 상세 객체 가져오기
	 */
	public FreePostDetailResponse getBoardFreeDetail(Integer seq, Boolean isAddCookie) {

		BoardFree boardFree = boardFreeRepository.findOneBySeq(seq)
				.orElseThrow(() -> new ServiceException(ServiceError.NOT_FOUND_POST));

		if (isAddCookie)
			this.increaseViews(boardFree);

		CommonWriter commonWriter = AuthUtils.getCommonWriter();

        // 글 상세
		FreePostDetail freePostDetail = new FreePostDetail();
		BeanUtils.copyProperties(boardFree, freePostDetail);

		if (! CollectionUtils.isEmpty(boardFree.getLogs())) {
			List<BoardFreeLog> logs = boardFree.getLogs().stream()
					.map(boardLog -> {
						BoardFreeLog boardFreeLog = new BoardFreeLog();
						BeanUtils.copyProperties(boardLog, boardFreeLog);
						LocalDateTime timestamp = DateUtils.dateToLocalDateTime(new ObjectId(boardFreeLog.getId()).getDate());
						boardFreeLog.setType(Constants.BOARD_FREE_HISTORY_TYPE.valueOf(boardLog.getType()));
						boardFreeLog.setTimestamp(timestamp);

						return boardFreeLog;
					})
					.sorted(Comparator.comparing(BoardFreeLog::getId).reversed())
					.collect(Collectors.toList());

			freePostDetail.setLogs(logs);
		}

		BoardCategory boardCategory = new BoardCategoryGenerator().getCategory(Constants.BOARD_TYPE.FOOTBALL, boardFree.getCategory(), JakdukUtils.getLocale());

		freePostDetail.setCategory(boardCategory);
		freePostDetail.setNumberOfLike(CollectionUtils.isEmpty(boardFree.getUsersLiking()) ? 0 : boardFree.getUsersLiking().size());
		freePostDetail.setNumberOfDislike(CollectionUtils.isEmpty(boardFree.getUsersDisliking()) ? 0 : boardFree.getUsersDisliking().size());

		// 엮인 사진들
		if (boardFree.getLinkedGallery()) {
            List<Gallery> galleries = galleryRepository.findByItemIdAndFromType(
                    new ObjectId(boardFree.getId()), Constants.GALLERY_FROM_TYPE.BOARD_FREE, 100);

            if (! CollectionUtils.isEmpty(galleries)) {
                List<BoardGallery> postDetailGalleries = galleries.stream()
                        .map(gallery -> BoardGallery.builder()
                                .id(gallery.getId())
                                .name(StringUtils.isNoneBlank(gallery.getName()) ? gallery.getName() : gallery.getFileName())
                                .imageUrl(urlGenerationUtils.generateGalleryUrl(Constants.IMAGE_SIZE_TYPE.LARGE, gallery.getId()))
                                .thumbnailUrl(urlGenerationUtils.generateGalleryUrl(Constants.IMAGE_SIZE_TYPE.LARGE, gallery.getId()))
                                .build())
                        .collect(Collectors.toList());

                freePostDetail.setGalleries(postDetailGalleries);
            }
        }

        // 나의 감정 상태
		if (Objects.nonNull(commonWriter))
			freePostDetail.setMyFeeling(JakdukUtils.getMyFeeling(commonWriter, boardFree.getUsersLiking(), boardFree.getUsersDisliking()));

		// 앞, 뒤 글
		BoardFreeSimple prevPost = boardFreeRepository.findByIdAndCategoryWithOperator(new ObjectId(freePostDetail.getId()),
				boardCategory.getCode(), Constants.CRITERIA_OPERATOR.GT);
		BoardFreeSimple nextPost = boardFreeRepository.findByIdAndCategoryWithOperator(new ObjectId(freePostDetail.getId()),
				boardCategory.getCode(), Constants.CRITERIA_OPERATOR.LT);

        // 글쓴이의 최근 글
		List<LatestFreePost> latestFreePosts = null;

		if (ObjectUtils.isEmpty(freePostDetail.getStatus()) || BooleanUtils.isNotTrue(freePostDetail.getStatus().getDelete())) {

			List<BoardFreeOnList> latestPostsByWriter = boardFreeRepository.findByIdAndUserId(
					new ObjectId(freePostDetail.getId()), freePostDetail.getWriter().getUserId(), 3);

			// 게시물 VO 변환 및 썸네일 URL 추가
			latestFreePosts = latestPostsByWriter.stream()
					.map(post -> {
						LatestFreePost latestFreePost = new LatestFreePost();
						BeanUtils.copyProperties(post, latestFreePost);

						if (! post.getLinkedGallery()) {
							List<Gallery> latestPostGalleries = galleryRepository.findByItemIdAndFromType(new ObjectId(post.getId()),
									Constants.GALLERY_FROM_TYPE.BOARD_FREE, 1);

							if (! ObjectUtils.isEmpty(latestPostGalleries)) {
								List<BoardGallerySimple> boardGalleries = latestPostGalleries.stream()
										.map(gallery -> BoardGallerySimple.builder()
												.id(gallery.getId())
												.thumbnailUrl(urlGenerationUtils.generateGalleryUrl(Constants.IMAGE_SIZE_TYPE.SMALL, gallery.getId()))
												.build())
										.collect(Collectors.toList());

								latestFreePost.setGalleries(boardGalleries);
							}
						}

						return latestFreePost;
					})
					.collect(Collectors.toList());
		}

		return FreePostDetailResponse.builder()
				.post(freePostDetail)
				.prevPost(prevPost)
				.nextPost(nextPost)
				.latestPostsByWriter(ObjectUtils.isEmpty(latestFreePosts) ? null : latestFreePosts)
				.build();
	}

	/**
	 * 읽음수 1 증가
	 */
	private void increaseViews(BoardFree boardFree) {
		int views = boardFree.getViews();
		boardFree.setViews(++views);
		boardFreeRepository.save(boardFree);
	}

	/**
	 * BoardLogs 생성
	 */
	private List<BoardLog> initBoardLogs(ObjectId objectId, String type, CommonWriter writer) {
		List<BoardLog> logs = new ArrayList<>();
		BoardLog history = new BoardLog(objectId.toString(), type, new SimpleWriter(writer));
		logs.add(history);

		return logs;
	}

	/**
	 * 감정 표현 CRUD
	 *
	 * @param userId 회원 ID
	 * @param username 회원 별명
	 * @param feeling 입력받은 감정
	 * @param usersFeeling 편집할 객체
	 */
	private void setUsersFeeling(String userId, String username, Constants.FEELING_TYPE feeling, UsersFeeling usersFeeling) {

		List<CommonFeelingUser> usersLiking = usersFeeling.getUsersLiking();
		List<CommonFeelingUser> usersDisliking = usersFeeling.getUsersDisliking();

		if (CollectionUtils.isEmpty(usersLiking)) usersLiking = new ArrayList<>();
		if (CollectionUtils.isEmpty(usersDisliking)) usersDisliking = new ArrayList<>();

		// 해당 회원이 좋아요를 이미 했는지 검사
		Optional<CommonFeelingUser> alreadyLike = usersLiking.stream()
				.filter(commonFeelingUser -> commonFeelingUser.getUserId().equals(userId))
				.findFirst();

		// 해당 회원이 싫어요를 이미 했는지 검사
		Optional<CommonFeelingUser> alreadyDislike = usersDisliking.stream()
				.filter(commonFeelingUser -> commonFeelingUser.getUserId().equals(userId))
				.findFirst();

		CommonFeelingUser feelingUser = new CommonFeelingUser(new ObjectId().toString(), userId, username);

		switch (feeling) {
			case LIKE:
				// 이미 좋아요를 했을 때, 좋아요를 취소
				if (alreadyLike.isPresent()) {
					usersLiking.remove(alreadyLike.get());
				}
				// 이미 싫어요를 했을 때, 싫어요를 없애고 좋아요로 바꿈
				else if (alreadyDislike.isPresent()) {
					usersDisliking.remove(alreadyDislike.get());
					usersLiking.add(feelingUser);

					usersFeeling.setUsersDisliking(usersDisliking);
				}
				// 아직 감정 표현을 하지 않아 좋아요로 등록
				else {
					usersLiking.add(feelingUser);
				}

				usersFeeling.setUsersLiking(usersLiking);

				break;

			case DISLIKE:
				// 이미 싫어요를 했을 때, 싫어요를 취소
				if (alreadyDislike.isPresent()) {
					usersDisliking.remove(alreadyDislike.get());
				}
				// 이미 좋아요를 했을 때, 좋아요를 없애고 싫어요로 바꿈
				else if (alreadyLike.isPresent()) {
					usersLiking.remove(alreadyLike.get());
					usersDisliking.add(feelingUser);

					usersFeeling.setUsersLiking(usersLiking);
				}
				// 아직 감정 표현을 하지 않아 싫어요로 등록
				else {
					usersDisliking.add(feelingUser);
				}

				usersFeeling.setUsersDisliking(usersDisliking);

				break;
		}
	}

}
