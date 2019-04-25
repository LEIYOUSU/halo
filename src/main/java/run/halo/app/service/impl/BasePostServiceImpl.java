package run.halo.app.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import run.halo.app.exception.AlreadyExistsException;
import run.halo.app.exception.BadRequestException;
import run.halo.app.exception.NotFoundException;
import run.halo.app.model.entity.BasePost;
import run.halo.app.model.enums.PostStatus;
import run.halo.app.repository.base.BasePostRepository;
import run.halo.app.service.base.AbstractCrudService;
import run.halo.app.service.base.BasePostService;
import run.halo.app.utils.DateUtils;
import run.halo.app.utils.MarkdownUtils;
import run.halo.app.utils.ServiceUtils;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.Direction.DESC;

/**
 * Base post service implementation.
 *
 * @author johnniang
 * @date 19-4-24
 */
@Slf4j
public abstract class BasePostServiceImpl<POST extends BasePost> extends AbstractCrudService<POST, Integer> implements BasePostService<POST> {

    private final BasePostRepository<POST> basePostRepository;

    public BasePostServiceImpl(BasePostRepository<POST> basePostRepository) {
        super(basePostRepository);
        this.basePostRepository = basePostRepository;
    }

    @Override
    public long countVisit() {
        return Optional.ofNullable(basePostRepository.countVisit()).orElse(0L);
    }

    @Override
    public long countLike() {
        return Optional.ofNullable(basePostRepository.countLike()).orElse(0L);
    }

    @Override
    public long countByStatus(PostStatus status) {
        Assert.notNull(status, "Post status must not be null");

        return basePostRepository.countByStatus(status);
    }

    @Override
    public POST getByUrl(String url) {
        Assert.hasText(url, "Url must not be blank");

        return basePostRepository.getByUrl(url).orElseThrow(() -> new NotFoundException("The post does not exist").setErrorData(url));
    }

    @Override
    public POST getBy(PostStatus status, String url) {
        Assert.notNull(status, "Post status must not be null");
        Assert.hasText(url, "Post url must not be blank");

        Optional<POST> postOptional = basePostRepository.getByUrlAndStatus(url, status);

        return postOptional.orElseThrow(() -> new NotFoundException("The post with status " + status + " and url " + url + "was not existed").setErrorData(url));
    }

    @Override
    public List<POST> listAllBy(PostStatus status) {
        Assert.notNull(status, "Post status must not be null");

        return basePostRepository.findAllByStatus(status);
    }


    @Override
    public List<POST> listPrePosts(Date date, int size) {
        Assert.notNull(date, "Date must not be null");

        return basePostRepository.findAllByStatusAndCreateTimeAfter(PostStatus.PUBLISHED,
                date,
                PageRequest.of(0, size, Sort.by(ASC, "createTime")))
                .getContent();
    }

    @Override
    public List<POST> listNextPosts(Date date, int size) {
        Assert.notNull(date, "Date must not be null");

        return basePostRepository.findAllByStatusAndCreateTimeBefore(PostStatus.PUBLISHED,
                date,
                PageRequest.of(0, size, Sort.by(DESC, "createTime")))
                .getContent();
    }

    @Override
    public Optional<POST> getPrePost(Date date) {
        List<POST> posts = listPrePosts(date, 1);

        return CollectionUtils.isEmpty(posts) ? Optional.empty() : Optional.of(posts.get(0));
    }

    @Override
    public Optional<POST> getNextPost(Date date) {
        List<POST> posts = listNextPosts(date, 1);

        return CollectionUtils.isEmpty(posts) ? Optional.empty() : Optional.of(posts.get(0));
    }

    @Override
    public Page<POST> pageLatest(int top) {
        Assert.isTrue(top > 0, "Top number must not be less than 0");

        PageRequest latestPageable = PageRequest.of(0, top, Sort.by(DESC, "editTime"));

        return listAll(latestPageable);
    }

    @Override
    public Page<POST> pageBy(Pageable pageable) {
        Assert.notNull(pageable, "Page info must not be null");

        return listAll(pageable);
    }


    @Override
    public Page<POST> pageBy(PostStatus status, Pageable pageable) {
        Assert.notNull(status, "Post status must not be null");
        Assert.notNull(pageable, "Page info must not be null");

        return basePostRepository.findAllByStatus(status, pageable);
    }

    @Override
    public void increaseVisit(long visits, Integer postId) {
        Assert.isTrue(visits > 0, "Visits to increase must not be less than 1");
        Assert.notNull(postId, "Goods id must not be null");

        long affectedRows = basePostRepository.updateVisit(visits, postId);

        if (affectedRows != 1) {
            log.error("Post with id: [{}] may not be found", postId);
            throw new BadRequestException("Failed to increase visits " + visits + " for post with id " + postId);
        }
    }

    @Override
    public void increaseLike(long likes, Integer postId) {
        Assert.isTrue(likes > 0, "Likes to increase must not be less than 1");
        Assert.notNull(postId, "Goods id must not be null");

        long affectedRows = basePostRepository.updateLikes(likes, postId);

        if (affectedRows != 1) {
            log.error("Post with id: [{}] may not be found", postId);
            throw new BadRequestException("Failed to increase likes " + likes + " for post with id " + postId);
        }
    }

    @Override
    public void increaseVisit(Integer postId) {
        increaseVisit(1L, postId);
    }

    @Override
    public void increaseLike(Integer postId) {
        increaseLike(1L, postId);
    }

    @Override
    public POST createOrUpdateBy(POST post) {
        Assert.notNull(post, "Post must not be null");

        // Check url
        urlMustNotExist(post);

        // Render content
        post.setFormatContent(MarkdownUtils.renderMarkdown(post.getOriginalContent()));

        // Create or update post
        if (ServiceUtils.isEmptyId(post.getId())) {
            // The sheet will be created
            return create(post);
        }

        // The sheet will be updated
        // Set edit time
        post.setEditTime(DateUtils.now());

        // Update it
        return update(post);
    }

    @Override
    public POST filterIfEncrypt(POST post) {
        Assert.notNull(post, "Post must not be null");

        if (StringUtils.isNotBlank(post.getPassword())) {
            String tip = "The post is encrypted by author";
            post.setSummary(tip);
            post.setOriginalContent(tip);
            post.setFormatContent(tip);
        }

        return post;
    }

    /**
     * Check if the url is exist.
     *
     * @param post post must not be null
     */
    protected void urlMustNotExist(@NonNull POST post) {
        Assert.notNull(post, "Sheet must not be null");
        // TODO Refactor this method with BasePostService

        // TODO May refactor these queries
        // Get url count
        long count;
        if (ServiceUtils.isEmptyId(post.getId())) {
            // The sheet will be created
            count = basePostRepository.countByUrl(post.getUrl());
        } else {
            // The sheet will be updated
            count = basePostRepository.countByIdNotAndUrl(post.getId(), post.getUrl());
        }

        if (count > 0) {
            throw new AlreadyExistsException("The sheet url has been exist");
        }
    }
}