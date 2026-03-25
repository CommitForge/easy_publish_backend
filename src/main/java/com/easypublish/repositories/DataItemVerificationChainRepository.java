package com.easypublish.repositories;

import com.easypublish.entities.onchain.DataItemVerificationChain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataItemVerificationChainRepository extends JpaRepository<DataItemVerificationChain, String> {

}