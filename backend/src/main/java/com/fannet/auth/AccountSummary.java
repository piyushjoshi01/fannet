package com.fannet.auth;

import com.fannet.accounts.Account;

public record AccountSummary(String id, String handle, String displayName) {

    static AccountSummary of(Account a) {
        return new AccountSummary(a.getId(), a.getHandle(), a.getProfile().displayName());
    }
}
