package com.easypublish.repositories;

import com.easypublish.entities.onchain.UpdateChain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UpdateChainRepository extends JpaRepository<UpdateChain, String> {}
