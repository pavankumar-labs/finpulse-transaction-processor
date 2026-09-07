package com.finpulse.exception;

public class CredentialExpiredException extends RuntimeException{

    public CredentialExpiredException(String message){
        super(message);
    }
}