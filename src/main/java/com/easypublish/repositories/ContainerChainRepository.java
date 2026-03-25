package com.easypublish.repositories;

import com.easypublish.entities.onchain.ContainerChain;
import com.easypublish.entities.onchain.ContainerChildLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContainerChainRepository extends JpaRepository<ContainerChain, String> {
    //  List<ContainerChildLink> findById_containerParent(String containerId);

}
