package com.fannet.accounts;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AccountRepository extends MongoRepository<Account, String> {

    Optional<Account> findByHandle(String handle);

    Optional<Account> findByHumanAuthEmail(String email);
}
