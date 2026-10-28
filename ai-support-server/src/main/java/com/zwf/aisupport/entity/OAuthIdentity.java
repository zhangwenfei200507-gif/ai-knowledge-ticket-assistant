package com.zwf.aisupport.entity;

import lombok.Data;

@Data
public class OAuthIdentity {
    private Long id;
    private Long userId;
    private String provider;
    private String providerSubject;
}
