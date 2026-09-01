package com.finpulse.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class ApiKeyGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private  static final String KEY_PREFIX= "fp_live_";

    public static String generateRawKey(){
        byte[] randomBytes= new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String encoder= Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return KEY_PREFIX+encoder;
    }

    public static String hash(String rawKey){
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] hashBytes= digest.digest(rawKey.getBytes());
            StringBuilder hex=new StringBuilder();
            for(byte b : hashBytes){
                hex.append(String.format("%02x",b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }


}
