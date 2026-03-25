package com.easypublish.repositories;

import com.easypublish.entities.onchain.DataItemChain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataItemChainRepository extends JpaRepository<DataItemChain, String> {

}
