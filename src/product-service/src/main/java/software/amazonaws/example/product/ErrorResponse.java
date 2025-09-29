package software.amazonaws.example.product;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class ErrorResponse {
    @JsonProperty("error")
    private String error;

    @JsonProperty("message")
    private String message;

    public ErrorResponse() {
        // Default constructor for Jackson
    }

    public ErrorResponse(String message, int statusCode) {
        this.message = message;
        this.error = "HTTP " + statusCode;
    }
    
    public ErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
    }
    
    public int getStatusCode() {
        if (error != null && error.startsWith("HTTP ")) {
            try {
                return Integer.parseInt(error.substring(5));
            } catch (NumberFormatException e) {
                return 500;
            }
        }
        return 500;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErrorResponse that = (ErrorResponse) o;
        return Objects.equals(error, that.error) &&
               Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(error, message);
    }

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "error='" + error + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}