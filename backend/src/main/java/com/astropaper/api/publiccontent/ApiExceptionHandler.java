package com.astropaper.api.publiccontent;

import com.astropaper.api.auth.InvalidCredentialsException;
import com.astropaper.api.auth.AccountNotActiveException;
import com.astropaper.api.auth.AdminResourceNotFoundException;
import com.astropaper.api.auth.ForbiddenRoleChangeException;
import com.astropaper.api.auth.InvalidAccessConfigurationException;
import com.astropaper.api.auth.LastAdministratorException;
import com.astropaper.api.auth.UnknownAccessCodeException;
import com.astropaper.api.auth.UserAlreadyExistsException;
import com.astropaper.api.auth.TooManyLoginAttemptsException;
import com.astropaper.api.articles.ArticleConflictException;
import com.astropaper.api.articles.ArticleOwnershipException;
import com.astropaper.api.articles.InvalidArticleRequestException;
import com.astropaper.api.interactions.InvalidCommentParentException;
import com.astropaper.api.interactions.InvalidInteractionStatusException;
import com.astropaper.api.uploads.ImageUploadException;
import com.astropaper.api.uploads.ImageNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PostNotFoundException.class)
    public ProblemDetail handlePostNotFound(PostNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "The requested post does not exist.");
        problem.setTitle("Post not found");
        return problem;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        problem.setTitle("Invalid credentials");
        return problem;
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ProblemDetail handleTooManyLoginAttempts(TooManyLoginAttemptsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        problem.setTitle("Too many sign-in attempts");
        return problem;
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ProblemDetail handleInactiveAccount() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "This account is not available.");
        problem.setTitle("Authentication required");
        return problem;
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ProblemDetail handleExistingUser(UserAlreadyExistsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Account already exists");
        return problem;
    }

    @ExceptionHandler(AdminResourceNotFoundException.class)
    public ProblemDetail handleAdminResourceNotFound(AdminResourceNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Account or access resource not found");
        return problem;
    }

    @ExceptionHandler(UnknownAccessCodeException.class)
    public ProblemDetail handleUnknownAccessCode(UnknownAccessCodeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid access configuration");
        return problem;
    }

    @ExceptionHandler(InvalidAccessConfigurationException.class)
    public ProblemDetail handleInvalidAccessConfiguration(InvalidAccessConfigurationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid access configuration");
        return problem;
    }

    @ExceptionHandler(ForbiddenRoleChangeException.class)
    public ProblemDetail handleForbiddenRoleChange() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Only a permission administrator can assign the ADMIN role.");
        problem.setTitle("Forbidden");
        return problem;
    }

    @ExceptionHandler(LastAdministratorException.class)
    public ProblemDetail handleLastAdministrator() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "At least one active administrator must remain.");
        problem.setTitle("Last administrator cannot be removed");
        return problem;
    }

    @ExceptionHandler(InvalidInteractionStatusException.class)
    public ProblemDetail handleInvalidInteractionStatus(InvalidInteractionStatusException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid interaction status");
        return problem;
    }

    @ExceptionHandler(InvalidCommentParentException.class)
    public ProblemDetail handleInvalidCommentParent(InvalidCommentParentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid reply target");
        return problem;
    }

    @ExceptionHandler(ArticleConflictException.class)
    public ProblemDetail handleArticleConflict(ArticleConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Article conflict");
        return problem;
    }

    @ExceptionHandler(ArticleOwnershipException.class)
    public ProblemDetail handleArticleOwnership(ArticleOwnershipException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setTitle("Forbidden");
        return problem;
    }

    @ExceptionHandler(InvalidArticleRequestException.class)
    public ProblemDetail handleInvalidArticleRequest(InvalidArticleRequestException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid article request");
        return problem;
    }

    @ExceptionHandler(ImageUploadException.class)
    public ProblemDetail handleImageUpload(ImageUploadException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid image upload");
        return problem;
    }

    @ExceptionHandler(ImageNotFoundException.class)
    public ProblemDetail handleImageNotFound(ImageNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Image not found");
        return problem;
    }
}
