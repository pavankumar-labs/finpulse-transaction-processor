package com.finpulse.exception;

public class InvalidCompanyStateException extends RuntimeException{
    public InvalidCompanyStateException(String message){
        super(message);
    }
}
