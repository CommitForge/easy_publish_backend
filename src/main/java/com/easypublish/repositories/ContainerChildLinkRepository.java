package com.easypublish.repositories;



import com.easypublish.entities.onchain.ContainerChildLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContainerChildLinkRepository extends JpaRepository<ContainerChildLink, String> {
  //  List<ContainerChildLink> findById_containerParent(String containerId);

}
