package Katedra.Server.exception;

public class SocialAccountConflictException extends RuntimeException {

    private final String errorCode;

    public SocialAccountConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
